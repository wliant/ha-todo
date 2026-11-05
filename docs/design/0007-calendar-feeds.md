# 0007 - Calendar feeds

Design for [requirements/0007](../requirements/0007-calendar-feeds-on-my-day.md)
(R62-R72).

Decisions this design implements rather than argues:
[adr/0034](../adr/0034-calendar-feeds-are-read-only-ics-subscriptions.md) (read-only
`.ics` subscriptions, no OAuth),
[adr/0035](../adr/0035-a-feed-address-is-a-write-only-secret-fetched-through-a-guard.md)
(the address is write-only; every fetch passes an egress guard),
[adr/0036](../adr/0036-the-household-has-one-timezone.md) (one household
timezone, served to the client).

## Scope

Four phases, each shippable and each verifiable in a browser.

- **A - the household's zone.** `app.timezone`, defaulting to
  `Asia/Singapore`; `/api/me` carries it; `calendar.ts` buckets by it; the
  recurrence job reads it from its new home. No calendar feature yet, and My day
  is already visibly correct for a household that is not in UTC. Phase A is
  separable and should be merged on its own, because it changes an existing
  screen's behaviour and nothing about it depends on the rest.
- **B - a feed, fetched.** `calendar_feed`, `calendar_feed_access`,
  `calendar_event`; the URL guard, the fetcher, the parser; the preview and the
  write endpoints; the profile screen. Events are stored and nothing reads them.
  End of phase B a member can subscribe a calendar, see the preview, share it,
  and watch the row say when it last synced.
- **C - events on My day.** `/api/dashboard/scheduled` gains events and feed
  warnings; the day, week and month views render them merged with items; the
  popup opens an event. This is the phase that delivers the value.
- **D - the scheduled refresh.** `@Scheduled` poll behind
  `app.calendar.enabled`, the staleness notice, and refresh-now. Ordered last
  deliberately: phases B and C are driven by the explicit refresh endpoint, so
  the job is added once there is something to watch it do.

## Data

One migration, **V916**. Three tables; nothing is added to `todo_list`,
`todo_item` or `app_user`.

### V916 - feeds, their audience, and their events

```sql
-- Subscribed iCalendar feeds (requirements/0007 R62) and the events read out of
-- them. A feed is somebody else's calendar, published at an unguessable URL;
-- see docs/adr/0034 for why there is no OAuth here and docs/adr/0035 for why
-- the URL never leaves this table.
CREATE TABLE calendar_feed (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label          VARCHAR(80)  NOT NULL,
    -- A bearer capability for the whole calendar behind it. Written and
    -- replaced, never selected into a response record (adr/0035). Not
    -- encrypted, and requirements/0007 records why.
    url            TEXT         NOT NULL,
    -- sha256 of the URL, so the same feed cannot be added twice by one member
    -- without comparing secrets in an index we would then have to read.
    url_digest     VARCHAR(64)  NOT NULL,
    -- Inferred from the host for display only ('GOOGLE' | 'OUTLOOK' | 'OTHER').
    -- Nothing branches on it: one parser reads all of them.
    provider       VARCHAR(10)  NOT NULL,

    -- Sync state, which R71 makes part of the product rather than diagnostics.
    last_attempt_at    TIMESTAMPTZ,
    last_success_at    TIMESTAMPTZ,
    last_error         VARCHAR(300),
    consecutive_errors SMALLINT  NOT NULL DEFAULT 0,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_calendar_feed_label_not_blank CHECK (btrim(label) <> '')
);

CREATE UNIQUE INDEX ux_calendar_feed_owner_label ON calendar_feed (owner_id, lower(label));
CREATE UNIQUE INDEX ux_calendar_feed_owner_url ON calendar_feed (owner_id, url_digest);

-- Who may read this feed's events (R64). Binary, as every grant in this product
-- is, and the owner holds a row of their own - written in the same transaction,
-- exactly as on data_list - so every later question is one table.
CREATE TABLE calendar_feed_access (
    feed_id    UUID        NOT NULL REFERENCES calendar_feed (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (feed_id, user_id)
);

CREATE INDEX ix_calendar_feed_access_user ON calendar_feed_access (user_id);

-- One occurrence of one event. A recurring series is stored expanded, one row
-- per occurrence inside the cached window: the views ask "what is on this day",
-- and a reader that had to expand an RRULE to answer that would be expanding it
-- in three places.
CREATE TABLE calendar_event (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id       UUID         NOT NULL REFERENCES calendar_feed (id) ON DELETE CASCADE,
    -- RFC 5545 UID, kept because it is how the same appointment arriving
    -- through two members' feeds is recognised as one (R69).
    uid           TEXT         NOT NULL,
    summary       VARCHAR(300) NOT NULL,
    description   TEXT,
    location      VARCHAR(300),

    -- An all-day event is a date and a timed event is an instant, and the
    -- difference is structural rather than a flag to remember: see adr/0036.
    -- The check makes the wrong combination unrepresentable.
    all_day       BOOLEAN      NOT NULL,
    starts_at     TIMESTAMPTZ,
    ends_at       TIMESTAMPTZ,
    start_date    DATE,
    end_date      DATE,        -- exclusive, as DTEND is

    -- The household-zone day this occurrence is placed on, maintained by the
    -- parser. NOT a generated column: deriving it would need
    -- `starts_at AT TIME ZONE 'Asia/Singapore'`, and `AT TIME ZONE` is STABLE
    -- rather than IMMUTABLE - Postgres refuses it in a generation expression,
    -- and a zone baked into the schema could not be reconfigured anyway.
    occurs_on     DATE         NOT NULL,
    -- One orderable key for both kinds, so a merged day reads in order: an
    -- all-day event sorts to the top of its day.
    sort_minute   SMALLINT     NOT NULL,

    CONSTRAINT ck_calendar_event_shape CHECK (
        (all_day AND start_date IS NOT NULL AND starts_at IS NULL)
        OR (NOT all_day AND starts_at IS NOT NULL AND start_date IS NULL)
    )
);

CREATE INDEX ix_calendar_event_feed ON calendar_event (feed_id);
CREATE INDEX ix_calendar_event_day ON calendar_event (occurs_on, sort_minute);
```

No `version` column anywhere: the poller is the only writer of
`calendar_event`, it replaces a feed's rows wholesale, and one instance runs
(`adr/0025`). `calendar_feed` is edited only by its owner, from one screen, so
there is no second editor to tell about.

No `stored_image` owner column either. An event's description is plain text, not
this product's rich text, so `adr/0032`'s fourth-owner threshold is untouched.

### The cached window, and why it is this wide

The views ask for a window and the cache has to cover the widest one they can
ask for. `monthRange` starts at the Monday of the week containing the 1st and
runs 42 days, so on the 31st of a month beginning on a Sunday it reaches **36
days back**, and in the first week of a month it reaches **35 days forward**.

The poller therefore keeps `[today - 45 days, today + 90 days]`
(`app.calendar.window-days-back`, `window-days-forward`) - the back figure with
headroom over 36, the forward figure wide enough that making My day navigable by
month, which `requirements/0007` expects somebody to ask for, does not need a
second pass over this. Anything outside the window is simply absent, and a view
that asked for it would render an empty day rather than fail.

### Migration path

`V916` adds tables and touches nothing existing, so it applies to dev and prod
with no backfill and no downtime. Phase A changes no schema at all - it renames
a configuration property. `APP_TIMEZONE` is already set in both deployed
environments' env files; `deployment/.env.example` gains a note that it is now
the whole product's zone, and `app.recurrence.zone` is deleted rather than left
as an alias, because two names for one zone is how they come to disagree.

## Backend

New package `com.hatodo.calendar`. It owns the feed aggregate, the fetch, the
parse and the query; `com.hatodo.dashboard` composes its query into the
dashboard response and knows nothing else about it.

```
calendar/
  CalendarFeed.java              @Entity, the aggregate root
  CalendarFeedAccess.java        @Entity, the grant
  CalendarEvent.java             @Entity, one occurrence
  CalendarFeedRepository.java
  CalendarEventRepository.java
  CalendarFeedService.java       CRUD + grants + preview, transaction boundary
  CalendarEventQuery.java        the one read used by the dashboard
  FeedUrlGuard.java              adr/0035's guard, the only way out
  FeedFetcher.java               GET through the guard, size- and time-capped
  IcsParser.java                 bytes -> parsed occurrences
  ParsedEvent.java               record, the parser's output
  CalendarSyncService.java       fetch + parse + replace, one feed, one tx
  CalendarSyncJob.java           @Scheduled, phase D
  CalendarProperties.java        @ConfigurationProperties("app.calendar")
  CalendarFeedController.java    /api/me/calendars
  ...Request/...Response records
```

### Configuration

```yaml
app:
  # The household's zone: one household, one definition of Thursday. Read by
  # the recurrence job and by every date-to-day decision. See docs/adr/0036.
  timezone: ${APP_TIMEZONE:Asia/Singapore}
  calendar:
    enabled: ${CALENDAR_SYNC_ENABLED:true}
    # Every 20 minutes, offset off the hour. Providers regenerate their .ics
    # output on their own schedule (adr/0034), so polling harder buys nothing.
    cron: ${CALENDAR_SYNC_CRON:0 7/20 * * * *}
    window-days-back: ${CALENDAR_WINDOW_BACK:45}
    window-days-forward: ${CALENDAR_WINDOW_FORWARD:90}
    # False everywhere but local and the integration tests, which serve fixture
    # feeds from localhost. See docs/adr/0035.
    allow-private-hosts: ${CALENDAR_ALLOW_PRIVATE_HOSTS:false}
    # A feed stale for longer than this is called out on My day (R71).
    stale-after: ${CALENDAR_STALE_AFTER:PT6H}
```

`@EnableScheduling` goes on the calendar feature's own `@Configuration`, as
recurrence's does, so `enabled: false` disables the job rather than the
annotation. **Integration tests must set `app.calendar.enabled: false`** for the
reason `adr/0025` gives: a job firing mid-assertion is a flake nobody enjoys
diagnosing.

### The dependency

One new library: an iCalendar parser. **biweekly** - a single jar with no
required transitive dependencies, which parses RFC 5545 and iterates a
recurrence rule for you. Pin it exactly and aged, per CLAUDE.md; check Maven
Central for the current release rather than trusting a version written in a
design document.

`ical4j` was the alternative and lost on weight: it brings a transitive set
(commons-*, slf4j bridges, `threeten-extra`) into an application whose entire
dependency list is currently eight starters, minio and the HTML sanitizer.

Writing the parser by hand was considered and rejected on one line of RFC 5545:
recurrence. `RRULE` with `BYDAY`, `BYSETPOS`, `UNTIL` versus `COUNT`, `EXDATE`,
and a `RECURRENCE-ID` override is the part that must be right, and
`adr/0025`'s own refusal to implement RFC 5545 for *generating* chores does not
extend to *reading* a calendar somebody else wrote - we do not get to choose the
subset.

### The guard, and the fetch

`FeedUrlGuard.check(URI)` is the only place a feed URL is approved, and
`FeedFetcher` is the only place one is opened. Both are small enough to read in
one screen, which is the point.

```java
// FeedUrlGuard, in order, failing with FeedUrlNotAllowedException:
//   1. scheme is exactly "https"
//   2. no userinfo, no non-default port below 1024 other than 443
//   3. InetAddress.getAllByName(host): every address must be global unicast -
//      not loopback, link-local, site-local, any-local, multicast, and not in
//      100.64/10
//   4. the message says the address is not allowed and nothing about what was
//      found there
```

`app.calendar.allow-private-hosts` short-circuits the scheme, the port **and**
the host together, and only in the local and test profiles: a fixture `.ics` is
served over plain HTTP on an ephemeral port, so relaxing the host alone would
leave the feature unexercisable. Credentials in the authority stay refused, and
the scheme must still be `http` or `https`.

`FeedFetcher` uses `java.net.http.HttpClient` with
`followRedirects(NEVER)` - redirects are followed by hand, at most three deep,
with `guard.check` on **every** hop, because a public host that redirects to
`127.0.0.1` is the whole trick. Connect timeout 10s, read timeout 20s, and the
body is read through a stream that aborts past 5 MB rather than buffering first
and checking after. `Accept: text/calendar, text/plain, */*`; the response's
declared content type is *not* trusted, because providers serve `.ics` as all
three.

### The parser

`IcsParser.parse(bytes, ZoneId household, LocalDate from, LocalDate to)` returns
`List<ParsedEvent>` and is pure - no clock, no database, no HTTP - which is what
makes the hard cases cheap to test.

What it does, in the order the hard cases appear:

1. **Decide all-day from the value type, not from the time.** `DTSTART` with
   `VALUE=DATE` is all-day and keeps its `LocalDate`; it is **never** converted
   to an instant. This is `adr/0036`'s central point, and the hazard is that
   biweekly resolves a `DATE` against a timezone of its own choosing - so the
   parser reads the property's raw value and `isHasTime()` rather than
   accepting a `java.util.Date` and hoping. **Probe a real Google feed and a
   real Outlook feed before trusting this**, the way `adr/0033` says a
   generation expression must be probed: a test written against what the
   documentation says happens will agree with the documentation.
2. **Resolve a timed start** from the property's `TZID` where it has one, from
   UTC where it ends in `Z`, and from the household zone where it is floating.
3. **Expand recurrence** across the window, taking `EXDATE` and `UNTIL`/`COUNT`
   into account.
4. **Apply overrides.** A moved or edited single occurrence arrives as a second
   `VEVENT` with the same `UID` and a `RECURRENCE-ID`; it replaces the expanded
   occurrence with that key. `STATUS:CANCELLED` and `METHOD:CANCEL` drop the
   occurrence instead.
5. **Clamp and default.** A missing `SUMMARY` becomes `(No title)` rather than a
   null; `summary`, `location` and `description` are truncated to their column
   widths; an occurrence outside the window is discarded here rather than
   stored.
6. **Compute `occurs_on` and `sort_minute`** in the household zone: for an
   all-day event the date itself and `-1`, so it sorts above every timed event
   on its day; for a timed event the local date and `hour * 60 + minute`.

A multi-day event is placed on its **start** day only. Spanning a week view's
columns is a layout problem the grid cannot express today, and "Ben away
Thursday to Sunday" shown on Thursday with its end date in the popup is the
honest 90%.

Nothing in the description is HTML. It is rendered as text, never through
`dangerouslySetInnerHTML`, so `adr/0018`'s sanitizer is not involved and must
not be invoked here - a feed is the one input in this product that nobody in
this household wrote.

### The sync

`CalendarSyncService.sync(feedId)` is one feed, one transaction, and the only
writer of `calendar_event`:

```
fetch -> parse -> DELETE FROM calendar_event WHERE feed_id = ?
                  -> insert the parsed occurrences
                  -> last_success_at = now(), last_error = null,
                     consecutive_errors = 0
```

Replacement is wholesale, and that is the simplification the whole design rests
on: there is no identity to preserve across a refresh, so an event that moved,
an event that was edited and an event that was deleted are all just a different
set of rows next time. No diffing, no tombstones, no update-detection, nothing
to get subtly wrong.

A failure records `last_attempt_at`, a short `last_error` and increments
`consecutive_errors` **and keeps the existing rows** - a provider hiccup must
not empty Thursday. `requirements/0007` R71 is what keeps that from being a
silent lie: the staleness is shown.

Error text is sanitised to a short phrase - the HTTP status, "not a calendar",
"too large", "address not allowed" - never an exception message and never
anything echoed from the response body, because the owner reads it on a screen
and the URL is a secret.

### Authorization

Two predicates, in SQL, in the repository - never a fetch-then-filter in Java:

- **owned**: `calendar_feed.owner_id = :me`. Everything under
  `/api/me/calendars` requires it. An id in a path is not proof; a feed that is
  not the caller's answers **404**, because an unshared feed must not be
  discoverable.
- **readable**: a `calendar_feed_access` row, which the owner holds too - the
  convention `data_list` already follows, so the join alone answers "can I see
  it". The event query joins it, so revoking a grant takes the events out of
  that member's next read with no cleanup at all (R72).

There is deliberately no admin path. The admin administers accounts, not
contents (`adr/0010`), and "which calendars does Ben publish" is not account
administration.

### REST

Under `/api/me/calendars`, because a feed belongs to the caller and there is
therefore no user id in the path to tamper with (`adr/0016`).

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/me/calendars` | the caller's own feeds. **Never** the URL |
| `POST` | `/api/me/calendars/preview` | `{url}` -> what came back; stores nothing |
| `POST` | `/api/me/calendars` | `{label, url, memberIds[]}`; syncs inline |
| `PATCH` | `/api/me/calendars/{id}` | `{label?, url?, memberIds?}`; a URL change re-syncs |
| `DELETE` | `/api/me/calendars/{id}` | cascades the grants and the events |
| `POST` | `/api/me/calendars/{id}/refresh` | sync now - ICS lag makes this worth a button |

`CalendarFeedResponse(id, label, provider, memberIds, lastSuccessAt, lastError,
stale, eventCount)`. There is no `url` component on the record at all, which is
a stronger guarantee than remembering not to populate one.

`PreviewResponse(provider, totalInWindow, List<PreviewEvent> next)` - at most
five, formatted exactly as My day will show them, so R65's promise ("what the
household will see") is literally true.

`memberIds` are validated to be existing users other than the caller; the owner
is never a grant row.

The preview makes an authenticated outbound fetch, so it carries a per-caller
minimum interval (a few seconds) alongside the guard's size and time caps. The
guard is what makes it safe; the throttle is what stops it being an amplifier.

### The dashboard

`/api/dashboard/scheduled` gains two components rather than a sibling endpoint:

```java
public record ScheduledResponse(Instant from, Instant to,
                                List<DashboardItemResponse> items,
                                List<DashboardEventResponse> events,
                                List<FeedWarningResponse> staleFeeds) {}
```

One request, because the grid draws items and events together: two queries would
give one grid two loading states and a visible flash of chores-without-events.
The undated panel is untouched - every event has a date.

`DashboardEventResponse(id, summary, description, location, allDay, startsAt,
endsAt, startDate, endDate, occursOn, sortMinute, List<EventSourceResponse>
sources)`, where a source is `(feedId, label, memberDisplayName,
avatarImageId)`.

**`scope=mine` means feeds the caller owns**, by the same logic
`DashboardService` already applies to items: an event is never assigned, so the
nearest honest reading of "mine" is "my own calendars".

**Collapsing happens here, per request, after the access filter** (R69). It
cannot happen at write time: which feeds a caller may read varies by caller, so
the same two rows are one entry for Ana, who can see both feeds, and one entry
for Ben, who can see one. The key is `(uid, occurs_on, sort_minute)` and the
merge concatenates `sources`, so the popup says "Ben's calendar, Ana's
calendar".

Cross-provider dedup is **not** guaranteed and the design does not pretend
otherwise: Google preserves the originating `UID` on an invitation, Exchange
does not always. A Google-to-Outlook invitation may therefore appear twice.
Collapsing on summary-plus-start was considered as a fallback and rejected -
it would merge two genuinely different events that happen to share a name and an
hour, which is a worse failure than a duplicate row.

`LIMIT` stays as it is for items; events get their own cap of 500 for the same
reason.

## Frontend

### Phase A - the zone

`CurrentUser` gains `timezone: string`. `calendar.ts`'s functions take a
`timeZone` and compute parts through `Intl.DateTimeFormat`, keeping the module's
existing rule - `setDate`/`setHours`, never arithmetic on milliseconds - and
gaining a second one: **no function in this module reads the device zone.** A
`useHouseholdZone()` hook reads it from the current-user query so no component
reaches for the clock's zone by habit.

### API module

`web/src/api/calendars.ts`, with its own DTOs and calls, and **no barrel** -
tests spy by module namespace (`import * as calendarsApi`). `api/dashboard.ts`
gains the `events` and `staleFeeds` types on `Scheduled`.

Queries: `calendarKeys.mine()` for the profile screen. Events ride the existing
`dashboardKeys.scheduled(from, to, scope)` entry, so a view change refetches
exactly once and the popup keeps finding its subject in the cache.

### Profile

`ProfilePage` gains a **Calendars** section under the existing ones: a row per
feed showing the label, the provider, the audience as faces (`ListAudience`,
reused), the last-synced time or a warning, and a refresh action. No URL
anywhere on the screen.

Adding one is a two-step dialog, because R65 makes the preview part of the flow
rather than a nicety:

1. **Paste the address.** With the provider-specific instructions written out -
   Google: calendar settings, "Integrate calendar", *Secret address in iCal
   format*, **not** the public address beside it; Outlook: Settings, Calendar,
   "Publish a calendar". This copy is the onboarding, and
   `requirements/0007` consequence 2 is why it is worth writing properly.
2. **Confirm what came back.** The next five events as the household will read
   them, the count in the window, a label, and the audience checkboxes
   (`useMembers()`, everyone but the caller, none ticked). Save creates the feed
   and syncs it.

A failure at step 1 says which kind: not reachable, not a calendar, address not
allowed, too large.

Deleting says what it takes with it, because R72 means the events go too.

### My day

`MyDayView`, `WeekView` and `MonthView` keep their shape and change what they
iterate.

- `byDay` becomes a merge over one array of **entries**, each an item or an
  event, keyed by the household-zone day and sorted by `sort_minute` - all-day
  events first, then the day's timed events and dated items in clock order.
- `DashboardItemChip` gains a sibling `DashboardEventChip`: distinct enough that
  nobody mistakes an appointment for a chore - no completion affordance, a
  different shape, the time or "All day" as its leading text, and the owner's
  avatar.
- `DashboardItemDialog` gains a sibling `DashboardEventDialog`: title, when,
  where, description as **text**, and which members' calendars it came from.
  Read-only, no actions at all. Both are reached through the same `?item=` /
  `?event=` search param pattern, so one popup is open at a time and a
  deep link still works.
- A stale feed shows one dismissible-per-session line above the grid: *"Ben's
  Work calendar last synced on Tuesday - events may be missing."* This is R71,
  and it is the line that keeps an empty Thursday from being a lie.

No new navigation. No Calendar in `SideNav`, and no route of its own.

## Testing

### Unit - `app/src/test/java/com/hatodo/calendar/`

- **`FeedUrlGuardTest`** - the decision table, exhaustively, because this is the
  file whose bugs are security bugs: `http://`, `file:`, `ftp:`; a host
  resolving to `127.0.0.1`, `::1`, `10.x`, `192.168.x`, `169.254.x`, `fc00::`,
  `100.64.x`; userinfo in the authority; a port of 5000; and the allowed case.
  Plus `allow-private-hosts: true` flipping exactly one of those and no other.
- **`IcsParserTest`** - the cases that actually break, each as a fixture file:
  an all-day event (asserting a `LocalDate` and that no instant was
  manufactured); an all-day event parsed while the JVM default zone is UTC and
  the household zone is `Asia/Singapore`, asserting the date does not move -
  this is the regression `adr/0036` exists for; a floating `DTSTART`; a
  `TZID`-qualified one; a weekly `RRULE` with `EXDATE`; a `RECURRENCE-ID`
  override that moves one occurrence; a `STATUS:CANCELLED` occurrence; an
  occurrence outside the window; a missing `SUMMARY`; an over-long summary; a
  multi-day event placed on its start day; and a feed whose bytes are HTML
  because the address was wrong.
- **`CalendarFeedServiceTest`** - a non-owner gets 404; `memberIds` containing
  the caller is rejected; a duplicate URL for one owner is rejected; a URL
  change triggers a re-sync; no response record can carry a URL (a reflective
  assertion over `CalendarFeedResponse`'s components, so adding one later fails
  a test rather than leaking a calendar).
- **`CalendarSyncServiceTest`** - a failed fetch keeps the existing rows and
  records the error; a successful one replaces them; the error text contains
  neither the URL nor an exception class name.
- **`DashboardServiceTest`** - collapsing two feeds' identical `uid` into one
  entry with two sources; *not* collapsing when the caller can only read one of
  them; `scope=mine` limiting events to owned feeds.

### Integration - `app/src/integrationTest/java/com/hatodo/calendar/`

Testcontainers Postgres as everywhere else, `app.calendar.enabled: false`, and
`allow-private-hosts: true` so a feed can be served locally.

- A **fixture feed server** - `com.sun.net.httpserver.HttpServer` on an ephemeral
  localhost port, no new dependency - serves `.ics` files from test resources and
  can be told to answer 404, 500, a redirect to `127.0.0.1`, a 6 MB body, or to
  hang. This is `adr/0034`'s testability claim being cashed in, and the redirect
  and oversize cases are `adr/0035`'s guard tested end to end rather than in
  isolation.
- The whole write path over HTTP: preview, create, the events landing in the
  table, `GET` returning no URL, a grant letting a second member read the events
  and a revocation taking them away in the same test, delete cascading.
- `GET /api/dashboard/scheduled` returning items and events in one payload for a
  member with a grant, and only items for one without.
- An ungranted feed id answering 404 for a `PATCH`, a `DELETE` and a `refresh`.

### Frontend - `web/src/`

- `calendar.test.ts` gains the zone cases: a day boundary computed for
  `Asia/Singapore` while the test runner's zone is UTC, and the same for the
  week and month grids. `vi.stubEnv`/`TZ` is the existing pattern.
- `DashboardPage.test.tsx` - an event and an item on the same day rendering in
  clock order with the all-day event first; the event dialog opening read-only
  from a chip; the stale-feed line appearing from `staleFeeds`.
- A new `CalendarFeeds.test.tsx` - the two-step add dialog, the preview being
  shown before anything is saved, a save posting the audience, and the rendered
  row never containing the URL the test typed.

### E2E - `e2e/tests/test_calendars.py`

Honest about a real limitation: **dev's guard refuses private hosts**, so the
suite cannot serve a fixture feed to a deployed environment, and there is no API
that writes a `calendar_event` other than the poller. So e2e covers the profile
screen - adding a feed with an unreachable-but-public address and getting the
error, the address never appearing in the DOM, a feed being shared with the
second member and the row showing it, deletion - and **not** events rendering on
My day, which is covered by the integration test and the frontend test instead.
That gap is stated rather than papered over; pointing a test at a real public
calendar would buy one assertion and an external dependency that breaks the
suite on somebody else's schedule.

The existing `test_dashboard` assertions must keep passing with phase A merged,
which is the check that the zone change did not move anybody's chores.

## Rollout

Phase A alone first - it changes My day's bucketing for every member and nothing
else, so it is worth being able to deploy and look at on its own. Then B, C, D
in order, each verified in a browser against the local stack and then against
dev with `e2e/` as CLAUDE.md's workflow requires.

`deployment/.env.example` gains `APP_TIMEZONE` (documented as the household's
zone, not just recurrence's), `CALENDAR_SYNC_ENABLED`, `CALENDAR_SYNC_CRON` and
`CALENDAR_ALLOW_PRIVATE_HOSTS` with a warning that it stays false outside local.
`scripts/deploy.sh` needs no new required variable: every calendar property has
a working default.

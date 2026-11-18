# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

**Everything specified so far is built, as of 2026-11-11.** Build, local stack, deploy scripts and all
three test harnesses are real and passing: 296 backend unit tests, 276 integration tests, 407 frontend
tests, 109 end-to-end (13 skip without `E2E_ADMIN_PASSWORD`).

Implemented: **accounts and administration (R8-R12)** — username identity, optional per-user passwords, a
pick-a-user sign-in screen, and a single admin who adds and deletes users. **The PWA (R13, R14)** —
installable as "List", with an Android share target. **The UI shell (R15-R17)** — now a sidebar rather
than the original bottom bar, master-detail administration, and a sign-in roster that describes nobody.
**R18-R35 are implemented.** Material Design throughout; the list entity with per-list access grants,
pins, recency and search; the per-list field configuration including named costs; profile pictures in
object storage; and items — values for every declared field, assignment bounded by access, completion
with points credited to whoever did the work, the detail field's rich text and embedded pictures, and a
shared link becoming a real item.

`docs/design/0004` describes that feature and `docs/adr/0010`-`0021` record its decisions.

**R36-R49 are implemented too**, in the five phases `docs/design/0005` lays out, with
`docs/adr/0022`-`0028` recording the decisions. Completion stores and shows how the work went, including a
rich-text note and its pictures; an item row opens to explain itself, one at a time; the add form starts
with only what the list insists on; work is claimed in bulk and the claim reads the same to everybody;
reopening keeps the record and returns the points; a list has tabs — items, standing instructions, and
who did what lately; and **the dashboard is the landing page**, reading across every list a member can
see.

**R50-R61 are implemented**, in the eight steps `docs/design/0006` lays out, with `docs/adr/0029`-`0033`
recording the decisions (`0033` supersedes `0030`). **Data lists** — called *collections* in the UI — are
reference lists: recipes, restaurants, quotes, vocabulary. An entry has a name, a description, a
rich-text detail and free-form tags, and is *never* assigned, dated, completed, scored or generated on a
schedule. Search reads the whole entry — name, description, the detail's text, tags — within one
collection and across every collection a member can see, finding a word by its middle as well as its
start. A shared link can become an entry as well as an item.

**Four things in there are easy to break.** A data list is **its own aggregate** — `data_list` /
`data_list_access` / `data_entry`, not a `kind` column on `todo_list` — because nine existing readers of
`todo_list` would otherwise have to remember to exclude it, and the one that forgets is wrong without
failing (`docs/adr/0029`). An entry's `search_vector` is a **generated** `tsvector` fed by
**application-maintained** `detail_text` and `tags_text` columns, so `DataEntry` exposes one
`setDetail(html, text)` and one `setTags(tags)` and **never a setter for a single column** — a caller
able to write one half is a search index that quietly stops matching (`docs/adr/0033`). `search_vector`
is deliberately **not mapped in JPA at all**. And `DataEntrySearchQuery`'s statement is assembled with
`String.replace`, not `String.formatted`: it is full of SQL comments, and the first one to contain a
per-cent sign makes it an invalid format string, which fails in a static initialiser and surfaces as an
unsatisfied bean dependency with a null cause.

**`setweight` over `array_to_tsvector` taught the general lesson** (`docs/adr/0033`): a generated column
Postgres *accepts* is not a generated column that computes what was intended. The original expression
created, indexed and queried without complaint and was inert, because a weight is a property of a
position and `array_to_tsvector` emits none. Probe a real database before trusting a generation
expression; a test written against the schema as described would have agreed with the description.

Collections had **no pins and no recency** at all until 0012. They now have both, each in a table of
their own — `data_list_user_state` for the pin (`docs/adr/0043`) and `data_list_interaction_day` for how
often they are opened (`docs/adr/0041`). `list_user_state.list_id` is still an FK to `todo_list` and
**still must not be loosened**: that constraint is why these are separate tables rather than a widened
one. The share picker still offers collections by name and cannot preselect one — it falls back to a
collection only when the member has no lists at all.

**Two things in there are easy to break.** "Finished" and "no longer work" are **two different
questions** since 0015. The second is answered by a *generated* column — now called `closed`, not `done`
(`docs/adr/0024`, widened by `docs/adr/0048`) — and the first by `TodoItem.isCompleted()`, which must
exclude a lapse as well as a reopen. A new reader written against `completed_at`, or one that reaches for
`closed` when it meant "was this finished", will compile, pass, and be wrong. And the recurrence job is
the only thing in the product that acts without being asked — see the scheduler paragraph below.

The open questions left in `docs/requirements/0004` are now two, not three: whether earned points can be
revoked **by a list's owner** (0005 R49 settles only the reopen path), and whether a list should show who
else has it open. Where a completed item goes is **answered** by 0005 R38 — it stays in place, struck
through, with its detail and completion note one tap away.

**R62-R72 are implemented.** `docs/requirements/0007` and `docs/design/0007` add **calendar feeds**:
a member subscribes somebody else's calendar by its iCalendar URL (Google's secret iCal address,
Outlook's published link), grants other members access to it per feed, and its events appear on the
agenda *merged* with the chores. Nothing here authors an event, there is no Calendar menu, and there is no OAuth
— `docs/adr/0034` records why a read-only `.ics` subscription beats two application registrations for a
household of four, and what it costs (the copy lags its source by up to hours, which is why there is a
refresh button). Subscriptions live in the **profile**, not behind a menu entry.

**Four things in there are easy to break.** The feed address is a **bearer capability** for somebody's
whole calendar, so `CalendarFeedResponse` has **no component** it could be returned in — a stronger
guarantee than remembering not to populate one, and `CalendarFeedServiceTest` asserts over the record's
components so adding one fails a test rather than leaks a calendar (`docs/adr/0035`). Every fetch passes
`FeedUrlGuard`: https, no credentials, no port, every resolved address public, **re-checked on each
redirect** — a public host redirecting to `127.0.0.1` is the whole trick, and the app container can
otherwise be made to GET the unauthenticated registry on `192.168.1.248:5000` and hand the response back
in the preview. `app.calendar.allow-private-hosts` relaxes scheme, port *and* host together (a fixture
`.ics` is plain HTTP on an ephemeral port) and is true only in `local` and `itest`. And **biweekly
resolves `DTSTART;VALUE=DATE` against `TimeZone.getDefault()`**, ignoring both the parser's
`defaultTimezone` and the zone given to `getDateIterator` — probed, not assumed — so an all-day
occurrence is the **raw date plus the iterator's day offset** and never an instant; `IcsParserTest` pins
it across four server zones.

**The household now has one timezone** (`docs/adr/0036`): `app.timezone` (`APP_TIMEZONE`, default
`Asia/Singapore`) replaced `app.recurrence.zone`, is served to the SPA on `/api/me`, and governs the day
bucketing of **items** too — one merged grid cannot have two definitions of Thursday, so the agenda no
longer follows the device's zone. `occurs_on` is application-maintained rather than generated, because
`AT TIME ZONE` is STABLE rather than IMMUTABLE and Postgres refuses it in a generation expression.

Recurrence is expanded at parse time, one row per occurrence, and **collapsing is per occurrence**
(`uid` + day + minute) rather than per series — a weekly event keeps all its occurrences even when two
members' feeds both carry it. An override the expansion never matches is **still emitted**, and this is load-bearing rather than an
edge case: **not every publisher writes an `RRULE`.** Some expand a series into one `VEVENT` per
occurrence sharing a UID, where every occurrence after the first carries a `RECURRENCE-ID` and the master
carries no rule at all — expansion then yields one slot, every child matches nothing, and a weekly event
shows up **once**. That is what dropping unmatched overrides looked like from the outside, and
`IcsParserTest.readsASeriesWrittenAsOneMasterPlusRecurrenceIdChildren` is the regression.

A refresh **replaces a feed's rows wholesale** — there is no identity to preserve, so a moved, edited or
deleted event is just a different set of rows next time. A failed refresh **keeps the rows it has** and
records a phrase written for the owner's screen; R71's staleness notice on the agenda is what stops that
being a silent lie, so do not quietly drop it. Collapsing two feeds' copies of one appointment happens
**per request, after the access filter**, because which feeds a caller may read decides whether two rows
are one entry. Cross-provider dedup is not guaranteed: Exchange does not always preserve the originating
`UID`.

**One e2e gap is deliberate**: a deployment's guard refuses every address on the machine running the
suite, so `e2e/tests/test_calendars.py` covers the profile screen and the refusals, and the
subscribe-fetch-store path is covered by `CalendarFeedApiIntegrationTest`, which serves itself a real
`.ics` over HTTP.

**Unrelated, pre-existing**: two `e2e/tests/test_recurring.py` tests fail against the **local** stack
because `deployment/docker-compose.local.yml` sets `RECURRENCE_CATCH_UP_DAYS=7` while they assert
"Added 1."; they pass with `RECURRENCE_CATCH_UP_DAYS=0` and against dev, which uses 0. They did
**pass** against local on 2026-11-07 with the setting unchanged, so the failure is conditional on how
many occurrences the catch-up window actually reaches rather than guaranteed - do not read a green local
run as evidence the setting was fixed.

**R73-R78 are implemented.** `docs/requirements/0008` and `docs/design/0008` make the agenda
**navigable**,
which is the open question 0007 left. The tabs are named for the range - **Day, Week, Month** - rather
than for the present, there are previous/next controls and one that returns to the current period, and
`docs/adr/0037` puts the period on screen in the address as `?on=YYYY-MM-DD`, omitted when it is today.
Nothing became stateful: `adr/0015`'s rule still holds, with `AppShell` owning the only layout state.

**The surface is now called "Agenda"**, not "My day" (R78) — the old name was wrong in both words once
the period could move: it showed a week or a month, and its default scope is the whole household with
"Mine" as a toggle. **The route is still `/dashboard`** and must stay: the manifest's `start_url` points
at it (`adr/0007`). Deliberately *not* "Calendar", which would promise that a member can add an event —
the one thing 0007 R63 permanently refuses. Documents written before this (0005, 0007 and their ADRs)
say "My day" throughout and mean the agenda; they are left alone, because an accepted document records
what was decided when it was decided.

**The mistake this makes available** is conflating two things that used to be the same: the period being
*looked at* (the anchor, from the URL) and the period we are *in* (the clock). Layout reads the anchor;
today's highlight in the week and month grids, and whether the return control is enabled, read
`now(zone)`. A new reader that takes "today" from the anchor will be right every day except the ones
somebody navigated away on. `?on=` is parsed defensively and falls back to today, because this is the
landing page and a mangled address must not be a member who cannot get in.

**And navigating past what the poller has read is now possible**, so `ScheduledResponse` carries
`eventWindow` (from `CalendarWindow`, i.e. `CALENDAR_WINDOW_BACK`/`_FORWARD`) and the agenda says when the
range on screen falls outside it. Items have no such bound, so a member four months out sees their
chores and none of the appointments; the notice names the half the product knows it does not know. An
empty February that means "nobody looked" must never read as "nothing is on".

**R83-R87 are implemented.** `docs/requirements/0010` and `docs/design/0010` make the agenda
**writable in exactly one way** — two ways since 0015 R138, which added lapsing: a chore can be completed from it — from the Day view's rows and from the
popup the week view, the month view and the undated panel open — with the hold-for-a-note accelerator the
list has. **Frontend only** (`docs/adr/0039`): `PUT /api/items/{id}/completion` is per-item, checks the
caller's grant and compares the version first, and `DashboardItemResponse` already carried `version`.

**The scheduled query keeps `closed = FALSE`** (`done` until 0015), so a completed chore *leaves* the agenda rather than
staying struck through as it does on a list — the agenda answers "what is left", and a month grid of
one-line chips filled with finished work answers something else. (Since 0013 a list leaves it struck
through only when the status filter is showing Done; under the default To do filter the row goes there
too, which is what R119's snackbar is for.) A snackbar says what happened, because
the row vanishing cannot be the only feedback.

**The thing this breaks is a premise.** `dashboard/queries.ts` said its keys could live outside the
per-list hierarchy because the dashboard never writes. It writes now, so `useCompleteFromAgenda`
invalidates **both `['dashboard']` and `['lists']`** — the two trees are siblings and neither prefix
reaches the other. A second writer added there that forgets `['lists']` leaves the list it came from
showing an open chore, with nothing to make it refetch and no failure anywhere. Reopening is deliberately
**not** on the agenda: a completed chore is not there to reopen, and the record lives on the list.

**R88-R93 are implemented.** `docs/requirements/0011` and `docs/design/0011` put **overdue work** on
the agenda, which until now was invisible: the ranged query is half-open on the window the client asks
for, so a chore due last Tuesday existed in last Tuesday's window and nowhere else — not in today, not in
the undated panel (`dueAt IS NULL`, and a late chore has a date), not in any count. `GET
/api/dashboard/overdue?scope=` is a third cross-list read, modelled on `/undated`: same SQL access guard,
same capped page plus separately counted total. It is drawn as a collapsible strip **above** the period,
beside the stale-feed and out-of-window notices, and carries the same `CompleteControl` the Day row does.

**Three things in there are easy to break.** The boundary is **the start of today in `app.timezone`**,
computed by `DashboardService`, and the endpoint **takes no window** — a bound a caller supplies is a
bound the period on screen can leak into, and "overdue" would quietly come to mean "before whatever month
I am looking at" (`docs/adr/0040`). Day granularity, not `< now()`, is what keeps a chore due at 09:00 in
today's row until midnight instead of jumping out of it mid-morning and being drawn twice either side of
the cutoff. And `useCompleteFromAgenda.onMutate` hand-writes **three** payload shapes now — a fourth
dashboard query whose shape it does not know still *works*, because `onSettled` invalidates
`['dashboard']` wholesale, so the row merely lingers for one round-trip and **no test fails**; that is the
same invisible-defect shape as the `['lists']` invalidation above, and
`DashboardPage.test.tsx`'s "takes the row out before the server answers" is the regression (it completes
against a promise that never resolves, so the refetch cannot do the work for it).

`AgendaItemRow` is now shared by the Day view and the overdue strip, differing only in its caption — the
`ButtonBase`-with-a-sibling-control shape exists once, which is where `adr/0039`'s invalid nesting would
otherwise grow back.

**R94-R103 are implemented.** `docs/requirements/0012` and `docs/design/0012` are two small frictions in
the gesture a member performs most often, with `docs/adr/0041` and `0042` recording the decisions.

**Dating a chore is now two taps** (R94-R97). `items/DueAtField` replaces the bare `datetime-local`:
Today and Tomorrow chips, −/+ day steppers, a separate time box **defaulting to 23:59**, and a Clear
chip when the list does not insist on a date. The ISO instant stays the single source of truth — the
component holds no copy of the date, so an item loaded for edit cannot leave it showing something the
form is not holding. Its one piece of local state is `pendingTime`, the time typed before a date was
picked.

**Two things in there are easy to break.** The conversion is `instantOf(...)` from
`dashboard/calendar.ts`, **never `new Date(localString)`** — the old input spoke the *device's* zone, so
"Thursday 09:00" typed on a phone set to London was an instant the agenda filed under Wednesday, which
is the one bug `adr/0036` exists to prevent. `ItemForm`'s `toLocalInput`/`fromLocalInput` were
**deleted** rather than left for reuse: a helper by that name sitting in the file is an invitation back.
And 23:59 rather than midnight is load-bearing, not cosmetic — midnight belongs to the day *before* the
one a member meant, so a chore dated Thursday would sit on Wednesday's row and read as overdue by
Thursday lunchtime.

**The sidebar reaches every list and collection** (R98-R106). **Recent is gone**; **Pinned stays** and
now holds both kinds together. Recent showed only lists with a recorded interaction, so a list just
created — and every list somebody else shared — was absent until opened once, which is the opposite of
what a shortcut is for. Below the pinned section, Lists and Collections are expandable groups holding
everything **not** pinned, both open by default, ordered by how often the member opened each one over the
last month, capped at `sidebar_list_count` (R102, default 10, bounded 1-30, beside the theme on
`app_user`).

**A collection can be pinned** (R104, `docs/adr/0043`), which `adr/0029` and the first cut of 0012 both
said it could not. It lives in **`data_list_user_state`** — a table of its own, because
`list_user_state.list_id` is an FK to `todo_list` and that does not get loosened. It is deliberately
**thinner** than `list_user_state`: a pin and nothing else, since a collection's recency comes from its
interaction buckets. `adr/0043` names the three statements in accepted documents it overrides.

**The pinned section and the two groups partition** what a member can see — both `findForSidebar` queries
carry `WHERE s.pinned_at IS NULL`, so a pinned thing *leaves* its group rather than appearing twice. That
is also what keeps the cap honest: pin twelve things with a preference of ten and you still get a group
underneath them. The pinned section is **uncapped and scrolls** (`PINNED_MAX_HEIGHT`, 15rem) — silently
dropping somebody's twelfth pin is the sidebar overruling an explicit instruction. It is merged **by
name**, not by pin time: the two halves come from two requests with no shared clock, and the alternative
is a per-member timestamp on DTOs the whole app reads.

**Frequency is a pruned daily bucket, not a counter** (`docs/adr/0041`). `list_interaction_day` /
`data_list_interaction_day` hold one row per (list, member, household day) with `hits` and `last_at`. A
monotonic `interaction_count` would rank *lifetime* use and never forget, which is the decay R100 asks
for; per-visit rows would need a second `@Scheduled` job, and `adr/0025` says that costs more than it
looks. Three things fall out of the shape and all three are why it was chosen: the 15-minute dedup
window is read from the row being written (`ON CONFLICT ... DO UPDATE ... WHERE last_at < ...`), so
**collections get frequency without getting a state table** — `list_user_state.list_id` is still an FK to
`todo_list` and must not be loosened; each `record` is followed by a `prune` scoped to the **member**,
not the list, because the buckets worth dropping belong to the lists they *stopped* opening; and the
table is bounded by construction.

**Six things in there are easy to break.** The window is enforced by **two independent statements**
sharing one constant (`InteractionWindow.DEDUP_MINUTES`) — they can legitimately disagree at midnight,
and making one read the other's row reintroduces an ordering dependency between two writes in one
method, after which the second silently stops counting. `on_day` is **application-supplied** in
`app.timezone` for the reason `occurs_on` is. Both group queries are **native** and both outer joins
are **LEFT** — that is R98, and a reader who "tidies" one into an inner join quietly restores the old
only-what-you-opened behaviour. `useThemePreference.onMutate` now **merges** into the cached
preferences rather than writing `{ theme }` over them: that row carries more than the theme, and
replacing it leaves the sidebar size undefined until the server answers. A pin must **never** be stored
on `*_interaction_day` — those rows are pruned on a rolling thirty-day window, so the pin would silently
expire. And `app/repin.ts` distinguishes a summary from a sidebar envelope by
`typeof record.pinned === 'boolean'`, because an envelope's `pinned` is an **array**; it deliberately
does not move a row between the envelope's two arrays, so the star flips at once and `onSettled`
relocates the row.

**Which groups are open is `SideNav`'s own state** (`docs/adr/0042`), which narrows `adr/0015` again
rather than dropping it: disclosure is not placement. The test for the next component that wants state
is "is this about where something sits, or about something the member did?" — placement is still CSS,
nobody else calls `useMediaQuery`, and the state is deliberately **not persisted**. The chevron and the
create button are **siblings** of the group's `ListItemButton`, not children, so the label still
navigates and the invalid nesting `adr/0039` had to undo once does not grow back. The sidebar now asks
**twice** — `GET /api/lists/sidebar` and `GET /api/data-lists/sidebar` — because the groups are
independent and nothing is drawn from both, so there is no pair of values that can disagree; a
cross-aggregate read would be a third place that has to remember collections exist. `SidebarResponse`
lost its `pinned`/`recent` split rather than gaining a field, so every reader broke at compile time.

**The sidebar's groups distinguish three states, and must keep doing so.** `pending`, `error` and
`ready`-but-empty are separate, because a group that reads its rows out of `data ?? []` is empty while
the request is in flight **and** empty when the request failed — and in both cases announced "No lists
yet", which is a confident lie about somebody's own data, shown for as long as the connection is slow or
forever. `PinnedSection` had a `loaded` guard for exactly this from the start; `NavGroup` did not, and
that shipped. `SideNav.test.tsx`'s "does not claim a group is empty while it is still loading" resolves
against a promise that never settles, so the query cannot rescue the assertion. `PinnedSection` needs
only **one** of the two requests to have succeeded — requiring both hid pins that had loaded fine when
the other half failed.

**A latent e2e bug was fixed in passing**:
`test_dashboard.py::test_opening_an_item_is_a_url_the_back_button_closes` loaded
`/dashboard/week` and looked for a chore due *tomorrow*. Weeks start Monday, so it passed six days out
of seven and failed on a Sunday. It now anchors on `?on=<the chore's day>`.

**R107-R122 are implemented.** `docs/requirements/0013` and `docs/design/0013` make a list
**filterable and sortable**, with `docs/adr/0044`-`0046` recording the decisions. A list is narrowed by
status, by assignee (including *unassigned*), by due date (overdue / today / next 7 days / undated / an
explicit range) and by **each cost it declares** - choices for a FIXED cost, a range for a NUMERIC one -
and ordered by one key in either direction, manual order still being the default.

**It all happens in the browser, over one unfiltered fetch** (`docs/adr/0044`). `ItemResponse` already
carried every input, a list is never paginated, and a household's list is not big enough to justify a
second implementation of these predicates in SQL. So the server's `status` parameter is **gone** -
`findForList(listId)` takes no filter, and `ItemStatus` was deleted from the client rather than left
exported. **The threshold for revisiting this is pagination**: the moment the client stops holding the
whole list, every predicate has to move to the server *at once*, because a filter applied to a page is a
filter applied to a sample.

**Status is a filter dimension, not a `FieldKind`** (`docs/adr/0046`). To do and Done are independently
selectable chips; "All" is gone, because it was a word for the empty filter. Both selected and neither
selected both mean *everything* - an empty set is an absent filter everywhere on that bar, and a list
that silently shows nothing because two chips were unticked is a bug nobody can diagnose. A per-list
status with choices (Blocked, Waiting) was declined deliberately: it would change what "done" means for
the dashboard, the agenda, the leaderboard, the recurrence job and the overdue strip.

**The filter lives in the address** (`docs/adr/0045`), as `?status=&who=&due=&cost.<fieldId>=&sort=&dir=`,
written with `replace` so four taps are not four history entries, and the default view writes **nothing** -
the same rule `?on=` follows (`adr/0037`). Parsing is defensive and **independent per value**: a broken
`due` must not take the status filter with it, and an id naming a deleted cost, a removed choice or a
revoked member is dropped rather than left to hide every row.

**Five things in there are easy to break.** `items/filters.ts` takes **today as a `Civil`**, never a
clock: `ItemsSection` calls `now(zone)` *inside* its `useMemo`, because a `Civil` computed during render
changes identity every time and `new Date()` there is what the purity lint rule exists to catch. Due
buckets are **day-granular in `app.timezone`** for `adr/0040`'s reason - a chore due at 09:00 is today's
until midnight. An item that never answered a cost is **excluded** by any active filter on it unless "Not
answered" is ticked (R111): a blank is not a small amount. Blanks sort **last in both directions**, which
is one `null` branch in `visibleItems` rather than one per key - and the sort's tie-break is the item's
index in the fetched array, because `ItemResponse` carries no `position`. And **manual order is that
array's order**, so a reader who "helpfully" sorts the fetched items before handing them over destroys
it silently.

**The thing this changes about completing** is that the row now leaves *at once* rather than after the
refetch, since the optimistic write flips `completed` and the default filter is To do. R119 is why
`ItemsSection` raises a snackbar naming what was finished - the same rule the agenda follows
(`adr/0039`). In `e2e/`, that snackbar contains the item's description, so a
`get_by_text(description)` assertion after a completion now matches **twice** and fails strict mode;
assert on the row's own disclosure button instead. `conftest.show_status` is how a test changes which
statuses a list shows.

**R131-R140 are implemented.** `docs/requirements/0015` and `docs/design/0015` give an item a **second
terminal state**: *lapsed* — planned, and nobody got to it. It earns nothing, it leaves the agenda and
the overdue strip, it is recorded with who and when, and it reopens exactly as a completion does.
`docs/adr/0048` records the decisions. The point is that the two states a member had were both wrong for
this: leaving the chore open makes the overdue strip a permanent reproach, and ticking it complete
credits points and lands in the activity feed for work nobody did.

**The generated column was widened *and renamed*** — `done` → `closed`,
`(completed_at IS NOT NULL OR lapsed_at IS NOT NULL) AND reopened_at IS NULL`. Widening is what makes
the five agenda predicates right without being touched; a second column beside `done` would have left
them reading the wrong one with nothing to make them fail. Renaming is the load-bearing half: a name that
answers a different question than its definition is `adr/0024`'s "seventh reader" with a head start.

**Four things in there are easy to break.** There is deliberately **no mutual-exclusion constraint**
between `completed_at` and `lapsed_at` — complete, reopen, lapse is legal and keeps the completion, for
the reason `adr/0024` keeps it — so **`pointsByMember` must spell out a standing completion**
(`completedAt IS NOT NULL AND reopenedAt IS NULL AND lapsedAt IS NULL`) rather than reading `closed`,
which would credit a zero-point row to somebody who earned nothing. `completeBy` clears a lapse but
`lapseBy` does **not** clear a completion, and that asymmetry is deliberate: a completion is history
somebody reads (R43), a lapse is only ever current state. Every transition clears the **reopen stamp**,
without which `closed` stays false and the chore sits on the agenda looking open. And R133 — a lapse is
worth nothing — is a **CHECK**, not a convention, so a lapse path that forgets to null the points fails
loudly.

**The agenda now has two verbs**, which widens `adr/0039`'s "writable in exactly one way". One mutation
serves both, because a row reaching *either* terminal state simply leaves — so the three hand-written
optimistic payload shapes needed no new case, and the `['dashboard']` **and** `['lists']` invalidation
still has to survive every refactor of it. Reopening is still deliberately absent from the agenda.

**The client's boolean became a three-valued `state`**, and replacing it rather than adding beside it is
what found four binary assumptions — above all `matchesFilter`, whose "fewer than all selected means
exactly one, so read `status[0]`" was correct for two statuses and silently wrong for three. The wire
renamed with it: `PUT /api/items/{id}/completion` is now `/state`, and `CompletionRequest` is
`ItemStateRequest`.

**R123-R130 are implemented.** `docs/requirements/0014` and `docs/design/0014` put a **list filter on
the agenda**, with `docs/adr/0047` recording the decisions. A **Lists** chip beside the Everything /
Mine toggle opens a checkbox menu of every list the member can see; picking some narrows the Day, Week
and Month views, the overdue strip and the undated panel. Picking none means every list - an empty set
is an absent filter, the same rule the list's bar follows (`adr/0046`). The selection lives in the
address as `?lists=<id>,<id>`, written with `replace`, omitted when empty.

**Frontend only, over the fetch already being made** (`adr/0047`). `DashboardItem` already carried
`listId`, and `DashboardService` caps each query at 500 as a safety bound rather than paginating, so
the rows in hand are the rows there are; the query keys deliberately do **not** mention the filter, so
one cached payload serves every selection and ticking a list costs no request. The revisit threshold is
`adr/0044`'s, inherited: the day a dashboard payload is genuinely paginated, this predicate moves to
the server, because a filter applied to a page is a filter applied to a sample.

**Three things in there are easy to break.** **Events are passed through unfiltered on purpose** -
`byDayEntries(onlyLists(items, lists), events, zone)` is the shape in all three views, because an event
belongs to a calendar feed and has no list (R126); a view that "tidies" this into filtering both
arguments takes somebody's appointments off their agenda and nothing outside `DashboardPage.test.tsx`
fails. **`total` is a server count a client filter cannot narrow**, so while a filter is active the
undated panel and overdue strip count the rows they are showing and say "and possibly more" rather than
subtracting their way to a confident number about rows they never saw. And **`known()` runs once, in
`DashboardPage`**, not per view: it drops ids for lists the member cannot see, which can empty the
selection - and an empty selection is no filter, so a link naming a deleted list shows the **whole**
agenda rather than an empty one (R128). A view that narrowed for itself would filter every row out
while the chip showed no filter at all.

`MenuChip` now lives in `web/src/app/MenuChip.tsx` and is shared by the list's `FilterBar` and the
agenda's chip. The menu's options come from `useLists({})`, **never** `useSidebar()`, whose `lists`
array is capped at `sidebar_list_count` and partitioned against the pinned section.

**R141-R147 are implemented.** `docs/requirements/0016` and `docs/design/0016` move **one** permission,
with `docs/adr/0049` recording the decisions: **any member with access to a list can add a standing
instruction to it**, and editing or deleting one needs to be its **author or the list's owner**. 0005 R41
needed ownership on 0004 R20's reading - a rule is as much the shape of a list as its fields - and that
reading was wrong in a specific way: a field constrains every item anybody adds, a rule constrains
nobody and only adds items, which is the one thing a grant has always meant. So the product allowed the
larger act and refused the smaller one. No migration: `recurrence_rule.created_by` has existed since
`V910` and was simply never read.

**Three things in there are easy to break.** The two-clause predicate is written **once**, as
`RecurrenceRule.isWritableBy(userId)`, because two callers need the same answer - the service's write
path, which refuses, and `RecurrenceRuleResponse`, which publishes it as `editable` for *this* caller.
A second copy is how a member gets an edit button that 403s, or - worse, because nothing fails anywhere -
no button on a rule they are entitled to edit. The client therefore renders the flag and **never**
recomputes it from `list.owned` plus `useCurrentUser()`; `useCurrentUser` is read in `RecurrenceSection`
only to decide whose name is worth printing. And the private helper `managed(ruleId)` became
`writable(ruleId)` and asks **`isReadable` first**: the two refusals are not interchangeable, since a
rule on a list the caller holds no grant on must stay a 404 (R19) while one they can see but did not
write is a 403. The old shape called the throwing ownership check and caught it to decide which, which
cannot express this rule at all - ownership is no longer the question. `ListNotManagedException`'s
handler in `RecurrenceExceptionHandler` was **replaced** rather than left beside the new one: nothing in
these paths calls `requireManaged` any more, so "Only the list's owner can change what recurs on it" is
a sentence nobody could get the product to print.

Everything else about a list stays the owner's (R147): its fields, its costs, its grants, its name, its
deletion. The author is on screen for the first time (R142) as a `by <name>` chip, omitted when the
author is the signed-in member - "by Ana" on four of Ana's own rules teaches nothing.

**R148-R152 are implemented.** `docs/requirements/0017` and `docs/design/0017` narrow 0005 R39, with
`docs/adr/0050` recording the decisions. **The collapsed add form now shows the assignee and the due
date whenever the list declares them**, required or not. R39's reasoning stands - a form asking six
questions for "milk" is why somebody reaches for paper - but **`required` was a proxy for the wrong
property**. What is worth hiding is a field *expensive to answer*; these two cost a tap (0012 R94 made
the date one), and **every planning surface in the product reads them**, so the form's most ordinary
path was producing items the agenda, the overdue strip, the Mine toggle and the week and month grids
cannot see. Frontend only: `SaveItem` already carried both.

Collapsed, the due date is **the chips alone** - `DueAtField` takes a `compact` prop and returns after
the chip row; the picker, the time box and the steppers are what the disclosure is for. The assignee is
**a row of chips in both modes** (`items/AssigneeField`), one per member plus **Nobody** when the field
is optional, carrying `aria-pressed` because a row of toggles whose state lives only in a fill colour is
unreadable to anything not looking at it. `MemberAvatar` rides along as decoration and stays
`aria-hidden`: a member with no picture has their initials drawn *inside* the chip, so Ana's
`textContent` reads "AAna" while her accessible name stays "Ana" - assert on roles and names, never on a
chip's text.

**Two things in there are easy to break.** `ItemForm`'s `hidden` count decides whether "More fields"
appears, and it used to equal "declared and not required" - which **stopped being the same question**.
The assignee must *not* count (a toggle that reveals nothing) and the due date must count **whenever it
is declared, required or not** (its picker is behind the toggle either way, and R40 means every list
declares it). Both failures are silent. And `compact` is a **render decision about one control**, not a
second control: the emit path, `instantOf` and the 23:59 default are shared, so a date set from the
collapsed chips is the same household-zone instant as one set from the picker (`adr/0036`). The
recurrence form's assignee is deliberately left a dropdown - a standing instruction is written once and
read for months.

**R153-R158 are implemented.** `docs/requirements/0018` and `docs/design/0018` add **Duplicate**: an
item row offers it beside Edit, and the agenda's item popup offers it too, opening the add form prefilled
with the original's **description and detail and nothing else**. Frontend only - a duplicate is an
ordinary create, and `SaveItem` already carried every component it needs. What it refuses to copy is the
point: a due date, an assignee or a cost carried over produces a chore already dated for a day that has
gone and assigned to whoever did the last one (R155). It is offered on a **finished** item too, which is
the one most often wanted again.

**Four things in there are easy to break.** `items/duplicate.ts`'s `duplicateOf` is the **one**
definition of what a copy carries, called by both surfaces over an `Item | DashboardItem` union - a
second copy is how the list and the agenda come to disagree, and neither would fail. The duplicate form
opens **expanded**, which is the opposite of adding (0017): what is left to answer after a copy is
exactly the date, assignee and costs R155 dropped, so a collapsed form hides the whole remaining task.
`dashboard/DuplicateItemDialog` must keep its **three** load states - a form drawn from `fields ?? []`
asks for a description alone while the configuration is in flight and is indistinguishable from a failed
request, which is `NavGroup`'s 0012 bug exactly. And **`useCreateItemFromAgenda` is a third writer that
must name both key trees** (`['dashboard']` *and* `['lists']`, `adr/0039`): reusing `items/queries`'
`useCreateItem` there compiles, passes, and leaves the list one item short with nothing to refetch it.

The agenda now creates items, widening `adr/0039`'s "writable in exactly one way" a second time, bounded
by R157: the only create reachable from there is one whose list is already decided - so there is no list
picker, and the form is the **list's** form, fetched through `useList`/`useListFields` under the list's
own cache keys. Duplicate is deliberately **not** on `AgendaItemRow`: that row has two write controls
already and a third icon is a mis-tap, while the popup is one tap from every view.

**R159-R172 are implemented.** `docs/requirements/0019` and `docs/design/0019` add **Trackers**, a
**third kind** beside lists and collections: a number a household records over and over - what somebody
weighs, what the groceries cost, how long a child read. `docs/adr/0051`-`0053` record the decisions. A
tracker declares a **unit** (a label, never a conversion), a **period** (day/week/month), a **scope**
(`PER_MEMBER`, a series each overlaid on one chart, or `SHARED`, one series the household writes) and a
**rollup** (SUM/AVERAGE/LATEST). A reading is one number in one bucket with a plain-text note.

**It is its own aggregate** (`adr/0051`, applying `adr/0029` a third time): `tracker` / `tracker_access`
/ `tracker_reading` / `tracker_user_state` / `tracker_interaction_day`, package `com.hatodo.tracker`,
V920-V922. Not a `kind` on `todo_list` (a cost hangs on an item that carries an assignee, a due date and
a completion, and `TodoListService.create` writes field rows) and not a `kind` on `data_list` (whose
generated `search_vector` and tags exist to find text a tracker does not have). **"Everything the member
can see" now has three answers**, and anything new that reads across a member's things has to say which.
**A fourth kind should re-examine the trade rather than copy it a fourth time** - the same is true of
`web/src/app/repin.ts`, which now names three arrays by hand.

**Six things in there are easy to break.** **`member_id` is nullable and NULL *is* "the household's"**
(`adr/0052`) - no companion boolean, for the reason `password_hash` has none; `recorded_by` is a separate
column because authorship and series membership are different questions, which is also what makes R172
work (member deleted → their own series cascades, shared numbers stay unattributed). **Two invariants are
service-enforced because SQL cannot state them**: `member_id IS NULL` iff the tracker is SHARED, and
`period_start` is always the canonical bucket start - both fail **silently**, so both are pinned by
integration tests that write through the API and read the column back. **There is deliberately no unique
index on `(tracker_id, member_id, period_start)`**: several readings per bucket is R163, and the rollup
is what resolves them - adding that index is a product change, not a tidy-up. **`TrackerPeriod.startOf`
is the one implementation of where a bucket starts** (weeks on Monday, matching `calendar.ts`), and
**nothing in this feature ever puts an instant on the wire** - `period_start` is a `DATE`, a `LocalDate`,
then a `YYYY-MM-DD` string, so the `new Date(localString)` device-zone trap cannot occur. **One `Rollup`
folds both within a bucket and across buckets** when a long range is drawn coarsely - two implementations
is how the chart and the headline come to disagree. And `TrackerReading.isWritableBy` is the **one**
predicate behind R165 (per-member: your own series; shared: anyone with access), published as `editable`
and never recomputed on the client.

**The chart is `@mui/x-charts`, pinned at `9.17.0`** (`adr/0053`) - aged, MIT, peers accept
`@mui/material@9.5.0` and React 19, and it draws through our own theme, which `adr/0014` is what decides.
**`web/src/trackers/TrackerChart.tsx` is the only module that may import it**, by deep path, reached only
through `React.lazy`: it is the heaviest dependency in the app (~99KB gzipped in its own chunk) and a
second import site drags d3 into the main chunk where **nothing fails**. `pnpm build` showing a separate
`TrackerChart-*.js` is the check. **Tests must never assert on the chart's SVG** - x-charts rewrites its
markup between minors, and its y-axis ticks will also happily match a `getByText` for the headline
figure. Assert on `trackers/series.ts` and the chart's accessible label.

**Three existing pieces had to change and all three fail silently**: `SideNav`'s `pinnedEntries` must
concatenate the tracker pins, `PinnedSection`'s `ready` must be a disjunction over **all three**
sidebar queries, and `app/repin.ts` must name `trackers` in both its envelope guard and its body.
`useThemePreference` invalidates the third sidebar key too, and sign-out clears the third touched set.
`lists/AudiencePicker` was **extracted** from `ListForm` so all three kinds ask "who can see it" once.

**A tracker reaches a planning surface in exactly one place** (R171): a quiet strip on the agenda naming
**daily** trackers with nothing recorded **today**, fed by `GET /api/trackers/pending` - under
`/api/trackers`, not `/api/dashboard/*`, so no dashboard code learns trackers exist. It is deliberately
the one surface that does **not** distinguish its three load states: a nudge is not somebody's data, and
"could not check whether you weighed yourself" is noise on the landing page. A tracker write must **not**
invalidate `['lists']` or `['dashboard']` - `useCreateItemFromAgenda` trains the opposite reflex, and a
reading is not work.

Keep this file true: when a decision here turns out wrong, update it in the same change.

## What this product is

`ha-todo` is a shared household todo list. **The deployment is the household** — there is one, every user
belongs to it, and there is no household entity (`docs/adr/0010`). A household has many **lists**
(grouping is a core feature, not a label); each list is seen by the people its creator grants access to, so
a shared grocery list and a private workout log are both ordinary. Members add **items** to a list, items
can be **assigned** to a member, and any member with access can mark any of that list's items complete.
Design every API and UI around "multiple people touching the same list concurrently", not around a
single-user todo app.

A household also has a **day to fit the work into**, and that lives in Google Calendar or Outlook. A
member subscribes those calendars by their published iCalendar address and the events appear on the
agenda beside the chores; nothing here authors an event (`docs/adr/0034`). The join with the todo domain is the
whole value - the providers can already share a calendar between two people, and can never show it next
to what the household owes today.

A household also keeps things it **looks up** rather than finishes, so there is a second kind of list:
a **data list** (a *collection* in the UI) holds recipes, restaurants, quotes, vocabulary. Same grant
model, entirely separate tables and package, and nothing that treats an entry as work — see
`docs/adr/0029`. A household also keeps **numbers**: what somebody weighs, what the groceries cost, how long a child
read. Those are neither work nor reference - they are a value against a bucket, and the point of them is
the shape they make over months - so there is a third kind, a **tracker** (`docs/adr/0051`). Same grant
model again, entirely separate tables and package, and nothing that treats a reading as work.

When adding anything that reads across "all the user's lists", decide deliberately
which of the **three** kinds it means.

## Monorepo layout

```
app/           Spring Boot 4.1.2 backend (Gradle, Groovy DSL, Java 21)
web/           React 19 + TypeScript frontend (Vite, pnpm, oxlint)
e2e/           Standalone pytest + Playwright end-to-end suite — runs against dev, not local mocks
deployment/    docker-compose files + env templates per environment
scripts/       Shell entrypoints for build/deploy; the only supported deploy path
docs/          Specs, designs, ADRs (see Spec-driven workflow)
```

`app/` and `web/` own their own unit and integration tests. `e2e/` is deliberately a separate project with
its own dependency set and no imports from the other two — it talks to a deployed system over HTTP only.

Each module is self-contained: `app/` has its own Gradle wrapper, `web/` its own lockfile. There is no root
pnpm workspace — one JS package does not need one, and it would move the lockfile out of the image build
context.

## Commands

Backend (`app/`):
```bash
./gradlew build                       # compile + unit + integration tests
./gradlew test                        # unit tests only
./gradlew integrationTest             # integration tests (Testcontainers-backed)
./gradlew test --tests '*AppUserDetailsServiceTest.mapsStoredUserOntoUserDetails'  # single test
./gradlew bootRun --args='--spring.profiles.active=local'   # needs infra up (see Local stack)
./gradlew check                       # test + integrationTest
```

Gradle **auto-provisions JDK 21** via the foojay resolver in `settings.gradle`; no system JDK 21 needed.
Integration tests need a running Docker daemon (Testcontainers).

Frontend (`web/`):
```bash
pnpm install
pnpm dev                              # Vite dev server
pnpm test                             # unit tests (watch off in CI)
pnpm test path/to/File.test.tsx       # single test file
pnpm lint && pnpm typecheck           # oxlint, then tsc -b --noEmit
pnpm build
pnpm icons                            # redraw public/icons/*.png, then commit them
```

**Pin new dependencies to an exact, aged version.** A `minimumReleaseAge` would make a just-published
dependency fail `--frozen-lockfile` *inside the Docker build* while succeeding on the host — the worst
place to find out. It is **not** configured today (`pnpm config get minimumReleaseAge` is `undefined` on
pnpm 10.30.3), so the hazard is latent rather than active; pinning exactly is cheap insurance either way,
and a caret range on a shared lockfile invites drift onto a fresh release. Verify any dependency change
with a real `docker build web/` before opening the PR.

E2E (`e2e/`) — pytest driving a real browser via Playwright:
```bash
uv sync
uv run playwright install chromium    # once, after install
E2E_BASE_URL=http://localhost:5173 uv run pytest      # against the local stack
E2E_BASE_URL=https://<dev-host> uv run pytest         # against dev (the intended target)
uv run pytest tests/test_smoke.py::test_passwordless_member_signs_in_with_one_click
uv run pytest -m smoke
uv run pytest --headed --slowmo 300   # watch it run while debugging
```

Local stack:
```bash
./scripts/dev-up.sh                   # infra, then app + web, waiting for health
./scripts/dev-down.sh                 # add --volumes to discard the local database
```
web on http://localhost:5173, API on http://localhost:8080. Sign-in is a list of people: pick **Ana**
(password `password123`) or **Ben** (no password at all), seeded from `db/seed` in the local and dev
profiles only. The admin is `admin` / `admin` locally, from `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

## Architecture notes

**Backend** is a layered Spring Boot service: REST controllers → application services (transaction
boundary, authorization) → domain → Spring Data JPA repositories against Postgres. Schema changes go
through versioned migrations (Flyway); never rely on JPA auto-DDL outside tests.

**Seed data is a repeatable migration (`db/seed/R__*.sql`), not a versioned one.** They are numbered
(`R__seed_01_demo_users.sql`, `R__seed_02_demo_lists.sql`) because **Flyway orders repeatable
migrations by description**: unprefixed, the lists seed sorted first and, on a fresh database, found no
users to own the lists - inserting nothing and failing silently. Flyway runs repeatable
migrations after every versioned one and re-runs them when their content changes, so demo data can be
edited freely and can never sit in front of a schema change. The old versioned seed at `V900` is why the
schema series resumes at **V901** — a lower version would be out of order on any database that applied it,
and why local/dev set `flyway.ignore-migration-patterns: "*:missing"`. Prod never applied it and keeps
strict validation. The highest applied migration is **V922**; new ones continue from V923.

**Authorization is per-list access, not household-scoped.** Every read and write is checked against the
caller's grant on that list, enforced in the service layer — not only in the controller and not only in
the UI. A list id in a URL is never sufficient proof of access. Two predicates: `readable` (the caller
owns the list or holds a `list_access` row) and `managed` (the caller owns it). `managed` is deliberately
**not** `owner || isAdmin()` — the admin administers accounts, not list contents, and gains nothing on a
list they were not granted. No access answers **404**, because an ungranted list must not be discoverable;
access without ownership answers **403**. Filter list queries in SQL by joining `list_access`, never by
fetching and filtering in Java. See `docs/adr/0010-per-list-access-instead-of-household-scoping.md`.

**Recurrence rules are the one thing on a list that is neither of those two predicates.** Since 0016
they are written at the `readable` level and rewritten by their author or the list's owner, which is a
third rule and lives in exactly one place: `RecurrenceRule.isWritableBy` (`adr/0049`). Do not reach for
`requireManaged` there.

**Item completion and assignment are the contended writes, and this is how they work.** Both `TodoList`
and `TodoItem` carry `@Version`, and every mutating request carries the version its view was drawn from,
checked *before* anything is mutated so the 409 is deterministic rather than dependent on flush timing.
An item conflict answers `{error, code: "STALE_ITEM", item}` — the item as it actually is, read in a
fresh transaction because the one that threw is being rolled back, and the prose names the other member.
The client takes the server's item outright and raises a snackbar; nothing is retried behind the
member's back. Two browser contexts cover it in `e2e/tests/test_items.py`.

Changing a list's children — its grants, its fields — calls `TodoListService.markChanged`, because
Hibernate will not dirty-check a parent whose own columns are untouched and a second editor would
otherwise never be told.

**The due date is declared on every list, and cannot be removed.** `DUE_AT` joins `DESCRIPTION` as a
kind every list has, with `required` defaulting to false and the owner free to toggle it — because a
dashboard that reads across lists (0005 R44) can only answer from the lists that declared the field, and
"the dashboard silently ignores this list" is not a behaviour anybody would choose. Enforced in
`ListFieldService.normalise`, not in SQL, for the reason `DESCRIPTION` is: SQL cannot say "every row here
has a child of this kind" without a trigger. See `docs/adr/0022`.

**A list's field configuration is typed rows, replaced by merging on id.** `list_field` (+
`list_field_choice`) rather than JSONB: the kinds are closed, and item values will reference a field and
a choice by foreign key. The `PUT` sends existing ids back and the service matches on them - recreating
the rows would orphan every item's costs, silently. Singleton kinds match by *kind* instead, because
there can only be one. Position uniqueness is `DEFERRABLE INITIALLY DEFERRED` so a reorder can rewrite
every position in one statement; partial unique indexes cannot be deferred, which is why those are
table constraints. See `docs/adr/0011` and `docs/adr/0012`.

**An item's cost values merge the same way, and for a harder reason.** `item_cost_value`'s identity is
`(item, field)`, so an edit matches each answer to the row already holding it and rewrites it in place.
Clearing the collection and adding a freshly built row for a field the item already answered is two
objects for one identifier, which Hibernate refuses with `NonUniqueObjectException` - a 500 on every
edit of an item carrying a cost. Putting the *same* instances back after a `clear()` is fine, which is
why the choices code reads as it does; building new ones is not.

**Boxed types in request records.** Jackson rejects a *missing* primitive in a record, so optional
`boolean`/`int` components are `Boolean`/`Integer` with a defaulting accessor (`isRequired()`,
`pointsOrZero()`). In a request that replaces a configuration, "not mentioned" is genuinely different
from "false" anyway.

**A sign-in lasts thirty days of use** (`docs/requirements/0009`, `docs/design/0009`,
`docs/adr/0038`). `server.servlet.session.timeout` and the
cookie's `max-age` are both `30d` (`SESSION_TIMEOUT` / `SESSION_COOKIE_MAX_AGE`), and
`RollingSessionCookieFilter` re-issues the cookie once a day so the thirty days run from **last use**
rather than from sign-in — Tomcat writes the session cookie once at creation and never again, so a plain
`max-age` would log out a daily user on day thirty. Three separate things end a session and they are
easy to confuse: the server's inactivity timer, the cookie's lifetime, and **the app restarting** —
sessions are in memory (`adr/0003`), so every deploy still signs everybody out. That is now the likeliest
reason somebody is asked to pick their name again; Spring Session JDBC against the existing Postgres is
the fix and is deliberately not done yet.

**`e2e/tests/test_sessions.py` has a trap worth knowing.** Playwright's `storage_state()` serialises
**session cookies too**, so saving it and restoring it into a fresh context puts them straight back — a
"reopen the app" test written the obvious way passes against a build with no cookie lifetime at all and
therefore cannot fail. `reopened_cold` drops cookies with no expiry, which is what a browser does when
its browsing session ends. Verified by running the file against a deployment of the previous build,
where two of its three tests fail.

**Authentication is session-based.** `POST /api/auth/login` takes JSON, rotates the session id, and stores
the context in the HTTP session. Anonymous API calls get **401**, never a redirect. CSRF stays on (the
credential is a cookie): `GET /api/auth/csrf` issues the `XSRF-TOKEN` cookie and clients echo
`X-XSRF-TOKEN`. `ERROR` dispatches are permitted in the chain — without that, a 500 inside a controller is
forwarded to `/error`, blocked, and surfaces as a misleading 401. See
`docs/adr/0002-session-auth-postgres-users.md`.

**Identity is a `username`, and a password is optional.** There is no `email` column. `password_hash IS
NULL` *is* "no password required" — there is deliberately no companion boolean, because a boolean that
disagrees with the hash is an auth bypass. `LoginService` branches on it: a stored hash goes through
`DaoAuthenticationProvider`, a null one is signed in directly and any submitted password ignored. Keep that
bypass as one obvious `if`, not a custom `AuthenticationProvider`. See
`docs/adr/0005-pick-a-user-login-with-optional-passwords.md`.

**The sign-in roster is public, and says only who exists.** `GET /api/auth/users` is `permitAll` because the
login screen is a list of people. It returns `id`, `username`, `displayName` and **nothing else**: no role,
no `passwordRequired` — publishing which account is the admin and which have no secret is a list of free
passes sorted by name. Never add a field to it. The client therefore cannot know whether a password is
needed and finds out by posting the login without one and reading the **401**, so a deliberate 401 is part
of a successful sign-in and must not be read as an attack on its own. See
`docs/adr/0009-opaque-sign-in-roster.md`. The older consequence is unchanged: anyone who can reach the
deployment can see the household and sign in as any passwordless member
(`docs/requirements/0002-accounts-and-administration.md`); do not widen it, and keep both environments
behind the HTTPS front door.

**One admin, from the environment.** `role` is `ADMIN` | `MEMBER`, and a partial unique index makes a
second admin unrepresentable. `AdminAccountInitializer` reconciles the admin row from `ADMIN_USERNAME` /
`ADMIN_PASSWORD` on every startup and **fails startup** if no password is set; that is also the rotation
path. `/api/admin/**` is `hasRole("ADMIN")`, and the service refuses to delete the admin row regardless of
caller. See `docs/adr/0006-admin-account-from-environment.md`.

**The app is an installable PWA whose service worker caches nothing.** `web/public/manifest.webmanifest`
(name "List", standalone, `start_url: /dashboard` since R44 made the dashboard the landing page — it
moves in three places at once: the manifest, `App.tsx`'s catch-all and `LoginPage`'s fallback), the iOS-only meta tags in `index.html`, and
`public/sw.js`, which exists solely to satisfy the install criteria. Offline use is out of scope, and a
precaching worker would serve a stale `index.html` behind nginx's `no-store` — so do not add caching to
it without replacing `docs/adr/0007-installable-pwa-without-offline-caching.md`. Icons are drawn by
`web/scripts/generate-icons.mjs` (`pnpm icons`, no dependencies) and the PNGs are committed.

**Receiving a shared link is Android-only, and the link becomes an item.** The manifest declares a
**GET** share target at `/share?title=&text=&url=`; `SharePage` normalises it (`src/share/sharedLink.ts`
— Chrome often puts the URL in `text`, not `url`) and the member picks which list it goes on, with the
one they touched most recently already chosen. No "Inbox" list is invented: a link landing somewhere
nobody looks is the failure that would replace. Nothing is parked on the device any more; `parkedLinks`
is gone, as `adr/0008` said it would be. iOS Safari does not implement Web Share Target, so an
installed iPhone app cannot appear in the share sheet — a platform limit, not a gap to work around. See
`docs/adr/0008` and `docs/adr/0020`.

**The signed-in UI is one shell with one sidebar.** Every authenticated route renders inside
`AppShell`, so `SideNav` mounts once. The breakpoint is `48rem`, named as `sm` in the MUI theme
(`breakpoints.values.sm = 768`) so it is one number in one place. Above it the sidebar is a permanent
`Drawer`; below it a temporary one behind a hamburger.

`AppShell` owns the drawer's open state and makes the **only** `useMediaQuery` call. A drawer opens and
closes, which is behaviour rather than placement, so the old CSS-only rule could not express it — see
`docs/adr/0015`. The rule is narrowed twice, not dropped: `SideNav` also owns which of its two groups
are expanded (`adr/0042`), on the same reasoning — disclosure is not placement. Everything about *where*
something sits is still CSS, and nothing else calls `useMediaQuery`. The drawer's open state is
*derived* from the pathname (`openedOver === pathname`), so a navigation closes it by construction, back
button included.

**Every surface at the top of the shell must add `env(safe-area-inset-top)` back.** `index.html` sets
`viewport-fit=cover` and `black-translucent`, so iOS draws a standalone PWA *under* the status bar and
takes every tap that lands there. The app bar sits at `top: 0` and its only control is the hamburger, so
omitting the inset made it unpressable on a notched iPhone while working perfectly on Android, which
does not paint under its status bar - and the inset must be cleared in `main` as well as reserved on the
bar, or the taller bar covers every screen's heading. The original comment said "the bottom navigation
adds the safe-area inset back", which was true when the nav *was* a bottom bar and nothing sat at the
top; it survived the bottom bar's removal. `AppShell.test.tsx` reads the compiled emotion rule rather
than `getComputedStyle`, because jsdom resolves `env()` to 0 and so cannot tell the two apart.
The bottom bar is gone; a second navigation surface is how two layouts start to drift.

**Styling is the MUI theme, and nothing else.** `web/src/theme/theme.ts` is the single source of truth
for colour, radius and type; `index.css` is ~30 lines of box model, iOS text inflation, and the
safe-area and `dvh` units a theme cannot express. The old `:root` token block and every component class
were **deleted, not mapped** — two names for one colour is how the old one quietly survives. From here
on no component introduces a global class: use `sx`, a styled component, or a theme override. Icons are
imported one per path (`@mui/icons-material/ChecklistRounded`), never from the barrel, which is ~11,000
modules. See `docs/adr/0014`.

**Object storage is wired in; Redis is still deferred.** Profile pictures live in an S3 store
(`app.storage.*`, from `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET`), and the detail
field's images will. **SeaweedFS** provides that API — locally, in the integration tests, and on the
host — because MinIO's own images stopped allowing anonymous pulls; nothing in the code is specific to
either, and the client library is still minio-java used as a plain S3 client. See `docs/adr/0013` and
`docs/adr/0021`. Redis remains deferred on `docs/adr/0003`'s reasoning, and still needs a concrete need
and a new ADR; its likeliest trigger is a second backend instance, where in-memory sessions break.

**The recurrence scheduler assumes exactly one backend instance.** `docs/adr/0025`
puts a `@Scheduled` job behind `app.recurrence.enabled` with its cron in the environment, and takes no
lock and elects no leader: both compose files run one `app` service with one replica. A second instance
would double-run every rule, which `ux_todo_item_occurrence (rule_id, occurrence_on)` turns into logged
constraint violations rather than duplicate chores — so the failure is noisy and harmless. This is the
*same* assumption Redis's deferral rests on, and sessions break before recurrence does, so recurrence is
not what blocks a second instance. Two consequences to keep true: `@EnableScheduling` lives on the
feature's own `@Configuration` so disabling the feature disables the job, and **integration tests must
set `app.recurrence.enabled: false`** or a Testcontainers run can fire the job mid-assertion. A scheduled
run has no caller and must not manufacture one — `TodoItemService.createFromRule` is the one entry point
that does not consult `CurrentUserProvider`, because nobody is calling.

**The app creates its own bucket at startup and fails closed.** `S3_BUCKET` is named explicitly per
environment, never derived from the Spring profile, so a misconfigured profile cannot put prod's images
in dev's bucket. An unreachable store stops the app from starting, deliberately: a deployment that
passes its health check and silently cannot save a picture is a bug report nobody can diagnose.
`scripts/deploy.sh` refuses without the four variables, before building anything.

**Image bytes are streamed through `GET /api/images/{id}`, never a presigned URL.** The store is not
published through either HTTPS front door, so a presigned URL is unreachable from a phone and blocked
as mixed content — and it is a capability that outlives a revoked grant. The type is decided by
sniffing the bytes, never the filename or `Content-Type`; PNG, JPEG and WebP only, and **SVG is refused
by name**, because an SVG served same-origin is script execution in our own security context.

**Who may read an image depends on its owner.** An `AVATAR` is readable by anyone, including
anonymously, because the sign-in screen draws faces before there is a session (`adr/0019`). A `DETAIL`
picture is claimed by the **item**, the **recurrence rule**, or the **collection entry** whose detail
refers to it, so that owner's list or collection decides — a private list's pictures are as private as
the list. `stored_image` therefore carries three nullable owner FKs under `num_nonnulls(...) <= 1`;
`adr/0032` sets the threshold for replacing that shape with a join table at a **fourth** owner. An
**unclaimed** picture is readable only by whoever uploaded it, which is what makes the editor's own
preview work between pasting an image and saving — documented since the uploader was written, but not
actually implemented until R61. Only the member who uploaded a picture can claim it, and only while
nothing else owns it: an id alone must never drag somebody else's picture onto a list they can see.
Note that `ImageController` injects `ImageAccess` **by type**, so a second implementation of it is a
`NoUniqueBeanDefinitionException` at startup — a new owner kind needs its own one-method interface, as
`DataEntryImageAccess` does.

**Detail HTML is sanitized server-side on write** (`com.hatodo.richtext.HtmlSanitizer`, `adr/0018`),
and `<img src>` is restricted to our own `/api/images/{uuid}` paths — which is also what makes the
claim check meaningful. The client renders the stored HTML with `dangerouslySetInnerHTML` and
sanitizes nothing itself: a second implementation of that rule drifts from the first. The editor is
`React.lazy`-loaded, because ProseMirror is ~130KB gzipped and most lists never ask for the field.

**The frontend reaches the backend same-origin in every environment.** The SPA only ever uses relative
`/api` paths - never an absolute API URL, never `VITE_API_URL`. nginx in the `web` image proxies `/api` and
`/actuator/health` to `app:8080`, and the Vite dev server proxies the same paths, so cookies, `SameSite`
and CSRF behave identically everywhere and CORS is never needed. In dev and prod the `app` service
publishes **no** host port at all (`expose` only); locally it is bound to `127.0.0.1` purely so `pnpm dev`
can proxy to it. Do not add a published app port or an absolute API base URL - that reintroduces a
cross-origin path the session cookie is not configured for.

**Frontend** is a React SPA consuming the REST API; keep server state in TanStack Query so a list
refetches cleanly after another member's change, and keep the list-grouping view model separate from the
item components.

**`api/client.ts` is split per feature** — `api/http.ts` holds the axios instance and the error helpers
(`isUnauthorized`, `isConflict`, `errorMessage`); `auth.ts` / `admin.ts` / `profile.ts` hold their own DTOs
and calls. There is deliberately **no barrel re-exporting them**: tests spy by module namespace
(`import * as authApi; vi.spyOn(authApi, 'fetchCurrentUser')`), and a re-exported live binding is exactly
what `vi.spyOn` cannot intercept. Add a feature's module; never add an index.

**Only the caller's own routes take no id.** `/api/me/*` acts on the session's principal, so there is no
user id in the path to tamper with. Services reach the caller through `CurrentUserProvider`, never a
threaded username — see `docs/adr/0016`.

## Environments

Two environments only: **dev** and **prod**. Postgres (and Redis/MinIO if ever needed) is *provided
infrastructure* in both — the app points at it via environment configuration and never provisions it.
Flyway's `db/seed` location is active in **local and dev only**, so demo accounts can never reach prod. Local development is
the exception: `deployment/docker-compose.infra.yml` stands up throwaway equivalents.

Configuration is environment variables injected by the compose file for the target environment; no secrets
and no environment-specific hostnames in committed Spring profiles or frontend bundles.

## Deployment

Deployment is **scripted, from the local machine, over SSH**:

1. `scripts/build.sh` builds the `app` and `web` images locally.
2. `scripts/push.sh <env>` tags and pushes them to the registry.
3. `scripts/deploy.sh <env>` does both, copies the compose + env files over, runs `docker compose pull`
   and `up -d` on the target via SSH, then polls `/actuator/health` until it reports UP.

Rules:
- **Never open an interactive SSH session to a server to do deployment work by hand** — if a step is
  missing, add it to the script. All SSH goes through the single `ssh_run` helper in
  `scripts/lib/common.sh`. `deploy.sh prod` requires typing `deploy prod` to proceed.
- `ADMIN_PASSWORD` is required in every environment's env file. `scripts/deploy.sh` refuses without it,
  rather than letting the app fail its health check after the images are already built and pushed.
- Targets live in uncommitted `deployment/.env.dev` / `.env.prod`; `deployment/.env.example` documents
  every variable. Both environments run on **`wliant@192.168.1.248`**, sharing
  `/home/wliant/myapps/ha-todo` — the compose/env filenames and the project names (`ha-todo-dev`,
  `ha-todo-prod`) keep them apart. **dev is port 5987, prod is 5986.**
- The registry is `192.168.1.248:5000` over **insecure HTTP**; both this machine and the target already
  list it under `insecure-registries`.
- Each environment has its own Postgres role and database on `192.168.1.248:5432`: `hatodo_dev` and
  `hatodo_prod`. Flyway creates every table, so the role only needs to own its database.
- Both environments are fronted by HTTPS: dev at **https://todo-dev.wliant.org**, prod at
  **https://todo.wliant.org**, terminating TLS upstream and proxying to ports 5987 / 5986. So
  `SESSION_COOKIE_SECURE=true` in both, and the app sets `server.forward-headers-strategy: framework`
  while nginx forwards the browser's original scheme (`$http_x_forwarded_proto`, not `$scheme`, which is
  always plain http inside the container).
- A consequence: hitting a deployed environment **directly on its port over http:// cannot log in** — the
  browser drops the `Secure` cookie. Always use the https:// hostname. The TLS terminator must send
  `X-Forwarded-Proto`.
- Compose on the target is always invoked with `--env-file .env.<env>`. `env_file:` only populates the
  container; without `--env-file`, `${REGISTRY}` and `${WEB_PORT}` interpolate to empty and the pull fails.
- Rollback is `./scripts/deploy.sh <env> <older-tag>` — that is why `IMAGE_TAG` is an explicit argument.
- Deploys are outward-facing and hard to reverse. Confirm with the user before running one, especially prod.

## Testing strategy

- **Unit** and **integration** tests live inside `app/` and `web/`. Backend integration tests use real
  Postgres via Testcontainers rather than mocks of the data layer, and live in the separate
  `src/integrationTest` source set so `./gradlew test` stays fast and Docker-free.
- **E2E** runs from `e2e/` against the **dev** environment, written as `pytest` tests driving Playwright
  (`pytest-playwright`) through the real UI. Assert on user-visible state, and prefer role/label selectors
  over CSS so a restyle doesn't break the suite. Multi-member scenarios (one member assigns, another
  completes) need two browser contexts in one test — that is the suite's main reason to exist.
- E2E targeting the admin screens needs `E2E_ADMIN_PASSWORD`; those tests **skip** without it rather than
  guessing a credential that is environment configuration, not seed data.
- E2E needs explicit **test data management**: use the `test_data` fixture in `e2e/conftest.py`. It gives a
  run a unique prefix (`TestData.name()`) so parallel and repeated runs cannot collide, and
  `defer_cleanup()` registers teardown that runs at session end. Seed through the API with
  `authenticated_api`. Never assume pre-existing data in dev, and never leave residue behind.
  **The prefix namespaces runs, not labels** — `test_data` is session-scoped, so two modules both asking
  for `name("Private")` get the same string and the second create fails with "You already have a list with
  that name". Label for the module, not the concept.
- **`ui_sign_in` asserts being signed in, not being anywhere.** A fresh sign-in lands on the dashboard, but
  `ProtectedRoute` resumes wherever a member was sent away from, so waiting on one page's heading makes the
  fixture wrong for half its callers. Equally: "New list" lives only on `/lists`, so a helper that creates a
  list has to navigate there first rather than assuming sign-in landed on it.
- Two members acting at once is the suite's reason to exist: the `second_member_page` fixture gives a second
  browser context with its own session.

## Development workflow

- `main` is the default branch; every feature gets its own branch off `main`.
- **Verify locally before opening a PR.** `./scripts/dev-up.sh`, then exercise the actual change in a
  browser through the `playwright` MCP server (`mcp__playwright__*`, registered in `.mcp.json`) — click the
  real flow a family member would, not just the happy-path unit test. Unit and integration tests passing is
  not sufficient evidence that a UI change works. Then `./scripts/dev-down.sh`, and report what was actually
  exercised in the PR description.
  If the MCP server is unavailable (it needs one-time approval via `claude mcp list`), fall back to running
  `e2e/` against the local stack and say which you used — don't claim a browser check you didn't do.
- **Then deploy to dev and run `e2e/` against it — before opening the PR, and before handing the work
  back.** In order: `E2E_BASE_URL=http://localhost:5173 uv run pytest` against the local stack,
  `./scripts/dev-down.sh`, `./scripts/deploy.sh dev`, then
  `E2E_BASE_URL=https://todo-dev.wliant.org uv run pytest`. The suite exists to be run against a real
  deployment (see Testing strategy): the local stack shares a machine, a scheme and a hostname with the
  browser, so it cannot catch what the HTTPS front door, `X-Forwarded-Proto` and the `Secure` cookie do.
  Export `E2E_ADMIN_PASSWORD` from `deployment/.env.dev`'s `ADMIN_PASSWORD`, or the admin tests skip
  rather than run. Quote the two pass counts in the PR description. A deploy is outward-facing, so ask
  before the first one in a session; dev only — `deploy.sh prod` is never part of this loop.
- **Spec-driven**: before implementing a feature, write the requirement and design into `docs/`, and record
  non-obvious technical choices as an ADR (`docs/adr/NNNN-title.md` from `template.md`, immutable once
  accepted — supersede rather than edit). Implementation should be traceable back to a spec document.
  Start from `docs/requirements/0001-shared-household-todo.md`, then `0004` which refines it. 0001's
  open question — how a member joins a household — is **closed, not answered**: there is no household to
  join (`docs/adr/0010`), so nothing blocks the domain work any more.

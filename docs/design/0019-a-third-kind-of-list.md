# 0019 - A third kind of list

Design for [requirements/0019](../requirements/0019-numbers-a-household-keeps.md)
(R159-R172). A new aggregate, three migrations, one new frontend area, one new
dependency, and two lines of existing code that must change or the feature is
silently half-broken.

Decisions this implements rather than argues:
[adr/0051](../adr/0051-a-tracker-is-its-own-aggregate.md),
[adr/0052](../adr/0052-a-reading-is-a-number-in-a-declared-bucket.md),
[adr/0053](../adr/0053-a-charting-dependency-and-which.md).

## Scope

| Layer | What |
|---|---|
| Schema | `V920` tracker + access, `V921` readings, `V922` pins + interaction buckets |
| Backend | `com.hatodo.tracker` - entities, repositories, three services, two controllers, two advices |
| Frontend | `web/src/api/trackers.ts`, `api/trackerReadings.ts`, `web/src/trackers/`, four pages, four routes |
| Existing code | `App.tsx`, `app/SideNav.tsx`, `app/repin.ts`, `theme/useThemePreference.ts`, `auth/useCurrentUser.ts`, `dashboard/DashboardPage.tsx` |
| Dependency | `@mui/x-charts@9.17.0`, one import site, lazily loaded |

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| The access pattern, twice written | `com.hatodo.list`, `com.hatodo.data` | adr/0051 copies it a third time deliberately |
| The household's zone | `HouseholdProperties.zone()`, `InteractionWindow.today()` | R166 needs no new date configuration |
| Civil-date maths in the browser | `dashboard/calendar.ts` (`now`, `civilIn`, `dayKey`, `startOfWeek`, `startOfMonth`) | The period control holds no `Date` of its own |
| A nav group that distinguishes three states | `app/SideNav.tsx`'s `NavGroup` + `statusOf` | R170's group is a third call, not a new component |
| Pin plumbing | `lists/PinButton`, `app/repin.ts` | Needs one array name added, not a rewrite |
| An icon set with no behaviour | `lists/listIcons`, `ListIcon` | Shared across all three kinds (adr/0029) |
| The chip-and-stepper date idiom | `items/DueAtField` (0012 R94) | `PeriodField` is the same gesture at three grains |

## Step 1 - the schema

Three migrations, so a failure is attributable. Full DDL is in the migration
files; what matters here is what the columns *mean*.

**`tracker`** carries the declaration: `unit` (a label, never a conversion),
`period`, `scope`, `rollup`, `decimals`, and the optional `target` /
`target_direction` pair under a check that makes them all-or-nothing - "70 kg"
is not a goal until you know which side of it is good.

**`tracker_reading`** carries `tracker_id`, a **nullable** `member_id` whose
NULL means the household's series (adr/0052), the canonical `period_start
DATE`, `recorded_at` (within-bucket order, what LATEST reads), `amount
NUMERIC(12,3)`, a plain-text `note`, and `recorded_by` for authorship.
`member_id` cascades on member deletion and `recorded_by` nulls - which is
exactly R172.

**`tracker_user_state`** and **`tracker_interaction_day`** are retargeted
copies of the collections pair (adr/0041, adr/0043).

Two product rules are written as **absences** and both are easy to "fix":

- no unique index on `(tracker_id, member_id, period_start)` - several readings
  per bucket is R163;
- no `amount >= 0` check - a tracker records a change as readily as a level.

Two more are service-enforced because SQL cannot state them (the adr/0022
precedent): `member_id IS NULL` iff the tracker is SHARED, and `period_start`
is always canonical. Both fail silently, so both are pinned by integration
tests that write through the API.

## Step 2 - the aggregate

`com.hatodo.tracker`, mirroring `com.hatodo.data` file for file minus
everything text-shaped. The access guard is the driving predicate of every
read - `EXISTS (SELECT 1 FROM TrackerAccess a WHERE a.tracker = t AND
a.user.id = :userId)` - never a Java filter.

`TrackerPeriod` holds the only genuinely new logic: `startOf(LocalDate)` and
`next(start)`, with WEEK on **Monday** to match the browser's `startOfWeek`.

`TrackerService` carries `readable` (absent → 404) and `managed` (owner only,
not `|| isAdmin()`). Two rules of its own:

- `create` writes the owner's grant row **and nothing else** - the line
  `DataListServiceTest` asserts, repeated here as the guard against a
  discriminator creeping back in.
- **`period` and `scope` are refused once a reading exists** → 409
  `TRACKER_IN_USE` (R161). The settings screen renders them read-only with the
  reason rather than offering a control that 409s.

`TrackerReadingService`: writing needs **`readable` only** - recording a number
is the ordinary act a grant has always meant, exactly as adding an item is. The
series is **derived from the scope and the caller**, never read from the request
body, so there is no `memberId` parameter to tamper with. Correcting goes
through one predicate written once, on the entity:

```java
// TrackerReading
public boolean isWritableBy(UUID userId, Tracker tracker) {
    return tracker.getScope() == TrackerScope.SHARED || userId.equals(memberId);
}
```

published to the client as `editable` so the client renders the flag and never
recomputes it (adr/0049's rule). Readability is checked **first**: a reading on
a tracker the caller holds no grant on stays a 404, one they can see but may
not touch is a 403.

## Step 3 - the series

`GET /api/trackers/{id}/series?from=&to=&by=` is what the chart reads:

```json
{ "trackerId": "…", "unit": "kg", "period": "DAY", "rollup": "LATEST",
  "scope": "PER_MEMBER", "decimals": 1, "target": 70, "targetDirection": "AT_MOST",
  "by": "MONTH", "buckets": ["2026-09-01", "2026-10-01", "2026-11-01"],
  "series": [ { "memberId": "…", "displayName": "Ana",
                "points": [ {"periodStart": "2026-09-01", "value": 72.4},
                            {"periodStart": "2026-10-01", "value": null} ] } ] }
```

- `buckets` is a **server-computed dense axis**, so the client never iterates
  dates looking for holes.
- A bucket nobody recorded is `null`, **never 0** (R167). A missed weigh-in is
  not 0 kg, and a chart that draws it as one invents a crash.
- `by` defaults to the tracker's period and may only be **coarser**; finer is a
  400, because folding cannot invent days.
- The fold is in Java, capped at 2000 readings, one `Rollup` enum used both
  within a bucket and across buckets (adr/0052).

`GET /api/trackers/pending` backs R171: DAY-period trackers the caller can read
with nothing recorded for today in the household zone. It lives under
`/api/trackers` rather than `/api/dashboard/*` because adr/0029 says no
dashboard code learns trackers exist - the agenda composes the strip from a
tracker request of its own.

## Step 4 - the frontend

`web/src/trackers/` with `queries.ts` holding one key hierarchy
(`['trackers']`, `search`, `detail`, `sidebar`, `readings`, `series`,
`pending`). A reading write invalidates `trackerKeys.all` and **must not touch
`['lists']` or `['dashboard']`** - worth a comment, because
`useCreateItemFromAgenda` trains exactly the opposite reflex. A tracker is not
work and never reaches the agenda except through its own `pending` key.

`PeriodField` asks for exactly the declared bucket - chips and ±1 day for DAY,
a week stepper reading "17-23 Nov" for WEEK, a month stepper for MONTH - and
emits a canonical `YYYY-MM-DD`. It holds no `Date`, and `now(zone)` is called
**inside** a `useMemo`, never during render (the `items/filters.ts` purity
rule).

The range lives in the address as `?range=30d|3m|1y|all`, written with
`replace`, omitted at the default - the rule `?on=` and a list's filter follow.

The chart is `TrackerChart.tsx`, the only module permitted to import
`@mui/x-charts`, reached through `React.lazy` behind a `Skeleton` of its exact
height (adr/0053). `BarChart` when the rollup is SUM, `LineChart` otherwise.

## What this breaks, and how it fails

Three existing pieces must change, and **all three fail silently**:

1. **`SideNav`'s `pinnedEntries`** must concatenate the tracker pins. Miss it
   and a pinned tracker simply never appears.
2. **`PinnedSection`'s `ready={listsQuery.isSuccess || collectionsQuery.isSuccess}`**
   must gain a third disjunct. Miss it and a member whose only successful
   request was trackers sees an empty Pinned section - the same conflation
   `NavGroup` shipped with in 0012.
3. **`app/repin.ts`** must name `trackers` in both its envelope guard and its
   body. Miss it and the star does not flip until the refetch lands.

And one rule with no enforcement at all: **`@mui/x-charts` is imported in
exactly one file**. A second import drags d3 into the main chunk, costs every
member who never opens a tracker, and fails nothing. `pnpm build` showing the
chart as its own chunk is the check.

## Tests

- **Unit** - `TrackerPeriodTest` (a **Sunday** maps to the preceding Monday;
  the 30th of a month maps to the 1st; year and 31→30 boundaries).
  `TrackerServiceTest` (404 without a grant, 403 for a granted non-owner,
  `managed` is not `|| isAdmin()`, create writes only the owner's grant,
  period/scope refused once a reading exists). `TrackerReadingServiceTest` (the
  series is the caller's even when the body names somebody else; a SHARED
  reading has a null member and a set `recordedBy`; another member's
  PER_MEMBER reading reads but does not write). `TrackerSeriesServiceTest`
  (each rollup within and across buckets; a finer `by` rejected; a gap is null).
- **Integration** - the grant guard on every endpoint; the two
  service-enforced invariants written through the API; and
  **`TrackerTimezoneIntegrationTest`**, with `app.timezone: Asia/Singapore` on
  a UTC container: 23:00 SGT buckets on the Singapore day, a MONTH reading
  posted for the 30th stores the 1st, `pending` asks about Singapore's today.
- **Frontend** - the sidebar group's three states (against a promise that never
  settles); `repin` over a tracker envelope; `period.ts` with a non-device zone
  injected; `series.ts`; the detail page's range-in-URL and headline. **Never
  assert on the chart's SVG** - x-charts changes its markup between minors.
- **E2E** - `test_trackers.py`: record, share, overlay two members, refuse the
  other member's row, pin, and the agenda nudge appearing and clearing. Test
  data must not be named anything containing "Tracker", or
  `get_by_role("link", name="Trackers")` matches twice under strict mode.

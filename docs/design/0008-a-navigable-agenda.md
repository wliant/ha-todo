# 0008 - A navigable agenda

Design for [requirements/0008](../requirements/0008-a-navigable-agenda.md)
(R73-R77). Mostly frontend; one field is added to a response.

Decision this implements rather than argues:
[adr/0037](../adr/0037-the-period-on-screen-lives-in-the-address.md) - the
period on screen is a search param, and nothing becomes stateful.

## Scope

Small enough to ship in one piece, but it lands in two halves that are worth
reviewing separately.

- **A - the period moves.** The anchor param, the controls, the labels, the
  three views deriving their range from the anchor instead of from the clock.
  End of A, R73-R76 are done and a member can walk the calendar.
- **B - the product admits what it has not read.** `ScheduledResponse` reports
  the window events are kept for; the agenda says so when the member steps past it.
  R77. Separable, and the feature is dishonest without it rather than
  incomplete - an empty February is indistinguishable from a quiet one.

## The rename (R78)

One string in `SideNav`, one `<h1>` in `DashboardPage`, and the selectors that
depend on them - the frontend tests and five e2e modules, which match the
heading and the sidebar link by accessible name.

**The route stays `/dashboard`.** It never said "my day", the PWA manifest's
`start_url` points at it (`adr/0007`), and changing it would break every link
anybody has saved for the sake of a word.

## Backend

One field, no schema, no new endpoint.

`CalendarWindow` already computes the bounds the poller fills
(`today - app.calendar.window-days-back`, `today + window-days-forward`) and is
already a bean. `DashboardService` reports them:

```java
public record ScheduledResponse(Instant from, Instant to,
                                List<DashboardItemResponse> items,
                                List<CalendarEventResponse> events,
                                List<CalendarFeedWarningResponse> staleFeeds,
                                EventWindowResponse eventWindow) {}

/** The days events are kept for; beyond it there is nothing to show because
    nothing has been read (R77). Household dates, as a day is. */
public record EventWindowResponse(LocalDate from, LocalDate to) {}
```

Reported rather than hardcoded in the client, for the reason every other bound
in this product is: it is configuration (`CALENDAR_WINDOW_BACK` /
`_FORWARD`), and a client that duplicated the numbers would be wrong in exactly
the deployment that changed them.

**Deliberately not** also reporting a bound for *items*: there is none. Items
are returned for whatever window is asked for, so the asymmetry R77 describes
is real, and the notice is about events only.

## Frontend

### The anchor

`DashboardPage` gains one derived value and no state:

```ts
// The day being looked at, today unless the address says otherwise. Parsed
// defensively: ?on= is in a URL anybody can edit or truncate.
const anchor = useMemo(() => parseAnchor(params.get('on'), zone), [params, zone])
```

`parseAnchor` accepts `YYYY-MM-DD` and falls back to `now(zone)` for anything
else - absent, malformed, `2026-13-45`. It returns a `Civil`, the same
household-zone date type `calendar.ts` already deals in, so every existing
helper applies unchanged.

`ViewContext` carries `anchor` beside `scope` and `zone`. The three views stop
calling `now(zone)` for their range and derive it from the anchor:

```ts
const range = useMemo(() => weekRange(anchor), [anchor])
const window = useMemo(() => windowOf(range, zone), [range, zone])
```

They keep calling `now(zone)` for **today's highlight** - the bordered column in
the week view, the bordered cell in the month grid - because that is a question
about the present, not about the period on screen. This is 0008's first
consequence and the one mistake this change makes available: *layout reads the
anchor, "now" reads the clock*.

### The controls

One row above the tabs, in `DashboardPage`, because it is the same control for
all three ranges and a copy per view would drift:

```
[‹]  Friday, 6 November            [Today]   [Everything | Mine]
```

- **Previous / next** are icon buttons whose accessible names say what they do
  *in the current range* - "Previous day", "Next week", "Next month" - rather
  than a bare chevron. They step the anchor by one day, seven days or one month
  (`addDays`, `addMonths`, which `calendar.ts` already has and which already
  anchors to the 1st so stepping from the 31st cannot skid).
- **The return control** is a text button labelled *Today*, *This week* or
  *This month* for the range (R75), and is `disabled` when the anchor already
  falls in the current period - compared by `dayKey`, `startOfWeek` or
  `isSameMonth` against `now(zone)`.
- **The period's name** sits between them and is the page's live region: it is
  the only thing that says which week is on screen, so it is `aria-live="polite"`
  and the headings inside the views say the same thing in their own words.

Stepping rewrites the param through the existing `setParams`, so the back
button walks the periods for free. Today's anchor deletes the param rather than
writing it, as `scope=all` does.

### The labels

`Today | Week | Month` becomes `Day | Week | Month` (R73). The tab routes are
unchanged (`/dashboard/day` already); only the labels move, and the e2e and
unit tests that select the tab by name move with them.

The day view's heading becomes the date it is showing - "Today" when it is
today, otherwise "Friday, 13 November" - because a heading that says "Today"
over another day is the same lie the tab label was.

### The out-of-window notice (R77)

A sibling of `StaleFeedsNotice`, in the same slot above the tabs, and
deliberately *not* merged with it: one says a calendar broke, the other says
nobody looked that far. It appears when the visible range extends beyond
`eventWindow`, and says which direction and how far the product does know:

> Events are only kept from 22 September to 4 February. This week is outside
> that, so nothing is shown here.

Partial overlap says so too ("from 4 February onwards"), because a week that is
half-covered is the case a member will hit first when stepping forward.

### Query keys

Unchanged in shape: `['dashboard', 'scheduled', from, to, scope]`, where `from`
and `to` already came from the range. They now vary with the anchor, so each
period is its own cache entry and walking back and forth re-renders without
refetching - which is what makes stepping feel instant and is why no
prefetching is needed.

`UndatedPanel` is untouched: it is not a period and has no neighbours (R45).

## Testing

### The rename (R78)

One string in `SideNav`, one `<h1>` in `DashboardPage`, and the selectors that
depend on them - the frontend tests and five e2e modules, which match the
heading and the sidebar link by accessible name.

**The route stays `/dashboard`.** It never said "my day", the PWA manifest's
`start_url` points at it (`adr/0007`), and changing it would break every link
anybody has saved for the sake of a word.

## Backend

- `DashboardApiIntegrationTest` asserts `eventWindow` is present and matches the
  configured days, so a client can rely on it.

### Frontend - `web/src/dashboard/`

- `calendar.test.ts`: stepping a month from the 31st, and `addMonths` across a
  year boundary. Both already exist for the helpers; the new cases are that
  `monthRange(anchor)` and `weekRange(anchor)` answer about the anchor rather
  than about today.
- `DashboardPage.test.tsx`:
  - the tabs read **Day**, Week, Month;
  - next steps one day / one week / one month, and the address carries it;
  - the range survives a range change - a day in another month, then the Month
    tab, lands on that month;
  - the return control is disabled on the current period and enabled off it,
    and lands back on today;
  - `?on=` rubbish falls back to today rather than rendering nothing;
  - today's highlight follows the clock, not the anchor - the regression for
    0008's first consequence;
  - the out-of-window notice appears past `eventWindow` and not inside it.

### E2E - `e2e/tests/test_dashboard.py`

Extended rather than replaced: stepping forward and back returns to the same
day, the heading changes with it, the address carries `on=`, and the return
control comes back to today. Asserted through the real controls by their
accessible names, as the rest of the suite is.

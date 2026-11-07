# 0011 - Overdue work on the agenda

Design for [requirements/0011](../requirements/0011-overdue-work-on-the-agenda.md)
(R88-R93). One migration-free backend read plus one frontend surface.

Decision this implements rather than argues:
[adr/0040](../adr/0040-overdue-is-a-day-boundary-read-from-the-server-clock.md).

## Scope

Two steps, in this order, because the second needs the first's payload:

1. **The read.** A third cross-list query and the endpoint that serves it.
2. **The surface.** A collapsible strip above the period, carrying the agenda's
   existing completion gesture.

No migration. No change to `/dashboard/scheduled`, `/dashboard/undated`, or to
`DashboardItemResponse` - the overdue payload is the same item shape, because
the popup and the row already know how to draw it.

## What is already there

| Piece | Where | Why it is enough |
| --- | --- | --- |
| The item shape | `DashboardItemResponse` | Carries the list, the assignee, the due date and the `version` |
| The access guard | `EXISTS (ListAccess ...)` in `TodoItemRepository` | The one in SQL, copied verbatim from the other two cross-list reads |
| The cap-plus-total shape | `findUndatedFor` / `countUndatedFor` | R92 is the same problem the undated panel already solved |
| The household's day | `HouseholdProperties.zone()` | The server already buckets events and items with it (`adr/0036`) |
| The write | `useCompleteFromAgenda` | R90's completion is 0010's mutation, pointed at a third payload |
| The gesture | `items/CompleteControl` | Press completes, hold offers a note (`adr/0027`) |

## Step 1 - the read

### `TodoItemRepository.findOverdueFor` / `countOverdueFor`

```
WHERE i.done = FALSE
  AND i.dueAt IS NOT NULL AND i.dueAt < :before
  AND (:mineOnly = FALSE OR i.assignee.id = :userId)
  AND EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId)
ORDER BY i.dueAt ASC, i.list.name ASC, i.position ASC
```

- `dueAt IS NOT NULL` is redundant against `< :before` and stated anyway: it is
  the sentence that says this is the complement of `findUndatedFor`, and the two
  queries have to partition the open work between them without overlapping.
- **Oldest first**, unlike the undated panel's by-list order. The cap means the
  page is the part a member sees, and the part worth seeing is the part that has
  been waiting longest.
- `:before` is a parameter of the query but **not of the endpoint** - see below.

### `DashboardService.overdue`

```java
UUID me = currentUser.requireUserId();
Instant before = LocalDate.now(household.zone()).atStartOfDay(household.zone()).toInstant();
```

The start of today in the household's zone. This is the whole of `adr/0040`'s day
boundary, and it is three lines in the one place that already owns the zone. The
client sends `scope` and nothing else, so there is no bound for an anchor to
leak into and R89 cannot be got wrong by a caller.

### `OverdueResponse(List<DashboardItemResponse> items, long total)`

Identical in shape to `UndatedResponse`, and deliberately a separate record
rather than a shared one: they are two answers to two questions, and a shared
record is where a field that only makes sense for one of them would land.

### `DashboardController` - `GET /overdue`

```java
@GetMapping("/overdue")
public OverdueResponse overdue(@RequestParam(required = false) String scope)
```

The class comment's "two endpoints rather than three or one" argument is about
the three *ranges* and still holds; it gains a sentence saying why a third
range-independent question is a third endpoint for the same reason the undated
panel is.

## Step 2 - the surface

### `dashboard/AgendaItemRow.tsx` - extracted, not duplicated

The Day view's `ItemRow` becomes a shared component taking `{ item, caption,
onOpen, onComplete, onWriteNote }`. `MyDayView` passes `listName · time`; the
overdue strip passes `listName · how late`. One row, so the
`ButtonBase`-with-a-sibling-control shape and its `"<description>, on <list>"`
label exist once - a second copy is where the invalid nesting `adr/0039`
describes grows back.

### `dashboard/OverduePanel.tsx`

- Renders **nothing** when the total is zero. The common case for a household
  that is keeping up, and a panel saying "nothing is late" is a line of furniture
  on every view forever.
- Collapsed by default: an `Alert`-weight strip reading **`3 chores are
  overdue`** with a disclosure. Expanded, it lists `AgendaItemRow`s and, when
  the server's `total` exceeds the page, the `and N more` line the undated panel
  already uses (R92).
- Named `OverduePanel` to match `UndatedPanel`, whose shape it copies - but
  rendered **above** the period, not in the right-hand column. `adr/0040` records
  why: at `xs` that column stacks below the outlet.
- `role="status"` with the count in the text, so the strip announces itself for
  the same reason `StaleFeedsNotice` does: a member who cannot see the grid still
  has to hear that something is behind them.

### `dashboard/period.ts` - `latenessLabel(due, today)`

`Yesterday`, `3 days ago`, and a plain date beyond a week. Takes `today` rather
than reading the clock, like every other function in these two modules: a date
computed during render is a query key that moves under its own refetch, and
`calendar.ts`'s header says so.

### `dashboard/DashboardPage.tsx`

`OverduePanel` joins `Notices`, above the tabs. It takes `scope` and nothing
else - no window, no anchor - which is R89 visible in the call site.

### `dashboard/queries.ts`

- `dashboardKeys.overdue(scope)` and `useOverdue(scope)`, beside `undated`.
- **`useCompleteFromAgenda.onMutate` learns the third payload.** It hand-writes
  each shape, so an overdue row completed from the strip would otherwise sit
  there until `onSettled`'s invalidation came back - a lingering row, no failing
  test, and the `['lists']` defect `adr/0039` records wearing a different hat.
  `Overdue` and `Undated` have the same shape, so the existing `total`-adjusting
  updater is factored out and applied to both.
- `OpenItem`'s fallback search gains the overdue payload, so a popup opened from
  the strip finds its item.

## Testing

### Backend unit - `DashboardServiceTest`

- The boundary is the start of today in the household's zone, not `now()`: an
  item due at 09:00 today is **not** overdue when the clock says 14:00. The test
  that pins R91, and the one that fails if somebody "fixes" the query to `<
  now()`.
- The zone is the household's: the same item is overdue or not depending on
  `app.timezone`, not on the JVM's default.
- `scope=mine` narrows; the access guard is exercised by the integration test
  because it is in SQL.

### Backend integration - `DashboardApiIntegrationTest`

- Another member's private list contributes nothing, with a real Postgres behind
  it. The guard is in the query, so this is the only place it can be checked.
- The page is capped and `total` reports the real count.
- An item with no due date appears in `undated` and **not** in `overdue`; an
  overdue one appears in `overdue` and not in `undated`. The partition R92's
  honesty rests on.

### Frontend unit - `DashboardPage.test.tsx`

- The strip is absent when nothing is late, present with a count when something
  is, and expands to the rows.
- The count does **not** change when the member navigates to another month -
  R89, asserted rather than trusted.
- Completing from the strip sends the item's version and removes the row.
- The lateness phrasing: yesterday, three days ago, and a date.

### E2E - `e2e/tests/test_dashboard.py`

- A member creates a chore due two days ago, opens the agenda, and is told about
  it on the Day view *and* on the Month view - the cross-range assertion no unit
  test makes against a real deployment.
- They complete it from the strip, and the strip goes with it.

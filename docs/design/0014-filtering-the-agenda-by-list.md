# 0014 - Filtering the agenda by list

Design for [requirements/0014](../requirements/0014-filtering-the-agenda-by-list.md)
(R123-R130). One feature, frontend-only - there is no server change at all.

Decisions this implements rather than argues:
[adr/0047](../adr/0047-the-agenda-filters-by-list-in-the-browser.md), which
inherits [adr/0044](../adr/0044-a-list-filters-in-the-browser.md)'s reasoning
and [adr/0045](../adr/0045-the-filter-lives-in-the-address.md)'s rule about
where a filter lives.

## Scope

Five steps, in this order, because the first is a pure move whose only check is
that somebody else's tests stay green:

1. **Lift `MenuChip`** out of `items/FilterBar.tsx` into `app/MenuChip.tsx`, so
   two filter surfaces draw one chip.
2. **`dashboard/listFilter.ts`** - the codec, the predicate and the label. Pure,
   and where most of the tests go.
3. **`dashboard/ListFilterChip.tsx`** and the `DashboardPage` wiring - the
   control, the address, and the one place that decides what the selection
   means.
4. **The five consumers** - three views, two panels - plus the empty states and
   the rule about counts.
5. **E2E**, against a deployment.

## What is already there

| Piece | Where | Why it is enough |
| --- | --- | --- |
| Which list a row came from | `DashboardItem.listId` / `listName` / `listIcon` | On every row of all three payloads already; nothing new on the wire |
| Every list the member can see | `GET /api/lists`, `useLists({})` | Uncapped, access-guarded in SQL, ordered by name - and **not** the sidebar's capped, partitioned set |
| Address-as-state | `useSearchParams`, `?on=` and `?scope=` beside it | R124 and R128 need no new mechanism |
| A chip that opens a menu | `items/FilterBar.tsx`'s `MenuChip` | Step 1 moves it; the behaviour is already right |
| Rows in hand, not on the wire | `DashboardService.LIMIT = 500` on each query | A household never reaches it, so filtering the payload filters the set |
| A per-view context | `dashboard/dashboardView.ts` | `scope` is already threaded this way; the selection goes beside it |

## Step 1 - one chip, two bars

### `web/src/app/MenuChip.tsx`

Moved verbatim. `app/` rather than a new `components/` directory, because that
is where this codebase already keeps a cross-feature component (`MemberAvatar`).
It exports the component and nothing else - a component file that also exports
helpers breaks fast refresh, which is why `sortOptions` and `sortLabel` live in
`items/filters.ts` rather than beside the bar that calls them.

`items/FilterBar.tsx` imports it and drops its now-unused `Chip`, `Menu`,
`useState` and `ReactNode` imports. Nothing else about it changes, and
`FilterBar.test.tsx` is the whole check on this step: it must pass unedited.

## Step 2 - the logic

### `web/src/dashboard/listFilter.ts`

Pure, no React, no clock:

```ts
export function parseLists(params: URLSearchParams): string[]
export function writeLists(params: URLSearchParams, ids: string[]): URLSearchParams
export function onlyLists(items: DashboardItem[], ids: string[]): DashboardItem[]
export function known(ids: string[], lists: ListSummary[] | undefined): string[]
export function listFilterLabel(ids: string[], lists: ListSummary[] | undefined): string
```

`writeLists` **clones the params it is handed** rather than returning a fresh
`URLSearchParams` the way `items/filters.ts#writeFilter` does. That difference
is load-bearing: the agenda's address also carries `on`, `scope` and `item`, and
a fresh one would silently drop the period somebody had navigated to. The
param is omitted entirely when the selection is empty (R124), so the default
agenda is still `/dashboard/day` with nothing after it.

`parseLists` splits on commas, trims, drops blanks, dedupes, and **does not
validate ids**. The member's lists have not loaded when the address is first
read, and rewriting the address while that query is in flight is a worse bug
than carrying an id for one render. `known` is what drops the ids no visible
list claims, and it returns `ids` unchanged while `lists` is `undefined`.

The R128 behaviour falls out of that rather than being coded for: dropping
every unknown id leaves an empty selection, and an empty selection is no
filter, so a link naming a deleted list shows the **whole** agenda.

## Step 3 - the control and the address

### `web/src/dashboard/ListFilterChip.tsx`

A `MenuChip` whose menu is "All lists" - selected when nothing is chosen, and
the one-tap way back (R124) - then a `Divider`, then one `MenuItem` per list
with a `Checkbox` and its `ListIcon`. The menu stays open on a tick, because
picking three lists is one gesture.

Options come from `useLists({})`. **Not `useSidebar()`**: its `lists` array is
capped at the member's `sidebar_list_count` and excludes the pinned section, so
it is not the set this filter has to offer.

The chip renders before the lists arrive and when nothing is picked (R124).

### `web/src/dashboard/DashboardPage.tsx`

```tsx
const { data: visibleLists } = useLists({})
const picked = useMemo(() => known(parseLists(params), visibleLists), [params, visibleLists])

function setLists(ids: string[]) {
  setParams(writeLists(params, ids), { replace: true })
}
```

Two things to keep true here.

`known` is applied **once, here**, and the views receive the result. If each
view narrowed for itself, a deleted-list id would filter every row out in the
views while the chip - which does narrow - showed no filter at all. One owner
of what the selection means.

`replace`, not `push`. Ticking four lists is one decision and must not be four
entries in the history (`adr/0045`). This is the page's only `replace` writer:
`moveTo`, `open`, `close` and `setScope` keep pushing, because moving the
period and opening an item are navigations.

The chip sits beside the scope toggle in an inner row - whose work, then which
lists - and `picked` goes onto `ViewContext` next to `scope`, with `lists={picked}`
passed to `OverduePanel` and `UndatedPanel` as `scope` already is.

`OpenItem` is deliberately **not** filtered. It looks `?item=` up in the cache
to draw a popup, and a popup open when the filter changed should keep
describing its chore rather than becoming a blank dialog.

## Step 4 - the five consumers

`MyDayView`, `WeekView` and `MonthView` all become one shape:

```ts
const grouped = byDayEntries(onlyLists(data?.items ?? [], lists), data?.events ?? [], zone)
```

**`events` is passed through untouched** and that asymmetry is R126. An event
belongs to a calendar feed and has no list; a view that "tidied" this into
filtering both arguments would take somebody's appointments off their agenda,
and nothing outside `DashboardPage.test.tsx` would fail.

`UndatedPanel` and `OverduePanel` filter their `items` and recount. The overdue
strip returns `null` when the filter leaves no late rows - a strip announcing
other lists' arrears is the one thing a narrowed agenda promised not to show.

### The counts

Both panels carry a server-side `total` beside a capped page. A client filter
cannot narrow it, so:

```ts
const rows = onlyLists(data?.items ?? [], lists)
const capped = data ? data.total > data.items.length : false
// headline: lists.length > 0 ? rows.length : data.total
// caption:  capped && (lists.length > 0 ? 'and possibly more' : `and ${…} more`)
```

Unfiltered behaviour is exactly as it was. Filtered, the panel counts what it
is showing and refuses to put a number on rows it never saw (`adr/0047`).

### The empty states

The Day view and the undated panel name the filter when one is active (R127) -
"Nothing from the lists you picked…" rather than "Nothing is due". The week and
month grids keep their empty cells: the filled chip and its count is the cue
there, and a message in forty-two cells is furniture.

## Testing

- **`dashboard/listFilter.test.ts`** - the default serialises to nothing and
  matches everything; round-trip; blanks and repeats dropped; `writeLists`
  keeps `on`, `scope` and `item`; `known` leaves the selection alone until the
  lists load, drops an unlisted id, and **empties rather than matching nothing**
  when every id is unknown.
- **`dashboard/ListFilterChip.test.tsx`** - options come from `useLists`;
  ticking adds rather than replaces; unticking removes; "All lists" clears; the
  chip is on screen before the lists arrive.
- **`dashboard/DashboardPage.test.tsx`**, a `describe` of its own - the other
  list leaves the Day view **and the event stays**; the address gains `lists=`
  while keeping `?on=`, without a history entry; the chip names one list and
  counts two; the undated panel and overdue strip narrow; a capped filtered
  panel says "and possibly more" and not a number; `?lists=id-deleted` renders
  the whole agenda; an emptied day says why; and ticking a list issues **no**
  further `fetchScheduled` (R130).
- **`items/FilterBar.test.tsx`** - unedited and green, which is the whole check
  on step 1.
- **`e2e/tests/test_dashboard.py`** - two seeded lists, each with a chore due
  today; filter to one; the other's row is gone and the filter survives a
  reload of the address.
- **Backend** - nothing. No Java file changes.

## What this deliberately does not do

- **No server parameter, no new endpoint, no query-key change.** `adr/0047`,
  and the pagination threshold at which that has to be revisited is inherited
  from `adr/0044`.
- **No filter over calendar feeds.** A feed is the other dimension and gets its
  own control if it ever gets one (`adr/0047`).
- **Nothing remembered between visits.** The address is the whole memory
  (`adr/0045`), and the landing page is the whole agenda.
- **No new state on the page.** `adr/0015` still holds: the only state
  `DashboardPage` owns is the open event and the note being written, both of
  which predate this.

# 0013 - Filtering and sorting a list

Design for [requirements/0013](../requirements/0013-filtering-and-sorting-a-list.md)
(R107-R122). One feature, frontend-only but for one deletion on the server.

Decisions this implements rather than argues:
[adr/0044](../adr/0044-a-list-filters-in-the-browser.md),
[adr/0045](../adr/0045-the-filter-lives-in-the-address.md),
[adr/0046](../adr/0046-status-is-a-filter-not-a-declared-field.md).

## Scope

Four steps, in this order, because the first one is the only one with tests to
move:

1. **Remove the server's `status` parameter**, and the client's status-keyed
   fetch with it. One items query per list.
2. **`items/filters.ts`** - the filter type, its URL codec, and the predicate
   and comparator that apply it. Pure, and where almost all the tests go.
3. **`items/FilterBar.tsx`** - the controls, built from the list's declared
   fields and members.
4. **`ItemsSection`** - read the address, apply the filter, and say what
   happened when a completion removes a row.

## What is already there

| Piece | Where | Why it is enough |
| --- | --- | --- |
| Every input the filter needs | `ItemResponse` | assignee, `dueAt`, `completed`, `costValues` - already on the wire |
| The list's declared fields | `useListFields`, handed to `ItemsSection` as `fields` | R120 is a filter over this array |
| The list's members | `list.members` | R109's choices, already fetched for the assignee picker |
| Household-zone date arithmetic | `dashboard/calendar.ts` | `civilIn`, `now`, `startOfDay`, `addDays`, `instantOf`, `dayKey` - and none of it reads the device |
| The zone | `app/useHouseholdZone.ts` | A hook, so no component reaches for the device out of habit |
| Address-as-state | `useSearchParams`, and `adr/0037`'s precedent | R115 needs no new mechanism |
| The vanishing-row snackbar | `ItemsSection`'s `conflict` snackbar, `adr/0039`'s reasoning | R119 is one more message through it |

## Step 1 - one items query per list

`fetchItems(listId)` loses its `status` argument; `ItemStatus` is **deleted**
rather than left exported, so every reader breaks at compile time rather than
quietly passing a string nothing reads. `itemKeys.filtered` goes with it and
`itemKeys.all(listId)` becomes the only items key - which the optimistic writes
in `useToggleCompletion` and `useAssignItems` already target by prefix, so they
are unaffected.

On the server, `ItemController.list`, `TodoItemService.list` and
`TodoItemRepository.findForList` all lose the parameter:

```java
@Query("SELECT i FROM TodoItem i WHERE i.list.id = :listId ORDER BY i.position ASC")
List<TodoItem> findForList(@Param("listId") UUID listId);
```

The `done` column keeps exactly one reader in Java - the dashboard's, which
needs it because it reads across lists and cannot fetch them all (`adr/0044`).

## Step 2 - `items/filters.ts`

### The filter

```ts
type StatusValue = 'open' | 'done'
type DuePreset = 'overdue' | 'today' | 'week' | 'none'
type DueFilter = { kind: DuePreset } | { kind: 'range'; from: string; to: string }

interface CostFilter {
  choiceIds: string[]      // FIXED
  min: number | null       // NUMERIC
  max: number | null
  unanswered: boolean      // R111, both modes
}

interface ItemFilter {
  status: StatusValue[]
  assigneeIds: string[]    // may contain UNASSIGNED
  due: DueFilter | null
  costs: Record<string, CostFilter>   // keyed by list field id
  sort: SortKey            // 'manual' | 'due' | 'description' | 'status' | 'assignee' | `cost:${id}`
  descending: boolean
}
```

`UNASSIGNED` is the string `'none'`, exported as a constant and used both as the
URL token and as the in-memory member id - one spelling, so a mismatch is
impossible rather than merely unlikely.

**An empty array is an absent filter, everywhere.** `status: []` and
`status: ['open', 'done']` both match everything (`adr/0046`), and the same rule
holds for assignees and for a cost's choices. There is no combination of taps
that shows a member nothing because of a filter they did not notice.

### The codec

`parseFilter(params, shape)` and `writeFilter(filter)`, the shape `adr/0045`
specifies. `shape` is `{ fields, memberIds }` - the list as configured, because
validating is the whole job here and half of it is about members. Three
properties hold and are tested:

- **`writeFilter(DEFAULT_FILTER)` is empty.** The default view leaves the
  address clean (R115).
- **Round-trip.** `parseFilter(writeFilter(f)) === f` for every filter the bar
  can produce.
- **Garbage in, default out.** Every malformed value is dropped
  *independently* - a broken `due` must not take the status filter with it
  (R116). The shape is passed in so a `cost.<id>` naming a field this list does
  not declare, a choice the owner deleted, or a member whose grant was revoked
  is dropped at parse time rather than becoming a filter that hides every row.

### Applying it

```ts
visibleItems(items, filter, fields, zone, today: Civil): Item[]
```

One exported function, taking today rather than reading the clock - and taking
it as a `Civil`, so the only place an instant becomes a household day is still
`calendar.ts`'s `now`. `ItemsSection` calls `now(zone)` *inside* the `useMemo`:
a `Civil` computed during render would change identity every time and make the
memo useless, and `new Date()` there is exactly what the purity lint rule is
for.

The due buckets are **day-granular in the household's zone** (R112), computed
from that day:

| Preset | Matches |
| --- | --- |
| `overdue` | dated, and its day is before today |
| `today` | dated, and its day is today |
| `week` | dated, and its day is within today..today+6 |
| `none` | `dueAt === null` |
| range | dated, and its day is within from..to, both ends inclusive |

Day-granular, not `< now`, for the reason `adr/0040` gives: a chore due at 09:00
belongs to today until midnight, not until 09:01.

Costs are matched per field against the item's `costValues` entry for that field
id:

- no entry, or an entry answering neither way → matches **only** if
  `unanswered` (R111)
- `choiceIds` non-empty → the entry's `choiceId` must be in it
- `min`/`max` → the entry's `amount` must be within, either bound optional

The comparator is **stable by construction**: it falls back to the item's place
in the fetched array, so equal keys keep manual order and a re-render cannot
reshuffle rows. The array's order *is* `todo_item.position` ascending - the
column itself is not on `ItemResponse`, so the index stands in for it, captured
before the sort rather than read with `indexOf` from the array being sorted. Every key sorts **blanks last in both directions** (R114) - the
null-handling sits in one `compareWith(nullsLast)` helper rather than in each
key, because "the one key that forgot" is the whole failure mode here. `status`
sorts To do before Done; `assignee` and `description` compare with
`localeCompare`; a cost sorts by `amount` for a numeric field and by the
**choice's configured position** for a fixed one, not by its label - 5 min / 30
min / 60 min is the order the owner arranged, and alphabetically it is 30, 5,
60.

## Step 3 - `items/FilterBar.tsx`

```
[ Status: To do ▾ ] [ Assignee ▾ ] [ Due ▾ ] [ Effort ▾ ] [ Sort: Manual ▾ ] [ ↑↓ ]  2 filters · Clear
```

One row of chips, each opening a `Menu` (R122): a phone shows the row scrolling
horizontally rather than a panel of expanded inputs, and nothing is permanently
taking vertical space above a list a member is scrolling. Each chip is filled
when its filter is active and outlined when it is not, and its label names the
selection when it is short enough to - so the bar reads as a sentence about what
is being shown.

Built from `fields`: the assignee chip appears only when the list declares
`ASSIGNEE`, one cost chip per `COST` field, and the sort menu offers the same
keys (R120). The due chip is always there, because every list declares the due
date (`adr/0022`).

The **"N filters" count and Clear** (R107, R118) are part of the bar, and
`countActive` is one exported function so the bar and R117's empty state cannot
disagree about whether anything is on.

## Step 4 - `ItemsSection`

```ts
const [params, setParams] = useSearchParams()
const filter = useMemo(() => parseFilter(params, fields), [params, fields])
const visible = useMemo(() => visibleItems(items ?? [], filter, fields, zone, new Date()), [...])
```

Writing is `setParams(writeFilter(next), { replace: true })` - `replace`, so
four taps on the bar are not four history entries (`adr/0045`).

Three things in here are deliberate:

- **The empty state distinguishes three cases** (R117), the way the sidebar's
  groups do: pending, filtered-and-empty ("Nothing matches these filters", with
  Clear), and genuinely empty ("Nothing to do here"). A single `data ?? []`
  would announce the last one while a request is in flight *and* while a filter
  is hiding everything - a confident statement about somebody's own list that is
  false both times.
- **Selection derives from `visible`**, not from `items` (R121). It already
  derived from what was fetched, which the server's status parameter was
  filtering; now one array is what is drawn and what can be selected.
- **A completion that removes a row says so** (R119). `onComplete` checks
  whether the item would survive the active filter and, when it would not,
  raises the existing snackbar with "Done." - the same message-through-one-
  snackbar shape the conflict path uses, because the row leaving cannot be the
  only feedback (`adr/0039`).

## Testing

- **`filters.test.ts`** carries the weight: the three codec properties, every
  due bucket across a zone boundary (an item due 23:00 Singapore time read from
  a device in London is still due today), the cost matrix including unanswered,
  blanks-last in both directions for every key, and the fixed-cost sort being
  by configured position rather than label.
- **`FilterBar.test.tsx`**: a chip per declared field and none for an undeclared
  one, the active count, Clear emptying the address.
- **`ItemsSection.test.tsx`** gains: the filter being read out of the address,
  the filtered empty state not being the empty one, status multi-select showing
  both kinds, the snackbar when a completion removes a row, and a selection that
  stops counting a filtered-out item. Several existing tests now open the list
  at `?status=open,done`: they are about a finished item, and the server used to
  do that narrowing, so a mock could return one and have it drawn regardless.
- **`e2e/tests/test_items.py`** gains one test: filter to a member's own work,
  reload the page, and the same subset is still on screen - which is R115
  through a real address bar rather than a mocked one. `conftest.show_status`
  replaces every `get_by_role("button", name="Done")` in the suite, because the
  toggle those clicked is gone. Two assertions there had to become exact
  matches on the row's own disclosure button: R119's snackbar names the item,
  so `get_by_text(description)` now matches twice and fails strict mode.

## What this deliberately does not do

- **No server-side predicate**, and no half-move of one (`adr/0044`).
- **No new `FieldKind`** (`adr/0046`).
- **No persistence** beyond the address (`adr/0045`).
- **Nothing on the recurring or activity tabs**, and nothing on collections -
  entries have search (0006), which is a different mechanism for a different
  question.

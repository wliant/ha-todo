# 0015 - Lapsing work nobody got to

Design for [requirements/0015](../requirements/0015-lapsing-work-nobody-got-to.md)
(R131-R140). One feature, and the first in a while that is not frontend-only:
the state machine is in the database and has to be.

Decisions this implements rather than argues:
[adr/0048](0048-a-lapse-is-the-second-terminal-state.md), which builds on
[adr/0024](../adr/0024-reopening-keeps-the-record-and-done-becomes-derived.md)
and answers the question [adr/0046](../adr/0046-status-is-a-filter-not-a-declared-field.md)
left open.

## Scope

Five steps, in this order, because each one breaks the next at compile time and
that is the mechanism being relied on:

1. **The schema** - the two columns, the widened CHECK, and `done` renamed to
   `closed`. Nothing compiles against the entity until this is decided.
2. **The entity and the state machine** - `lapseBy`, `isLapsed`, `getState`,
   and what `isCompleted` now has to exclude.
3. **Service, endpoint and DTOs** - the three-way switch, and the renames that
   force every caller to be revisited.
4. **The client** - the four binary assumptions the boolean was hiding.
5. **Docs** - this, the requirements, the ADR, and `CLAUDE.md`.

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| A terminal state with a stamp and an actor | `todo_item.completed_at` / `completed_by` | The lapse pair is shaped identically, so the CHECK grows rather than changes shape |
| Undoing a terminal state | `reopened_at` / `reopened_by`, `TodoItem.reopen` | R135 needs no second mechanism: one reopen stamp undoes either |
| A derived answer to "is this work" | the `done` generated column (`adr/0024`) | Widening it makes five agenda queries right without touching them |
| Version checks on every item write | `requireFreshView`, `@Version` | A lapse is a contended write like any other and inherits the 409 |
| A hold-for-a-note complete control | `items/CompleteControl` | Reused untouched; the lapse sits beside it rather than inside it |
| An optimistic "row leaves the agenda" mutation | `dashboard/queries.ts` | A lapse leaves identically, so one mutation serves both verbs |
| A multi-select status filter in the address | `items/filters.ts`, `adr/0045` | R137 is a third value, not a new mechanism |

## Step 1 - the schema

`V919__lapsing.sql`. Two columns, a CHECK that grows four clauses, and the
column rename.

```sql
ALTER TABLE todo_item ADD COLUMN lapsed_at TIMESTAMPTZ;
ALTER TABLE todo_item ADD COLUMN lapsed_by UUID REFERENCES app_user (id) ON DELETE SET NULL;
```

The four new CHECK clauses, and what each one is for:

| Clause | Says |
|---|---|
| `(lapsed_at IS NULL) = (lapsed_by IS NULL)` | the pair moves together |
| `(lapsed_at IS NULL OR points_earned IS NULL)` | **R133 is an invariant**, not a rule to remember |
| `reopened_at IS NULL OR completed_at IS NOT NULL OR lapsed_at IS NOT NULL` | a reopen still implies something to reopen |
| `reopened_at >= completed_at` and `>= lapsed_at`, each guarded on its own column | ordering, split because an item can carry both stamps |

Then the rename, which is the step with the consequences:

```sql
DROP INDEX ix_todo_item_due;
DROP INDEX ix_todo_item_assignee_due;
ALTER TABLE todo_item DROP COLUMN done;
ALTER TABLE todo_item ADD COLUMN closed BOOLEAN NOT NULL
    GENERATED ALWAYS AS ((completed_at IS NOT NULL OR lapsed_at IS NOT NULL)
                         AND reopened_at IS NULL) STORED;
```

Both indexes are recreated verbatim but for `WHERE closed = FALSE`: **a partial
index and the query that hopes to use it must name the same column**, or the
planner ignores it. `ix_todo_item_activity` is partial on `completed_at` and is
deliberately untouched - the activity feed is about completions, and a lapse is
not one.

## Step 2 - the entity and the state machine

Three transitions, and what each one clears, is the whole design:

| Verb | Sets | Clears |
|---|---|---|
| `completeBy` | completion columns, points | reopen stamp, **lapse stamp** |
| `lapseBy` | lapse stamp | reopen stamp, **points** |
| `reopen` | reopen stamp | points |

Each clears the reopen stamp for the reason `completeBy` already did: without
it the item reads as reopened and `closed` stays false, so the chore sits on
the agenda looking open.

`completeBy` clearing the lapse while `lapseBy` keeps the completion is the one
asymmetry, and `adr/0048` records why: a completion is history somebody reads,
a lapse is only ever current state.

Three readers in Java, all computed rather than read from the generated column
(Hibernate does not re-select one after a write):

```java
public boolean isCompleted() {
    return completedAt != null && reopenedAt == null && lapsedAt == null;
}
public boolean isLapsed() { return lapsedAt != null && reopenedAt == null; }
public ItemState getState() { ... LAPSED wins when both stamps are present ... }
```

The `lapsedAt` clause in `isCompleted()` is the one most likely to be left out,
and it is the difference between the leaderboard being right and being wrong.

## Step 3 - service, endpoint and DTOs

`setCompleted` becomes `setState`, and its `if/else` becomes a switch:

```java
if (request.state() != ItemState.COMPLETED && request.noteHtml() != null) {
    throw new InvalidCompletionException("A note belongs on a completed item.");
}
switch (request.state()) {
    case COMPLETED -> { item.completeBy(...); claimImages(item, me.getId()); }
    case LAPSED    -> item.lapseBy(me);
    case OPEN      -> item.reopen(me);
}
```

The note guard now covers two branches rather than one, and still refuses
rather than drops: the CHECK would make a dropped note a 500, and a silently
discarded field hides a client bug.

Three renames, all at once, so nothing can keep speaking the old protocol:
`CompletionRequest` → `ItemStateRequest`, `PUT /items/{id}/completion` →
`/state`, `ItemResponse.completed` → `.state` (plus `lapsedBy`/`lapsedAt`,
reported only while the lapse stands, on the same terms the completion record
already followed).

**`TodoItemRepository` - five mechanical, one not.** `findScheduledFor`,
`findUndatedFor`, `countUndatedFor`, `findOverdueFor` and `countOverdueFor` are
`done` → `closed` and nothing else; lapsed work leaves all three surfaces by
construction. `pointsByMember` is the exception and the trap:

```java
// not closed = TRUE: an item completed, reopened and then lapsed is closed
// and still has a completed_by, and earned nothing.
WHERE i.list.id = :listId AND i.completedBy IS NOT NULL
  AND i.completedAt IS NOT NULL AND i.reopenedAt IS NULL AND i.lapsedAt IS NULL
```

`DashboardItemResponse` needs no change at all: it carries no completion fields
because its rows are pre-filtered, and that stays true.

## Step 4 - the client

`Item.completed: boolean` becomes `state: ItemState`. Replacing rather than
adding is what found the four binary assumptions:

- **`matchesFilter`** read `status[0]` whenever fewer than all values were
  selected. Correct for two, silently wrong for three - `open,done` is two of
  three and would have been tested against `open` alone. Now a membership test
  over `statusOf(item)`. **An empty set is still an absent filter**
  (`adr/0046`), which the length guard keeps true.
- **`sortValue('status')`** was `completed ? 1 : 0`; now `STATUS_ORDER.indexOf`.
- **`FilterBar`** hard-coded `['open','done']` and a label ternary; now a table
  over `STATUS_ORDER`. `statusLabel` said "To do & done" for anything that was
  not exactly one value, which with three would have been simply wrong.
- **`ItemRow`** branched `completed ? Reopen : Complete`; now three-way.

The row:

```
[x] Paint the shed                       [ ✓ Complete |📝] [📅✗] [✎]
[x] ~~Sort the garage~~  (Lapsed)                          [ ↩ ]  [✎]
[x] ~~Buy milk~~  (Ana — 50 points)                        [ ↩ ]  [✎]
```

The lapse control is a **sibling** of the complete `ButtonGroup`, not a third
button in it: the group is one gesture with one accelerator, and a give-up verb
inside it is a mis-tap away from the primary one. The same shape is added to
`AgendaItemRow` (shared by the Day view and the overdue strip) and, spelled out
as a word, to `DashboardItemDialog`, which has the room.

`useToggleCompletion` → `useSetItemState`; `useCompleteFromAgenda` →
`useSetStateFromAgenda`, taking the target state. **Its three hand-written
`setQueriesData` shapes need no new case** - a lapsed row leaves scheduled,
undated and overdue identically - but `withoutItem`'s total arithmetic and the
`onSettled` invalidation of **both** `dashboardKeys.all` and `listKeys.all`
survive the refactor unchanged. A writer there that forgets `['lists']` leaves
the originating list showing an open chore with nothing to refetch it and no
test failing.

`ItemsSection.move(item, state)` is one function for both verbs, because the
only thing that differs is the word in the snackbar (R140).

## Testing

- `app/.../TodoItemTest` - the transition table, including the three that are
  easy to get wrong: lapsing clears a reopen stamp, completing clears a lapse,
  and an item holding both stamps is lapsed rather than completed.
- `ItemApiIntegrationTest` - against real Postgres, because only a real
  database computes a generated column and `adr/0033`'s lesson is that one
  Postgres *accepts* is not necessarily one that computes what was intended:
  `closed` over all three states and after a reopen, the CHECK refusing points
  on a lapse and a half-written lapse pair, and the complete→reopen→lapse item
  crediting nobody on `/points`.
- `DashboardApiIntegrationTest` - a lapsed chore absent from the ranged query,
  the undated panel (and its total) and the overdue strip; reopening putting it
  back.
- `filters.test.ts` - "shows one status, or some, or everything" over three
  values, with the two-of-three case named as the regression.
- `FilterBar.test.tsx`, `ItemsSection.test.tsx`, `DashboardPage.test.tsx` - the
  chip, the snackbar when the row leaves, reopening a lapsed item, and a
  lapse-from-the-agenda analogue of "takes the row out before the server
  answers", written against a write that never resolves so the refetch cannot
  do the assertion's work for it.
- `e2e/tests/test_items.py`, `test_dashboard.py` - lapse on a list and from the
  agenda; the row leaves the default view, is not under Done, is under Lapsed,
  and reopens. Row presence is asserted via the row's **own disclosure button**,
  never `get_by_text`: the snackbar names the item too and strict mode fails on
  two matches.

## What this deliberately does not do

- **No note on a lapse** (R134, `adr/0048`). The note belongs to the completion
  (`adr/0023`).
- **No automatic lapsing.** A second unasked-for actor costs more than it looks
  (`adr/0025`), and "we did not get to this" is a judgement.
- **No bulk lapse.** Claiming is bulk because it is routine (R46); giving up on
  nine things is not.
- **No reopening from the agenda.** Unchanged from 0010: a terminal chore is not
  there to be reopened, and the record lives on the list.
- **No declared per-list status.** `adr/0046` still holds; this adds a state the
  product models, not a field a list configures.

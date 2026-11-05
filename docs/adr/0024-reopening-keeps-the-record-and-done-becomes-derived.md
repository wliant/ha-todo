# 0024 - Reopening keeps the record, and "done" becomes a derived column

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R49 (completion is reversible, and reversing it returns the points)
Builds on: [0012](0012-cost-and-points-credited-to-the-completer.md)

## Context

R49 asks for two things that cannot both be true of the row as it stands.

Reopening must make an item actionable again, and it must **keep** what the
completion recorded - who finished it, when, and the note (R37) - because a
household that reopens an item still wants to know what happened the first time.

But "completed" is currently `completed_at IS NOT NULL`. `TodoItem.isCompleted()`
is exactly that expression, `TodoItemRepository.findForList` filters on
`i.completedAt IS NULL` for the open view, and `V906__items.sql`'s
`ix_todo_item_completer` is partial on the same column. Keeping `completed_at`
through a reopen therefore leaves the item reading as finished everywhere: filtered
out of the to-do list, struck through on screen, and counted as done by any new
query. Today's `reopen()` nulls all three completion columns precisely to avoid
this, which is why reopening currently works and why it currently forgets.

So reopening must change *something*, and the question is what, and what "open"
then means.

There is a second constraint. `adr/0012` snapshots points onto `points_earned` at
completion and R49 returns them, so reopening must still null that column - and
`TodoItemRepository.pointsByMember` already sums a nullable column, so the
leaderboard is correct either way.

## Decision

**Reopening stamps rather than erases.** Two new columns, `reopened_at` and
`reopened_by`, set when an item is reopened. The completion columns and the note are
left exactly as they were. `points_earned` is nulled, which is the one thing R49
does take back.

**"Done" is a stored generated column, computed by the database.**

```sql
ALTER TABLE todo_item ADD COLUMN done BOOLEAN NOT NULL
    GENERATED ALWAYS AS (completed_at IS NOT NULL AND reopened_at IS NULL) STORED;
```

This is the only version of the new definition that cannot drift. The repository
elsewhere refuses derived booleans - `password_hash IS NULL` *is* "no password
required" (`adr/0005`), `pinned_at` carries pinning, `isCompleted()` was computed -
on the sound reasoning that a boolean which can disagree with its data eventually
does. A generated column is the exception that proves the rule rather than breaking
it: Postgres computes it from the same two columns on every write, and **nothing in
Java can write it**. It is mapped `insertable = false, updatable = false` and used
**only** in JPQL predicates and index definitions.

**`isCompleted()` stays computed in Java.** Hibernate does not re-select a generated
column after a write, so a Java reader of the mapped field sees a stale value
immediately after `completeBy` - which would render a just-completed item as open in
the very response that completed it. The field carries a comment saying so, because
the obvious "simplification" is to use it in `ItemResponse.of`.

**Six readers of completion change together**, and this is the cost being accepted:
`isCompleted()`, `findForList`'s status predicate,
`ItemExceptionHandler.describe(ItemResponse)` (which names the other member on a
409), `ItemRow`'s strike-through and chip, every partial index whose predicate was
`WHERE completed_at IS NULL`, and the new dashboard and activity queries.

**Activity keys on `completed_at`, not on `done`.** A reopened item's completion
still appears in the week's activity (R43), because it happened, and R49 kept the
record so it could be seen. The points column will differ from the count for exactly
those rows, which is the honest presentation rather than a discrepancy.

## Consequences

- The completion record survives a reopen, which is what R49 asked for, and the
  activity feed can show work that was later undone rather than quietly losing it.
- **"Completed" is no longer one column**, and six places must agree about it. The
  generated column makes them agree by construction, but a seventh reader written
  against `completed_at` will compile, pass, and be wrong - which is why every new
  partial index is defined on `done` rather than on the underlying columns.
- A partial index predicate must now be written as `WHERE done = FALSE` and the
  query must say `done = FALSE` too, or the planner will not use it. The indirection
  is the price of the index remaining usable.
- **A reopened item holds a note nobody can see.** No API returns it while the item
  is open and no screen renders it; somebody reading the database directly will find
  a sentence about work that was undone. Completing again does not clear it unless
  the member writes over it. This is the consequence most likely to be regretted,
  and it is the direct cost of honouring "keep the record" literally.
- `ddl-auto: validate` must learn the generated column. Expect one iteration after
  the migration applies, loud at startup rather than subtle at runtime.
- `ck_todo_item_completion` grows two clauses - the reopen stamp implies a completion
  to reopen, and comes no earlier than it - so the whole group still moves together
  or not at all.
- Points are still returned on reopen, so a member can complete, reopen and complete
  again without minting anything: the credit is nulled before it is recomputed.

## Alternatives considered

- **Reopening clears everything, note included.** One line in `reopen()`, no new
  columns, nothing redefined, and "done" stays a single-column test. There is even a
  product argument: reopening already takes the points back, so it already means
  "this was not actually done", and forgetting it is consistent. Rejected because
  R49 explicitly asks to keep the record, and because the activity feed would then
  lose completions retroactively - a week's history that changes when somebody
  reopens an item is a history nobody can rely on.
- **Keep the columns and add no constraint, letting the item stay "complete".**
  Rejected immediately: the Reopen button would not reopen anything, only refund
  points, and the item would remain filtered out of the to-do view.
- **An ordinary `done` boolean maintained in `completeBy` and `reopen()`.** No
  generated column, no Hibernate caveat, and the index predicates are plain.
  Rejected as exactly the drift this repository refuses elsewhere: two writers, one
  truth, and a bug that looks like "the item is in the wrong list" months later.
- **A `reopened` boolean instead of a timestamp.** Cheaper, and the derivation reads
  the same. Rejected because the timestamp costs nothing extra and is the only thing
  that can answer "when was this undone" - and `reopened_by` is useless without it.
- **An append-only `item_completion` log, one row per completion.** The best shape
  for history: several completions per item, reopens as events, activity that is
  genuinely a feed. Rejected as far too large for this change - it moves `adr/0012`'s
  derived leaderboard off `todo_item` - and named in `adr/0023` as the successor if
  per-item completion history is ever wanted.

# 0048 - A lapse is the second terminal state, so "done" becomes "closed"

Status: accepted
Date: 2026-11-13
Implements: [requirements/0015](../requirements/0015-lapsing-work-nobody-got-to.md)
R131-R140 (an item can be lapsed, and lapsed work leaves the agenda)
Builds on: [0024](0024-reopening-keeps-the-record-and-done-becomes-derived.md),
[0046](0046-status-is-a-filter-not-a-declared-field.md)

## Context

R131 asks for a third state. Everything in the product that asks "is this item
still work?" asks one generated column, `done`, defined by `adr/0024` as
`completed_at IS NOT NULL AND reopened_at IS NULL`. It has exactly six readers,
all in `TodoItemRepository`: five `done = FALSE` predicates behind the agenda,
the undated panel and the overdue strip, and one `done = TRUE` behind the
leaderboard. Three partial indexes are defined on it.

`adr/0046` declined a declared per-list status and said exactly what such a
thing would owe: "an answer to how it relates to completion. An item marked
'Blocked' that is also `done`; the points credited on completion (R35) when the
status says otherwise; what the dashboard's `done = FALSE` means; whether a
reopen (R49) resets a status." This decision is that answer, for this one
state. It does not reopen reading 2 - a lapse is a state the product models,
not a field a list declares.

The five "still work" readers all want lapsed items gone. The one "was this
finished" reader does not. That split is the whole design problem: the two
questions have been one column since there was only one way out.

## Decision

**A lapse is two columns, `lapsed_at` and `lapsed_by`**, shaped exactly like
the reopen pair `adr/0024` added, and constrained by the same
`ck_todo_item_completion` so the whole group still moves together or not at
all. `points_earned` must be null whenever `lapsed_at` is set: R133 is a
database invariant, not a rule whoever writes the next lapse path has to
remember.

**The generated column is widened to cover both terminal states, and renamed in
the same migration:**

```sql
ALTER TABLE todo_item ADD COLUMN closed BOOLEAN NOT NULL
    GENERATED ALWAYS AS ((completed_at IS NOT NULL OR lapsed_at IS NOT NULL)
                         AND reopened_at IS NULL) STORED;
```

Widening is what makes the five agenda predicates right without being touched:
they say "show me work", and lapsed items stop being work. Adding a *second*
generated column beside `done` would have been the other option, and is the
reason this one was rejected - nine predicates would then each have to remember
to exclude lapsed work, and the one that forgets is wrong without failing.

**The rename is the load-bearing half.** `done` would now have to mean
"completed or lapsed", and `adr/0024` already identified the failure a lying
name produces: "a seventh reader written against `completed_at` will compile,
pass, and be wrong". A column whose name answers a different question than its
definition is that failure with a head start. Renaming breaks every reader at
compile time and at schema-validation time, which is the same move
`SidebarResponse` made when it lost its `pinned`/`recent` split rather than
gaining a field.

**There is deliberately no mutual-exclusion constraint** between `completed_at`
and `lapsed_at`. Complete, reopen, lapse is a legal sequence, and `adr/0024`
kept the reopened completion precisely so a week's activity does not change
when somebody reopens something. So an item can hold both stamps, and a
precedence rule decides what it *is*: a lapse is always the later statement,
because `lapseBy` clears the reopen stamp that must have preceded it.

**That precedence is what `pointsByMember` owes.** The leaderboard cannot read
`closed = TRUE AND completedBy IS NOT NULL` any more: such an item has a
completion record and is closed, so it would contribute a nonsense zero-point
row for somebody who earned nothing. It spells out a *standing completion*
instead - completed, not since reopened, not since lapsed - which is
`TodoItem.isCompleted()`, in JPQL. The activity feed is untouched: it keys on
`completed_at` because a completion that was later lapsed still happened.

**`completeBy` clears a lapse; `lapseBy` does not clear a completion.** The
asymmetry is deliberate. A completion is history somebody reads (R43); a lapse
is only ever the current state, and nothing looks back at one the item has
moved on from.

**The wire becomes a three-valued `state`**, replacing the boolean:
`CompletionRequest` becomes `ItemStateRequest`, `PUT /items/{id}/completion`
becomes `PUT /items/{id}/state`, and `ItemResponse.completed` becomes
`ItemResponse.state`. All three renamed together, so no client can keep sending
the old body and have it quietly mean something narrower. Two booleans could
describe a state that does not exist, and `completed: false` would otherwise
have had to mean both "reopen this" and "give up on this".

**The agenda gets a second verb, which widens `adr/0039`.** 0010 said the
agenda is "writable in exactly one way", and it is now two. The reasoning that
made one write safe makes the second safe identically: it goes through the
item's own endpoint, which checks the caller's grant and compares the version
first, and the agenda adds no authorization of its own. One mutation serves
both, because the agenda shows outstanding work and a row reaching *either*
terminal state simply leaves - so the three optimistic payload edits are the
same for both. Reopening is still absent, for 0010's reason.

**No note on a lapse** (R134). The note is part of the completion
(`adr/0023`), and a second sanitised rich-text column brings its own CHECK
clause, its own sanitiser call and its own image claiming, for a sentence
nobody is obliged to write.

## Consequences

- The five agenda predicates are correct about lapsing without saying anything
  about it, and the three partial indexes stay usable because predicate and
  query were renamed together.
- **"Finished" and "no longer work" are now two questions**, and a reader that
  picks the wrong one is wrong quietly. `closed` answers the second; nothing
  else does. `isCompleted()` answers the first and had to grow a `lapsedAt`
  test - the clause most likely to be left out of a future reimplementation.
- `pointsByMember` no longer reads the generated column at all, which looks
  like an inconsistency and is the point: it is the one query asking the other
  question.
- An item can carry a completion record nobody can see *and* a lapse. Somebody
  reading the database directly will find both. This is the same cost
  `adr/0024` accepted, extended by one column.
- `ddl-auto: validate` must learn the renamed column and the two new ones.
  Loud at startup rather than subtle at runtime.
- **The migration drops and recreates two indexes and a CHECK.** A full-table
  validation scan, which is nothing at household scale and would not be on a
  larger one.
- Every client reader of `completed` broke at compile time, which is how the
  four binary assumptions in the browser were found - including
  `matchesFilter`, whose "fewer than all means exactly one" shortcut was
  correct for two statuses and silently wrong for three.

## Alternatives considered

- **A second generated column beside `done`.** `done` keeps its meaning,
  `open`/`active` answers the agenda's question, nothing is renamed and no
  existing SQL moves. Rejected because it leaves five predicates reading the
  column that is now the wrong one, with nothing to make them fail - the exact
  shape of defect `adr/0024` was written to prevent, and the shape this
  codebase keeps finding (`['lists']` invalidation, `NavGroup`'s empty state).
- **Widening `done` without renaming it.** One line of migration, no readers
  touched. Rejected for the reason above: the saving is entirely in not having
  to look at the six call sites, and looking at the six call sites is the
  review this change most needs.
- **A `state` column maintained in Java, replacing all three stamps.** One
  value, no derivation, no precedence rule. Rejected as exactly the drift this
  repository refuses elsewhere (`adr/0005`, `adr/0024`): a maintained column
  that can disagree with the timestamps beside it eventually does, and the
  timestamps are not optional - R134 needs when, and R49 needs who.
- **Lapsing as a delete.** Honest about the outcome and far smaller. Rejected
  because R135 wants it reversible and because a household that deletes what it
  did not get to cannot see that it keeps not getting to the same thing.
- **A per-list declared status with choices**, the reading `adr/0046`
  described. Still rejected, and this decision deliberately does not become
  one: it adds a state the product itself understands, with a defined
  relationship to completion, points, the agenda and the recurrence job. A
  declared field would owe that relationship for values the product has never
  seen.
- **Lapsing automatically when a due date passes.** Tempting, and it is what
  the overdue strip makes people want. Rejected as a second unasked-for actor
  (`adr/0025` already warns what one costs), and because "we did not get to
  this" is a judgement, not a timestamp comparison.

# 0012 - Cost and points, credited to the completer

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R22, R23

## Context

R22 gives a list named cost fields, each either a set of fixed choices (5 min,
30 min, 60 min) or a numeric amount in a unit the owner names (dollar, hour). R23
lets the owner attach points: a point value per choice, or a rate per unit so a
rate of 1000 per hour makes a one-hour item worth 1000. Points go to whoever
completed the item, and are totalled per list per member.

Two things need deciding beyond the storage shape. What a fixed choice actually
is - a label, or a measured quantity with a label on it. And when the points are
computed, given that a list's configuration can change after an item is completed.

## Decision

**A fixed choice is a label worth points, and carries no numeric amount.** "30 min"
is an opaque string next to a point value; the database stores no 30 and no unit for
a fixed cost field. A numeric cost field is the other shape: an amount, a unit the
owner named, and an optional rate.

**Points are computed at completion and snapshotted onto the item.**
`todo_item.points_earned` is written in the completion transaction from the config
as it stands at that moment - the choice's `points`, or `amount * points_per_unit`
summed across the item's cost values. Un-completing clears it along with
`completed_by` and `completed_at`; the three columns move together, held by a
`CHECK`.

**The leaderboard is derived, not counted.** There is no `list_points` table.
`GET /api/lists/{id}/points` is

```sql
SELECT completed_by, SUM(points_earned) FROM todo_item
WHERE list_id = ? AND completed_by IS NOT NULL GROUP BY completed_by
```

covered by a partial index on `(list_id, completed_by)`.

**Points are credited to `completed_by`, never to `assignee_id`.** The two columns
are independent: an item assigned to one member and completed by another credits
the second. R23 is explicit, and it is the whole point - points reward doing the
work, not being handed it.

**A cost field may have a cost and no points.** `points_per_unit` is nullable and a
choice's `points` defaults to 0, so "this chore takes 30 minutes" is sayable
without turning the list into a game.

## Consequences

- A list can total its **points** but not its **costs**. "How many points did Ben
  earn" is a query; "how many hours of chores did we do this week" is not, for a
  fixed-choice field, because no minutes were ever stored. Numeric fields can be
  summed; fixed ones cannot. This is the accepted cost of the simpler choice
  editor, and reversing it later means adding an amount column and a unit, then
  backfilling labels like "30 min" by hand - which nobody can do automatically.
  Recorded plainly because it is the decision in this ADR most likely to be
  regretted.
- Editing a rate or a choice's point value does **not** rewrite history. Items
  completed before the change keep what they earned. The leaderboard and the live
  configuration can therefore disagree, and that is correct: the alternative is a
  member's score silently changing because someone edited a number.
- The flip side: a mistake in a rate cannot be corrected retroactively except by
  un-completing and re-completing the affected items.
- `SUM` over items is exact by construction - there is no counter to drift from the
  items, and no delete path that can forget to decrement one. At household scale
  the query is free.
- `points_earned` is nullable and meaningful only when completed, which the `CHECK`
  enforces rather than trusting.
- Deleting the completing user sets `completed_by` to NULL (`ON DELETE SET NULL`),
  which drops their points from the leaderboard while leaving the item complete.
  Acceptable: the member is gone.

## Alternatives considered

- **A choice carries a numeric amount as well as a label.** Lets a list total real
  hours, which is a question a family plausibly asks. Rejected for now because it
  doubles the per-choice input (label, amount, points) and forces every fixed field
  to name a unit it otherwise does not need, to buy a report nothing in
  requirements/0004 asks for. The user chose this trade explicitly.
- **A denormalised `list_points (list_id, user_id, points)` counter** incremented
  on completion. Makes the leaderboard a single-row read. Rejected: it is a second
  source of truth that disagrees with the items the first time a delete, a cascade
  or an un-complete path misses a decrement, and the read it optimises is already
  trivial.
- **Computing points on read from the live configuration.** No snapshot column, so
  no history to go stale. Rejected because it means editing a rate silently
  rewrites everyone's past score, and because a choice that has since been deleted
  would make a completed item's points unrecoverable.
- **Crediting the assignee.** Simpler - one column instead of two - and wrong. It
  rewards being assigned, and it leaves an unassigned item's completion crediting
  nobody.
- **Points on the list rather than per cost field.** A single "points for finishing
  an item" number, ignoring cost. Loses the thing that makes points fair, that a
  60-minute chore is worth more than a 5-minute one.

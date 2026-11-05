# 0022 - The due date is declared on every list

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R40 (every list has a due date), R44 (the dashboard)
Amends: [0011](0011-item-fields-as-a-typed-per-list-declaration.md) - `DUE_AT`
leaves the set of kinds a list may choose not to declare.

## Context

0004 R21 made the due date one of five field kinds a list's owner may declare, and
`DUE_AT` has behaved like `DETAIL`, `ASSIGNEE` and `COST` ever since: present if the
owner added it, absent otherwise. That was right while a date was only read on the
list that held it.

R44 changes who reads it. A dashboard answers "what do I need to do today" across
every list the caller can see, and it can only answer from the lists that happened
to declare the field. A household with six lists, two of which declare a due date,
gets a planning view that silently omits two thirds of its work - and the omission
is invisible, because nothing on the dashboard can say "there are lists I cannot
represent". A member would conclude the dashboard is wrong rather than that a field
is missing, and would stop trusting it.

The obvious alternative - have the dashboard read `due_at` regardless of whether
the list declares the field - is worse, because `TodoItemService.applyDueAt` nulls
the column when the list declares no `DUE_AT` field, deliberately, so that
disabling and re-enabling a field does not resurrect stale values. A dashboard
reading a column the write path keeps empty reads nothing.

## Decision

**`DUE_AT` joins `DESCRIPTION` as a kind every list declares.** A list has exactly
one, always. `ListFieldService.normalise` guarantees it the way it already
guarantees `DESCRIPTION`, `TodoListService.create` writes the row beside
`ListField.description(list, 0)`, and `ListFieldService.replace` refuses to remove
it - a `PUT` that omits it gets it back rather than an error, because "not
mentioned" already means "unchanged" for singleton kinds.

**It is optional to fill in, and the owner may insist.** `required` defaults to
`false` and remains the one thing the owner toggles. The field cannot be removed;
it can be ignored. This is the difference R40 turns on: the *column* is always
readable, so the dashboard is complete, while nobody is forced to date a workout
log.

**Existing lists are backfilled, not migrated lazily.** `V909__due_at_always_declared.sql`
inserts a `DUE_AT` row for every list that lacks one, with `required = false`, at
`MAX(position) + 1` computed per list. `WHERE NOT EXISTS` leaves a list that already
declared the field exactly as it is, `required = true` included. The per-list
subselect matters: `ux_list_field_position` is `UNIQUE (list_id, position)
DEFERRABLE INITIALLY DEFERRED`, so one `INSERT ... SELECT` commits cleanly, but
deferred is not absent and a constant position would collide at commit on every
list that already holds a field there.

**Presence is enforced in the service, not in SQL.** There is no constraint forcing
every list to hold a `DUE_AT` row, because SQL cannot express "every row in this
table has a child of this kind" without a trigger. That is the same position
`adr/0011` already takes for `DESCRIPTION`, and it is recorded here so the absence
does not read as an oversight.

## Consequences

- The dashboard is complete by construction: every item in every readable list can
  carry a date, so a range query over `due_at` cannot silently exclude a list.
- `ListFieldService.describe`'s exhaustive switch and the `FieldKind` enum are
  untouched - this changes *when* a kind is declared, not the set of kinds - so the
  compile-time announcement `adr/0011` relies on is unaffected.
- Lists that deliberately did without a due date now have one. A workout log shows a
  "Due" input in its add form. It is optional, so nothing becomes unsubmittable, but
  it is one more control on a screen whose owner did not ask for it. Accepted: R44 is
  worth more than the tidiness of the lists that will ignore it.
- The undated panel (R45) contains every open item on day one, because the backfill
  sets `required = false` and nobody has filled anything in. That panel therefore
  needs a cap and a count from the start, not after somebody notices. This is the
  consequence in this ADR most likely to be mistaken for a bug.
- `FieldConfigEditor` gains a second locked row. `DUE_AT` leaves `OPTIONAL_KINDS` and
  joins `DESCRIPTION` as undeletable, with its Required checkbox still live - which
  `DESCRIPTION`'s does not have, so the locked-row component stops being "locked and
  always required" and becomes "locked, required configurable".
- Two e2e tests that switch the due-date field on (`test_items.py`, `test_fields.py`)
  stop having a switch to click.

## Alternatives considered

- **Leave `DUE_AT` optional and have the dashboard read whatever lists declare it.**
  No migration, no change to any list that did not ask for one. Rejected because the
  resulting dashboard is incomplete in a way it cannot report: the member sees an
  under-full view and has no way to learn why, and `applyDueAt` nulls the column on
  undeclared lists anyway, so the data is not merely unexposed but absent.
- **A per-list `tracks_due_dates` boolean beside the always-present column.** Keeps
  the dashboard's query simple and lets a list opt out of the affordance. Rejected as
  two sources of truth for one fact: the flag and the field row can disagree, and the
  question "what happens to the dates already entered when the flag goes off" has no
  good answer. It is also a second mechanism for something `list_field.required`
  nearly expresses already.
- **A new `DATE` field kind, with a flag marking which one is the due date.** Most
  consistent with `adr/0011`'s typing, and would let a list carry several dates.
  Rejected because it needs the opt-in flag anyway to answer "which date does the
  dashboard mean", so it buys expressiveness nobody asked for at the cost of a join
  on every dashboard query and a new kind in every exhaustive switch.
- **Backfill with a fixed position, or rewrite every list's positions.** Simpler SQL.
  Rejected: a fixed position collides with existing fields on most lists, and
  rewriting positions would reorder field configurations their owners arranged
  deliberately.

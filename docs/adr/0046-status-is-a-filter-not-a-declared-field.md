# 0046 - Status is a filter dimension, not a field a list declares

Status: accepted
Date: 2026-11-11

## Context

`requirements/0013` names status alongside assignee, due date and cost as
something a member filters on, and asks for it to be multi-select like the
others. "Make status a field as well" admits two readings, and they are very
different pieces of work:

1. **A filter dimension.** Status stays what the product already models -
   completion - and stops being a toggle group of its own, becoming one control
   on the filter bar with two values a member picks any combination of.
2. **A new `FieldKind.STATUS`**, declared per list with owner-configured choices
   (Blocked, In progress, Waiting), stored per item in a new value table.

Reading 2 is a domain change, not a UI one. `FieldKind` is a closed set whose
members each have an item column or a value table; a status with choices would
need a `list_field_choice` reuse or a table of its own, an item value, a
position in the add form, and - the expensive part - an answer to how it relates
to completion. An item marked "Blocked" that is also `done`; the points credited
on completion (R35) when the status says otherwise; what the dashboard's
`done = FALSE` means; whether a reopen (R49) resets a status. Every cross-list
reader in the product keys on completion today.

## Decision

**Status is a filter over the completion the product already models: To do and
Done, multi-select, defaulting to To do.** No new `FieldKind`, no migration, no
item column.

- `ItemsSection`'s `ToggleButtonGroup` is replaced by a chip on the filter bar
  whose two values are independently selectable. **Both selected and neither
  selected both mean "show everything"** - an empty set is an absent filter
  everywhere on this bar, and a list that silently shows nothing because the
  member unticked two chips is a bug they cannot diagnose.
- "All" disappears as a named option. It was a word for the empty filter, and it
  is now spelled by the chips being off.

## Consequences

- **The filter bar is uniform.** Every control on it is a multi-select over a
  known set, or a range; status is not a special case, which is what R108 was
  asking for.
- **The default stays To do**, so a member who touches nothing sees today's
  view.
- **`requirements/0013` records per-list statuses as out of scope**, with this
  ADR as the reason - rather than leaving it to be rediscovered as "obvious"
  later.
- **If reading 2 is ever wanted, this does not block it.** A declared status
  field would be another cost-shaped field with choices, and the filter bar
  already renders exactly that. What it would still owe is the relationship to
  completion, which is the part this decision is declining to guess at.
- **One real loss**: the three-way toggle was a single tap from "To do" to
  "Done", and the chips are two. Accepted - it is the price of the combination
  the toggle could not express at all.

## Alternatives considered

**A `FieldKind.STATUS` with per-list choices.** The richer product, and it
needs a requirements document of its own: it changes what "done" means, which
is the predicate the dashboard, the agenda, the points leaderboard, the
recurrence job and the overdue strip all read. Doing it as a side effect of a
filter bar is how a product acquires two notions of finished.

**Keeping the exclusive toggle and adding the other filters beside it.** The
smaller change, and it leaves one control on the bar that behaves unlike every
other one, with a third value that exists only because the first two cannot be
combined.

**Dropping the status filter's default**, showing every item on arrival.
Rejected: it silently changes what every member's list looks like, in exchange
for a purity nobody asked for.

# 0060 - A lapse has no status to map to, so it leaves

Status: superseded by [0062](0062-a-list-is-a-rich-sensor-the-households-own-ui-reads.md)
Date: 2026-11-20

## Context

An item here has **three** states - `OPEN`, `COMPLETED`, `LAPSED` - since
[0048](0048-a-lapse-is-the-second-terminal-state.md) added the second terminal
one for work that was planned and that nobody got to. The point of that
addition was that the two states previously available were both wrong for it:
leaving the chore open makes the overdue strip a permanent reproach, and
ticking it complete credits points and lands in the activity feed for work
nobody did.

Home Assistant's to-do platform has **two**: `NEEDS_ACTION` and `COMPLETED`.
That is the entire vocabulary, and
[0059](0059-a-list-is-a-todo-entity-and-the-other-two-kinds-are-services.md)
chose the platform knowingly, for the native behaviour it brings.

So a lapsed item has to be presented as something, and both available answers
are the two answers `0048` exists to reject.

## Decision

A `LAPSED` item is **omitted from the entity's items entirely**. It is not
`COMPLETED`, not `NEEDS_ACTION`, and not present.

The mapping is written as an explicit three-way match on the state, not as a
boolean test.

Lapsing is also not a write the integration offers
([requirements/0022](../requirements/0022-home-assistant-acts-as-a-household-member.md),
explicitly not required): a lapse is a member deciding nobody is going to get to
something, which is a judgement rather than a physical fact.

## Consequences

- **Neither lie is told.** Work nobody did is not reported as done, and a
  closed chore does not sit in the automation nagging forever.
- **This is what every planning surface in the product already does.** A lapse
  leaves the agenda, the overdue strip and the scheduled query, because `closed`
  is true. Omitting it here is consistency, not a special case.
- **A chore can vanish from Home Assistant with no trace.** Something the
  automation listed yesterday is simply gone, and nothing in Home Assistant
  explains it. Accepted: the record lives on the list, where
  [0024](0024-reopening-keeps-the-record-and-done-becomes-derived.md) keeps it,
  and an automation has no screen on which an explanation would help.
- **An automation counting its own list disagrees with the product by the
  number of lapses.** Harmless for the uses R190-R197 describe, and the overdue
  sensor is the figure meant for counting.
- **The obvious one-line mapping is wrong.** `COMPLETED if state != "OPEN"`
  reads naturally, passes every test written against two states, and reports
  work nobody did as done - exactly the defect `0048` created a third state to
  make impossible in the first place. Written down here because the three-way
  match looks like something to simplify.

## Alternatives considered

- **Present a lapse as `COMPLETED`.** Keeps the item visible and its history
  legible, and is what a reader who thinks in terms of the `closed` column
  would write. Rejected: it credits finished work. `pointsByMember` has to
  spell out a standing completion for the same reason, and `0048` names that as
  the thing most easily got wrong.
- **Present a lapse as `NEEDS_ACTION`.** Strictly worse. The chore is closed and
  has left every planning surface; this would have the household's automation
  reminding somebody about it indefinitely, which is the permanent reproach
  0015 R131 was written to end.
- **Present it as `COMPLETED` with a marker in the summary** - "(lapsed) take
  the bins out". Honest to a human reading a dashboard, and invisible to
  everything that consumes the status, which is what automations do. It also
  puts text the product did not write into a field the product owns.
- **Ask the to-do platform for a third status.** The right fix in the long run
  and not available in a custom integration; the status enum is Home
  Assistant's.

# 0050 - The collapsed form asks for what costs a tap

Status: accepted
Date: 2026-11-15

## Context

0005 R39 gave the add form a collapsed mode and keyed it off one thing: a
field's `required` flag. The reasoning was sound - most items are a line of
text - but `required` turns out to be a proxy for the wrong property.

What the form should hide is fields that are **expensive to answer**: a detail
field that drags a rich-text editor behind it, a cost that needs a number
thought about. What it actually hides is fields the list's owner did not insist
on, which includes the assignee and the due date - the two fields a household
list declares most often, the two that every planning surface in the product
reads (`/api/dashboard/*`, the Mine toggle, the overdue strip, the week and
month grids), and the two that 0012 R94 already reduced to a single tap.

The result is that the form's most ordinary path produces items no planning
surface can see, and the remedy is a disclosure the member has no reason to
open. Separately, the assignee was a `select`: two taps and a popup over the
form, to choose one of four names that fit on a line.

Three constraints were real:

- The ISO instant must stay the single source of truth for the date
  (0012 R96, `adr/0036`). Any "compact" rendering that kept its own copy of the
  date would be the bug that ADR exists to prevent.
- `ItemsSection` owns one boolean for the form's mode. A third mode, or a
  second piece of remembered state, would be a new thing to keep consistent.
- `hidden`, which decides whether the disclosure appears, happens to equal
  "declared and not required" today. Changing what collapsed shows breaks that
  coincidence, and the failure is silent in both directions.

## Decision

**Collapsed shows what the list insists on, plus the assignee and the due date
whenever the list declares them.** Everything else still waits behind the
disclosure.

**The due date renders compactly while collapsed**: the Today / Tomorrow /
Clear chips, and nothing else. `DueAtField` gains a `compact` prop that returns
after the chip row; the picker, the time box and the steppers belong to the
expanded form. The emit path, `instantOf` and `DEFAULT_TIME` are shared, not
reimplemented - compact is a render decision about one control, not a second
control.

**The assignee becomes a row of chips** in both modes - one per member, plus
Nobody when the field is optional - carrying `aria-pressed` and the member's
name as its accessible name, with `MemberAvatar` beside it as decoration.

**The disclosure's condition stops being `hidden > 0` over non-required
fields** and becomes "is there anything still to show": the non-required detail
and costs, **plus the due date whenever it is declared** (its picker is behind
the toggle even when the field is required), and **never the assignee**.

## Consequences

- An item added the quickest way now carries a date and an assignee if the list
  declares them, which is what makes it visible to the agenda. That is the whole
  point of the change.
- The collapsed form is taller by two fields, one of them a wrapping chip row.
  Accepted in the requirement.
- A date set from the chips is end of day in `app.timezone`, in both modes,
  because it is literally the same code path. A member who wants 09:00 expands.
- The compact mode renders a non-interactive readout chip for a date that is
  neither today nor tomorrow. No path reaches it today - the form cannot be
  collapsed once expanded - and it exists so that the collapsed form can never
  hold a value it does not show. It is one chip rather than a mechanism, on
  purpose.
- Every by-name selector for the assignee `combobox` breaks, in unit tests and
  in `e2e/`. Deliberate: the control is a different control, and a test that
  still passed would be asserting something that is not on screen.
- The two chip rows now use one selected/unselected vocabulary (filled primary
  versus outlined). A third control that invents its own is the drift to watch.

## Alternatives considered

**Show every declared field collapsed.** Then collapsed *is* expanded and R39
was pointless. Its reasoning holds for the detail editor (~130KB of ProseMirror,
lazily loaded precisely so most lists never fetch it) and for costs.

**Make the assignee and due date `required` by default on every list.** Would
put them on screen with no code change at all, and is much worse: it makes the
form unsubmittable without answers the member may not have, and it quietly
overrides a choice that belongs to the list's owner. 0005 R40 deliberately
declares the due date on every list *without* requiring it.

**A third form mode between collapsed and expanded.** Names the thing precisely
and costs a new piece of state in `ItemsSection`, plus a rule about which mode
a form opens in. The compact due control is a property of one field's
rendering, which is where it is now expressed.

**A `ToggleButtonGroup` for the assignee instead of chips.** Semantically the
better match for "pick one of these". Lost because the date control beside it
is already chips: two adjacent single-choice rows drawn from two different
component families read as two different kinds of thing, and `aria-pressed` on
a chip recovers the semantics that was the only real argument for it.

**Keeping the dropdown and only changing what is shown collapsed.** Half the
request, and it leaves the most frequent gesture on the form costing two taps
and a popup to choose between four names.

# 0017 - The two answers worth asking for up front

Status: accepted
Date: 2026-11-15
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) - whose
R39 keyed the collapsed add form off `required` alone, and
[0012](0012-quick-due-dates-and-a-navigable-sidebar.md) - whose R94 made a date
two taps and left those taps one disclosure away.

## Problem

R39 made the add form start small, and it was right about why: most items are a
line of text, and a form asking six questions for "milk" is why somebody
reaches for paper. But it drew the line in the wrong place. Keyed off `required`
alone, a list that declares an assignee and a due date - **the two fields a
household list declares most often, and the two that every planning surface in
the product reads** - asks for neither until the member finds "More fields".

The cost shows up in what the rest of the product then cannot do. The agenda,
the overdue strip, the Mine toggle and the week and month grids are all built on
`dueAt` and `assigneeId`. An item added without them is invisible to every one
of those surfaces - it sits on its list and appears nowhere a member looks to
plan a day. So the form's most ordinary path produces exactly the items the
product is least able to help with, and the fix a member needs is a disclosure
they have no reason to open.

These two fields are also, unlike the others, **cheap to answer**. 0012 already
reduced a date to one tap on "Today" or "Tomorrow" (R94). An assignee is a
household of four, which is four names. Neither is a question - it is a tap,
already decided before the form opened. The expensive controls are the date
picker, the time box and the steppers, and nothing about asking "today or
tomorrow?" requires them to be on screen.

The assignee field has a second, smaller problem that is independent of the
first. It is a `select`: a tap to open it, a tap to choose, and a popup covering
the form in between, to pick one of four names that would all fit on one line.

## Who uses it

- **The member adding a chore on a phone, standing in the kitchen.** They know
  it is for tomorrow and they know whose it is. Both should cost one tap, from
  the form they are already looking at.
- **The member who plans from the agenda.** Every item that reaches them does
  so because somebody dated it. They are the ones who pay for a form that made
  dating optional *and* hidden.
- **The member who wants none of this.** A line of text with no date and nobody
  assigned stays a complete, submittable item. Nothing here makes a field
  required that the list's owner did not.

## Requirements

### Must have

- **R148 The collapsed add form shows the assignee and the due date whenever
  the list declares them**, required or not. This narrows R39: collapsed is no
  longer "only what the list insists on" but "what the list insists on, plus the
  two that cost a tap". Every other field still waits behind the disclosure.
- **R149 Collapsed, the due date is the chips and nothing else.** Today,
  Tomorrow, and Clear when a date is set and the list does not insist on one.
  No date picker, no time box, no steppers - those are what "More fields" is
  for. A date already set that is neither today nor tomorrow is shown as a
  readout, so the collapsed form can never hide a value it is holding.
- **R150 The assignee is a row of chips, collapsed and expanded alike** - one
  per member who can see the list, plus Nobody when the list does not insist on
  an assignee. One tap, no popup. The dropdown goes.
- **R151 The disclosure still appears when there is genuinely more.** That now
  includes the due date's full controls, not only unshown fields: a form whose
  only hidden thing is the date picker must still offer a way to reach it.
- **R152 Nothing about what is sent or required changes.** The same payload,
  the same server-side validation, the same per-list field configuration, and
  the same single source of truth for the date - the ISO instant (0012 R96).

### Explicitly not required

- **Showing every field collapsed.** That is just the expanded form, and R39's
  reasoning stands for the rest: a detail editor drags ProseMirror behind it, a
  cost needs a number thought about, and neither is a tap.
- **A third form mode.** Collapsed and expanded, as R39 has it. The compact due
  control is the collapsed rendering of one field, not a new state the form can
  be in.
- **Avatars as the chip's label.** The name is the label. A face on the chip is
  decoration beside it, and must stay out of the accessible name.
- **The same treatment for the recurrence form.** A standing instruction is
  written once and read for months; it is not the gesture somebody performs
  twenty times a week, and its assignee dropdown is left alone deliberately.
- **A member-ordering rule.** Chips follow the order the list already hands
  over. Sorting them by who is assigned most often is a guess dressed as help.

## Consequences accepted deliberately

1. **The collapsed form is taller.** Two fields' worth, one of them a chip row.
   That is the trade R148 is making: a form slightly longer than the shortest
   possible one, against items that no planning surface in the product can see.
2. **A date set collapsed is end-of-day in the household's zone**, because that
   is what the chips have always produced (0012 R95). A member who wants 09:00
   expands, which is where the time box lives.
3. **A list with many members gets a long chip row.** It wraps. A household is
   the unit here (`adr/0010`), and a list with twelve members is not the case
   this product is shaped around - if it arrives, the row is where to fix it.
4. **Every by-name selector for the assignee dropdown breaks.** Deliberately:
   the control is a different control, and a test that still passed against a
   `combobox` would be testing something that is no longer there.

## Open questions

- **Whether the collapsed form should remember a member's last answer** - the
  list, the assignee, the date they usually pick. Plausible, and a different
  kind of feature: this one is about what is on screen, not about guessing.
- **Whether the cost fields could be made cheap enough to show collapsed.** A
  FIXED cost is a handful of choices and could be chips too. Left out because
  it is a judgement about points, which is the one thing on the form somebody
  actually weighs.

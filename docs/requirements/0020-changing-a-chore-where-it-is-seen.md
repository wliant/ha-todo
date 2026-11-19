# 0020 - Changing a chore where it is seen

Status: accepted
Date: 2026-11-19
Refines: [0010](0010-completing-from-the-agenda.md) - whose R83 made the agenda
writable in exactly one way, and
[0018](0018-adding-another-one-like-this.md) - which widened that a second time
by letting the agenda create.

## Problem

The agenda is where a household looks at its week, and therefore where it finds
out that something about a chore is **wrong**. The date is the common case, and
it is the one this surface exists to show: a chore sitting on Thursday that
belongs on Saturday is visible on the agenda and invisible everywhere else.
Nobody is going to do it is the second case - and 0015 gave that a verb. Nobody
*has* been given it is the third, and assignment is a field.

Until now the only answer to any of them was "open the list". That is a
navigation away from the thing being looked at, a scroll to find the row, a
return, and - with the default To do filter and a status the member did not
change - an item that may not be on the screen they land on. Three steps to
move a date by one day, started from a surface that was already showing that
date.

Duplicating already works from here (0018 R156) on exactly this reasoning: the
list is already decided, so the one question that would otherwise have to be
asked has an answer. Editing is the same shape, and its absence is the odder
of the two - the agenda could copy a chore but not correct one.

## Who uses it

- **The member re-planning the week.** They are looking at Thursday, with too
  much on it. Moving one chore to Saturday should be the gesture they are
  already in, not a trip to a list.
- **The member who dated something wrong.** 0012 R94 made dating two taps; the
  cost of *fixing* a date stayed at a navigation.
- **The member handing work over.** The chore is nobody's, or is the wrong
  person's, and the agenda is where that becomes obvious.

## Requirements

### Must have

- **R173 The agenda's item popup offers Edit**, beside the Duplicate it already
  offers - so it is reachable from the Day rows, the week and month grids, the
  undated panel and the overdue strip, one control serving every view.
- **R174 Editing from the agenda is editing the item**, not a new one: the same
  id, the same completion record, the same points. Every value it already has
  is in the form, and a field left untouched is saved unchanged.
- **R175 It is the list's own form**, drawn from that list's declared fields and
  its members, as the duplicate form is (0018 R157). The agenda crosses lists
  and has no configuration of its own.
- **R176 A save carries the version the form was drawn from**, so an edit that
  lost a race to another member is refused and said so, rather than silently
  overwriting them.
- **R177 Saving updates both the agenda and the list.** A chore moved to
  another day appears there, and the list it belongs to is not left showing the
  old date.
- **R178 The form opens expanded**, showing everything the item is. The
  collapsed form (0017) exists to keep *adding* cheap; an edit is opened
  because of a field, and usually one the collapsed form would hide.

### Explicitly not required

- **Deleting from the agenda.** Deletion destroys a record and has no undo
  (unlike completing, lapsing and reopening, which are all reversible). It
  stays on the list, with the item in front of the member.
- **Editing from an agenda *row*.** The row has two write controls already
  (0010 R83, 0015 R138), and 0018 R153's reasoning holds unchanged: a third
  icon on a phone is a mis-tap. The popup is one tap away.
- **Moving a chore to another list.** That is a different feature with a
  different question, and the one question this surface is able to avoid
  asking.
- **Editing a calendar event.** 0007 R63 refuses this permanently; nothing
  here authors or changes an event.
- **Editing a standing instruction from the agenda.** A rule is not on the
  agenda - its occurrences are - and 0016 put its own permission rule on it.
- **Editing a completed chore from the agenda.** A chore in a terminal state
  has left the agenda (0010 R86, 0015 R136); its record lives on the list, as
  reopening does.

## Consequences accepted deliberately

1. **The agenda now writes three ways**: completing/lapsing (0010, 0015),
   creating (0018) and now changing. All three are bounded by the same thing -
   the list is already decided - and none of them adds authorization of its
   own, because the item's own endpoint is what checks the grant.
2. **The dialog fetches the list's configuration when it opens**, as the
   duplicate dialog does, cached under the list's own keys.
3. **An edit from here can move a chore out of the period on screen.** That is
   the point of it; the row leaves, and the refetch is what puts it where it
   now belongs.
4. **A 409 is now possible from the agenda's form.** The dialog stays open
   holding what was typed and says what happened; nothing is retried behind the
   member's back, which is the rule everywhere else in the product.

## Open questions

- **Whether the popup should offer Reopen** for a chore completed by mistake.
  It is deliberately absent today (0010), and a completed chore is not on the
  agenda to be found.
- **Whether a date moved from the agenda should be draggable** rather than
  typed. A grid that accepts a drag is a different design, not a control.

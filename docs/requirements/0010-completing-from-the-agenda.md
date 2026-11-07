# 0010 - Completing a chore from the agenda

Status: accepted
Date: 2026-11-07
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) - its
dashboard (R44) and its completion gesture (R48), which were built on separate
surfaces and never joined, and
[0008](0008-a-navigable-agenda.md) - which made that surface navigable and
renamed it the agenda.

## Problem

The agenda is the landing page: it is what the household opens, and it answers
what is due today across every list a member can see. It is also, today,
**read-only**. A member who has just put the bins out has to read the row, tap
it, tap "Open Chores", find the row again on the list, tick it, and go back.
Five taps and two screens to record a thing the agenda was already showing them.

The surface that tells somebody what to do is the surface they are standing on
when they finish doing it. Nothing about the gesture needs to be new - a list
already has a completion control, with a hold that offers a note (0005 R48,
`adr/0027`) - it simply is not reachable from the one screen that is open.

## Who uses it

- **A member working through the day**, phone in one hand. They opened the
  agenda to see what was left; the chore they just did is on screen.
- **The member who wants to say how it went** - a line, or a photograph of the
  shelf actually put up (0005 R37). The same accelerator as on a list, or they
  will learn that the note only exists somewhere else.
- **Two members in the same hour**, both looking at the household's agenda, both
  reaching for the same chore. That is this product's whole reason to exist and
  it must not become a surprise here.

## Requirements

### Must have

- **R83 A member completes a chore from the agenda's Day view.** The row the
  Day view already draws carries the completion control, and tapping it records
  the completion against the caller - the same write, the same points, the same
  record as doing it on the list.
- **R84 They can say how it went in the same gesture.** The hold-for-a-note
  accelerator and its visible, tabbable alternative both behave as they do on a
  list. One route to the note, not a second implementation of it.
- **R85 It works from anywhere on the agenda.** The popup that opens from a week
  cell, a month cell or the undated panel can complete the item too - so a chore
  due on Thursday can be ticked off on Thursday from whichever range the member
  happens to be in.
- **R86 The result is what going to the list and coming back would have
  given.** The agenda shows outstanding work, so a completed chore leaves it.
  Nothing else about it changes: the completion, its points and its note are
  recorded exactly as the list records them, and the list shows it struck
  through as it always has (0005 R38).
- **R87 The second member to tick a chore is told who got there first.** The
  stale write answers 409 as it already does, and the agenda says whose it was
  rather than failing quietly or retrying behind anybody's back.

### Explicitly not required

- **Reopening from the agenda.** A completed chore is not on the agenda to be
  reopened; the undo lives on the list, where the record is (0005 R49).
- **Editing, assigning or selecting** from the agenda. R46's bulk claim and the
  item form belong to a list, which knows its own fields and members.
- **Completing an event.** Permanently refused, for the reason 0007 R63 gives:
  this product does not author anybody's calendar, and an event is not work.
- **Showing the chore struck through where it was.** Considered and rejected -
  see the consequence below.
- **Any new endpoint.** The item's completion write is already per-item and
  already checks the caller's grant on its list.

## Consequences accepted deliberately

1. **The row disappears when it is ticked**, which is a thing moving on screen
   for a reason that is one frame old. It is the same answer as the list's "To
   do" filter gives, and the alternative - keeping it struck through - fills a
   week or month cell with work nobody has to do, on the one screen whose job is
   to say what is left. A confirmation says what happened so the vanishing is
   not the only feedback.
2. **The agenda is no longer a read-only surface.** Its queries live outside the
   per-list key hierarchy on the explicit grounds that nothing on it writes
   (0005's design). That premise is gone, so a completion has to invalidate both
   the agenda and the lists, and a future writer there has to remember it.
3. **A chore can be completed from a date nobody is standing on** - looking at
   February, from the popup. That is R85 working as asked, and it is the same
   freedom a list already gives, which has no notion of today at all.

## Open questions

- Whether earned points can be revoked by a list's owner. Unchanged from 0005.
- Whether a shared list should show who else has it open. Unchanged from 0004.
- Whether the undated panel should offer the completion control on its rows
  directly, rather than only through the popup. Deliberately left until somebody
  asks: the panel's rows are one line of text by construction (0005 R45) and a
  control in each would double their height.

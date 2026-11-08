# 0012 - Quick due dates and a sidebar that reaches every list

Status: accepted
Date: 2026-11-08
Refines: [0004](0004-lists-items-and-personal-settings.md) - whose R30 declares
the due-date field and whose R25-R28 built the sidebar this document rebuilds,
and [0003](0003-pwa-and-ui-shell.md) - which made that sidebar the one
navigation surface.

## Problem

Two small frictions, both in the gesture a member performs most often.

**Dating a chore costs more than the chore is worth.** The due-date field is a
bare `datetime-local`. A member adding "put the bins out" has to open a native
picker, find a day in a grid, then type or spin a time they do not care about -
and the overwhelming majority of what a household dates is due *today* or
*tomorrow*, at no particular hour. The time component is the worst of it:
`datetime-local` starts empty, so there is no way to supply only a date, and the
member is made to answer a question they have no answer to. Left at midnight,
the chore is due before the day it means; so a chore meant for Thursday lands on
Wednesday's row in the agenda and reads as overdue by Thursday lunchtime.

Worse, the input speaks the **device's** zone. `adr/0036` gave the household one
timezone and made the agenda bucket by it, so a member typing "Thursday 09:00"
on a phone set to London is writing an instant the agenda files under
Wednesday. The one editor in the product that still reads the device's clock is
the one that decides which day a chore appears on.

**The sidebar cannot reach a list the member has not just used.** It has two
sections, Pinned and Recent, and Recent is capped at six and shows only lists
with a recorded interaction. A list never opened on this account does not appear
at all - including one just created, and including every list somebody else
shared. Getting to it means navigating to `/lists` and searching, from a sidebar
whose entire job is to be the shortcut. Collections are worse: they have no
sidebar presence whatsoever (`adr/0029` gave them no per-member state), so every
visit to a collection goes through the index.

And "Recent" is the wrong ordering for a shortcut. The list a member opens every
single day is pushed out of the sidebar by three lists they opened once, which is
exactly backwards: a shortcut should privilege habit, not novelty.

## Who uses it

- **The member adding a chore for tonight.** Two taps, no picker, no time.
- **The member who always shops on Saturday.** The grocery list is the list they
  open; it should be the first thing in the sidebar, and it should stay there
  through a week of not shopping.
- **The member who was just granted a list.** It should be in their sidebar the
  first time they look, not after they have gone and found it.
- **The member looking up a recipe.** Collections are reference material reached
  from anywhere; the sidebar is where "anywhere" starts.
- **The member with eleven lists.** The sidebar must not become the index. How
  many it shows is theirs to decide.

## Requirements

### Must have

- **R94 A due date can be set without opening a picker.** Today and tomorrow are
  each one tap, and the date can be moved a day at a time in either direction
  from wherever it is. The native picker stays available for a date further out;
  nothing here replaces it.
- **R95 A dated chore is due at the end of its day unless the member says
  otherwise.** Setting a date supplies 23:59 as the time. The time stays
  editable and an edited time is never overwritten.
- **R96 The day a member picks is the household's day.** A date entered here
  means that date in `app.timezone`, so it lands on the agenda row the member
  was looking at, whatever zone the device is in.
- **R97 A chore's date can be cleared.** Unless the list insists on one, a date
  set by mistake can be taken off without emptying the form.
- **R98 The sidebar reaches every list and every collection the member can
  see.** Not only the ones they have interacted with. A list created or shared a
  minute ago is in the sidebar, in its order, without being opened first.
- **R99 Lists and collections are each one group in the menu, opened and closed
  by the member.** Both groups can be open at once, closing one does not close
  the other, and the group's own label still navigates to its index.
- **R100 The groups are ordered by how often the member uses each list, over
  the last month.** Not by how recently: a list opened daily outranks one opened
  once yesterday. A month is the window, so a habit that stops decays out of the
  sidebar rather than holding its place forever.
- **R101 Pinned lists come first.** A pin is an explicit instruction and
  outranks measured habit. Pinning stays a property of lists only; collections
  have no pins (`adr/0029`), so a collection group is ordered by use alone.
- **R102 How many each group shows is a personal preference.** Ten by default,
  changeable by the member, and personal rather than per-device.

### Should have

- **R103 An empty group says so.** A member with no collections opens the group
  and is told there are none and how to make one, rather than being shown
  nothing and left to wonder whether it failed.

### Out of scope

- **A pinned collection.** `adr/0029` keeps collections out of
  `list_user_state`, R101 accepts the consequence, and nothing here loosens it.
- **A recurring due date from this control.** Recurrence is a rule on a list
  (0005 R41), not a property of one item's date field.
- **Ordering the `/lists` index by use.** The index is a browse and sorts by
  name; only the sidebar is a shortcut.
- **Counting anything but opening a list.** Frequency measures visits, not
  items added or completed. A second signal is a second thing to keep
  consistent, for an ordering nobody will inspect.

## Open questions

- Whether a member should be able to reorder a group by hand, which would be a
  third ordering input competing with pins and measured use.
- Whether the frequency window should be configurable. One month is a guess,
  chosen because a household's habits turn over in weeks.

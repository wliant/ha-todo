# 0005 - Completion detail, recurrence, and planning views

Status: accepted
Date: 2026-11-05
Refines: [0004](0004-lists-items-and-personal-settings.md) R21 (the due date
field), R24 (assignment), R30 (completion)
Amends: [0004](0004-lists-items-and-personal-settings.md) - its exclusion of
recurring items, and its open question about where a completed item goes.
[0003](0003-pwa-and-ui-shell.md) R25 - a signed-in member no longer lands on the
list of lists.

## Problem

0004 built the middle, and a household has now used it. Four things are missing in
a way that shows.

**A tick is not a record.** Completion already stores who finished an item and when
- the columns are there and the API returns them - and nothing displays either.
"Did anyone actually do the bins?" is answerable by the database and not by the
product. Worse, a tick says nothing about *how* it went: the medicine was given but
half the dose was refused, the repair was done but the part is the wrong size. That
sentence is the useful part and there is nowhere to put it.

**The same work is typed in again every week.** Bins, vitamins, rent, hoovering.
0004 excluded recurring items on the reasoning that the domain had to exist first.
It exists, and a household list is *mostly* standing instructions - so the
exclusion is now the single largest source of retyping in the product.

**Nothing answers "what do I need to do today".** A member with six lists has to
open six lists. The due date field exists (R21) but it is per-list and opt-in, so
even a member who uses it has no surface that reads across lists, and a date is
only worth entering if something later reads it.

**The checkbox does too much.** One control means "this is finished", and finishing
is the only thing a member can express about an item in bulk or at all. There is no
way to say "I'll take these four" - which is how work is actually divided in a
kitchen, out loud, before any of it is done.

0004's open question about where a completed item goes is also still open, and it
blocks the first problem: a row cannot show a completion note until it is decided
whether a completed row is still on screen.

## Who uses it

- **Members**, on phones, mostly in a kitchen and often with one hand. They finish
  work and say how it went, claim work before doing it, and want one screen that
  says what today holds across every list they can see.
- **The list's owner**, with one extra concern: the standing instructions their
  list generates on its own, which they write once and expect to keep working
  without being watched.
- **The admin** (0002 R8), who administers accounts and, now, one piece of
  machinery: they can make the recurrence job run immediately when a household says
  "it didn't appear".

## Requirements

### Must have

- **R36 A completed item says who finished it and when.** Both are already stored
  at completion (R30) and neither is shown. The member's name and the time appear
  on the item, because the household's actual question is "has this been done, and
  by whom" and the product currently makes that unanswerable from the screen.
- **R37 A member records how it went.** On completing an item, a member may write a
  **completion note**: rich text with bullets and embedded pictures, the same
  editor the **detail** field uses (R21). A photograph of the finished repair, or
  one line saying the dose was refused, is the part the next person needs. The note
  belongs to the act of completing rather than to the item's shape, so every list
  can carry one and no list configures it away.
- **R38 An item row is collapsed or expanded, and only one at a time.** Collapsed
  shows the description and the item's values. Expanded adds the **detail** and the
  **completion note**. Expanding a row collapses whichever row was expanded, so a
  long list on a phone never becomes a wall of pictures. **This closes 0001's and
  0004's open question about where a completed item goes**: it stays where it is,
  struck through, with everything that explains it one tap away rather than in a
  separate section a member has to go looking for.
- **R39 Adding an item starts small.** The add form has a collapsed and an expanded
  mode. Collapsed shows only the fields the list marks **required**; expanded shows
  every field the list declares. Most items on most lists are a line of text, and a
  form asking six questions for "milk" is why a member reaches for paper instead.
- **R40 Every list has a due date, and may insist on one.** The **due date and
  time** field is declared on every list, always, and cannot be removed. It is
  **not** required by default, and the list's owner may make it required. This
  supersedes R21's treatment of the due date as one more opt-in field: a planning
  view that reads across lists (R44) is only as good as the lists that happened to
  declare the field, and "the dashboard silently ignores this list" is not a
  behaviour anybody would choose.
- **R41 A list carries standing instructions.** The list's owner writes
  **recurrence rules**, each a template - description, detail, assignee, costs -
  plus a schedule: every day, chosen days of the week, or a day of the month, with
  an interval so "every other Saturday" is expressible. Rules are managed apart
  from the items they produce, because a rule is not an item and editing next
  week's bins should not mean editing this week's.
- **R42 The system creates recurring items by itself.** Occurrences appear on the
  list without anybody opening it. The deployment sets how often the system looks,
  and the **admin can make it look now** - which is the answer to "it didn't
  appear" that does not require a shell on the server. A rule may also be run from
  its own list by anyone who can see that list.
- **R43 A list shows who did what this week.** Alongside its items, a list reports
  the last seven days of completions: who finished how many, worth how many points,
  and what they were. Points already accumulate per list (R23); this is the same
  fact over a window a household can actually feel, because "ever" stops being
  motivating around the third week.
- **R44 A dashboard answers "what do I need to do".** One surface, across every
  list the member can see, in three ranges: **today**, **this week** as the seven
  days laid out side by side, and **this month** as a calendar. The week and month
  views show descriptions only - a date grid with chips in it is unreadable on a
  phone - and opening an item shows the rest without leaving the view.
- **R45 Undated items are never hidden.** The dashboard shows items with no due
  date beside all three ranges, always. A planning view that quietly drops
  everything nobody dated would hide most of the household's work, and would teach
  members that the dashboard is not to be trusted.
- **R46 A member claims work in bulk.** A member selects several items and assigns
  them to themselves in one action. This is how work is divided in practice - "I'll
  do the upstairs ones" - and doing it one item at a time through an edit dialog is
  why it is done out loud and then forgotten instead.
- **R47 Claimed work is visibly claimed, to everybody.** An item the assignee chose
  for themselves is marked differently from one somebody else assigned to them, and
  every member sees the same mark. "Ben took this" and "Ana gave this to Ben" are
  different facts about a household, and only the first means the work is spoken
  for. The mark is not colour alone.
- **R48 Completing is a deliberate act, and may carry a note in one gesture.** The
  item's checkbox now **selects** (R46); completion is its own control. Pressing it
  completes immediately. Holding it for a second offers the completion note (R37)
  instead, so the common case stays one tap and the richer case needs no detour -
  and because a hold is not something a keyboard can do, the note is also reachable
  as an ordinary control beside it.
- **R49 Completion is reversible, and reversing it returns the points.** A
  completed item can be **reopened**: it becomes actionable again and the points
  credited at completion are taken back, because they were credited for work the
  household is now saying was not finished. What the completion recorded - who,
  when, and the note - is kept rather than erased, and simply stops being shown.

### Explicitly not required

- Groups, roles or permission levels on a list. A grant is binary, as in 0004.
- Recurrence beyond daily, chosen weekdays, and a day of the month, each with an
  interval. No "last Friday", no "every weekday", no occurrence count, no
  exceptions, and nothing resembling an RFC 5545 rule string.
- Catching up on occurrences the system missed while it was down. A chore that was
  due on Tuesday and never appeared is not worth creating on Friday.
- Reminders, due-date notifications, or push of any kind. Still excluded from 0004,
  and R44 is what replaces the need: a member looks, rather than being told.
- Editing a completion note after the fact, or a history of notes per item. One
  note, written when the work was finished.
- A dashboard across members - "what is Ben doing today". The dashboard is the
  caller's own view of lists the caller can see, and a household-wide planning
  board is a different product.
- Nested lists, list templates, archiving, or comments on items. Unchanged.
- Spending points, or a household-wide score. Unchanged from 0004: points still
  accumulate per list and what they are for is still a later decision.
- Showing who else has a list open. Still an open question, below.
- Offline use. Still out of scope from 0001.

## Consequences accepted deliberately

1. **"Completed" stops being one column.** R49 keeps the completion record through a
   reopen, so "is this done" can no longer be "does it have a completion time" -
   it becomes "it has one and has not since been reopened". That is one more fact
   on the row and six places in the code that must agree about it, and the only
   safe version is one the database computes rather than one anybody maintains.
   Accepted because the alternative is forgetting what happened, and the household
   asked to keep it.
2. **A reopened item still holds a note nobody can see.** Completing it again does
   not clear the old note unless the member writes over it. This is the honest cost
   of R49 keeping the record: the data is there, the screen is not, and somebody
   reading the database directly will find a sentence about work that was undone.
3. **A hold is not a keyboard gesture.** R48's press-and-hold has no keyboard
   equivalent - holding Enter on a button repeats the click rather than extending
   it - so the note must have a second, ordinary, tabbable control or the feature
   is unreachable for anybody not using a touchscreen or a mouse. The hold is
   therefore an accelerator and never the only route.
4. **The item row's controls change meaning, and the existing tests say so.** The
   checkbox stops completing (R46, R48). Every unit and end-to-end test that
   completes an item by clicking a checkbox named "Complete …" has to change in the
   same breath, and the new selection checkbox must be named so that none of them
   can keep passing by accident. A test that goes green while asserting the wrong
   control is worse than one that fails.
5. **The system now does something when nobody asked it to.** R42 is the first
   behaviour in the product that is not a response to a request. It assumes exactly
   one running instance of the backend, which is true of both deployments and is
   not enforced anywhere; a second instance would have two jobs creating the same
   occurrences. This is the same assumption 0003's deferral of Redis already rests
   on, and a second instance breaks sessions before it breaks recurrence.
6. **Every list gets a due date affordance whether its owner wanted one or not.**
   R40 adds the field to lists that deliberately did without it. The field is
   optional, so nothing becomes unsubmittable, but a workout log now has a "due"
   input in its add form. Accepted because R44 is worth more than the tidiness of
   the lists that will ignore it.
7. **A recurring item can be assigned to somebody who later loses access.** The
   rule outlives the grant (R41 vs R19). The rule must then produce an unassigned
   item and say why, rather than failing quietly or, worse, producing an item
   assigned to somebody who cannot see the list.

## Open questions

- Whether points already credited can be revoked by the list's owner. R49 settles
  only the reopen path - the member who completed the work undoes it, or somebody
  else does on the same evidence. Whether an owner can simply take points away is
  still unanswered, and is a question about authority rather than mechanism.
- Whether a shared list should show who else has it open. Unchanged from 0004, and
  more pressing now that R46 lets two members claim from the same screen at once.
- Whether a completion note should be editable after the fact. Excluded above on
  the grounds that a note records a moment, but a typo in the one sentence the next
  person reads is a weak reason to retype nothing.

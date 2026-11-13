# 0015 - Lapsing work nobody got to

Status: accepted
Date: 2026-11-13
Refines: [0004](0004-lists-items-and-personal-settings.md) - whose R30 makes
completion the only way an item stops being work, and
[0005](0005-completion-detail-recurrence-and-planning-views.md) - whose R49
made that reversible without making it optional.

## Problem

An item has two states, and only one of them is an exit. A chore that was
planned and never done - because the week had too much else in it, because the
shop was shut, because it stopped mattering - has nowhere honest to go.

A member has two choices today and both are bad. Leave it open, and it sits in
the overdue strip accusing them indefinitely: 0011 put late work on the agenda
precisely so it could not be ignored, which makes a chore nobody is ever going
to do a permanent reproach that drowns the ones they will. Or tick it complete,
which is a lie the product then believes: it credits points (R23), it lands in
the week's activity (R43), and it tells the household somebody did something
nobody did.

Neither of those is a small inaccuracy. The first makes the overdue strip
useless, which is the one feature whose whole value is that it is short enough
to read. The second corrupts the leaderboard, which is the one feature whose
whole value is that it is true.

What is missing is a word for *we are not doing this*, said out loud, that
leaves a record and stops the nagging.

## Who uses it

- **The member triaging a day with too much on it.** They are looking at the
  agenda, not at a list, and the judgement they are making - this one, not that
  one - is the judgement this feature is for.
- **The member tidying a list that has gone stale.** Six chores from a month
  nobody had, and clearing them should not mean claiming them or deleting the
  fact that they were ever planned.
- **The member who wants the leaderboard to mean something.** They are not
  asking for a feature; they are the reason lapsing must not credit points.
- **The member who ignores all of this.** Nothing changes for them. A list and
  an agenda behave exactly as they did, and no item lapses unless somebody says
  so.

## Requirements

### Must have

- **R131 An item can be marked lapsed, and that is a terminal state.** Lapsed
  means planned and not done. It is not a variety of done and not a variety of
  open: an item is in exactly one of the three.
- **R132 Anybody who could complete it can lapse it.** Completion is not the
  assignee's privilege or the owner's (R30), and giving up on a chore is not a
  larger claim than finishing one. A household where only the assignee may say
  "we did not get to this" is a household where nobody says it.
- **R133 A lapse earns nothing.** No points are credited, and points already
  credited are returned, exactly as reopening returns them (R49).
- **R134 A lapse records who and when, and nothing else.** No note, no reason.
  The state is the statement.
- **R135 A lapsed item can be reopened.** It returns to being work, the same
  gesture and the same control that reopens a completed one. Lapsing is not a
  delete.
- **R136 Lapsed work leaves the agenda.** It is absent from the day, week and
  month views, from the undated panel, and - the point of the whole feature -
  from the overdue strip and its count.
- **R137 A list can show what lapsed.** Status becomes three independently
  selectable values rather than two, still defaulting to To do, so a lapsed
  item leaves the default view and can be found deliberately.
- **R138 A chore can be lapsed from the agenda.** From the day's rows and from
  the popup the week view, the month view and the undated panel open - the
  surfaces that already complete a chore (0010 R83, R85).
- **R139 The lapse is visible on the item.** The row says it lapsed rather than
  that it is done, and opening it says when and by whom.
- **R140 A member is told when a row leaves because they lapsed it.** The same
  rule a completion follows (0013 R119, 0010 R87): a row disappearing cannot be
  the only feedback.

### Explicitly not required

- **A reason.** R134 settles it: the note mechanism belongs to completion
  (`adr/0023`), and a second sanitised rich-text field with its own pictures is
  a large change for a sentence nobody is obliged to write.
- **Lapsing automatically.** Nothing lapses because its date passed. The
  recurrence job is already the only thing in the product that acts without
  being asked (`adr/0025`), and a second one that silently retires somebody's
  chores is a different and much larger decision.
- **Lapsing in bulk.** Claiming is a bulk gesture (R46) because it is routine.
  Giving up on nine things at once is not.
- **A per-list status with the owner's own choices.** Still declined, and for
  the reason `adr/0046` declined it. This is one more state the product models,
  not a field a list declares.
- **Reopening from the agenda.** A chore in a terminal state is not on the
  agenda to be reopened, and the record lives on the list (0010's rule,
  unchanged).

## Consequences accepted deliberately

1. **"Finished" and "no longer work" become two different questions**, where
   they used to be one. Every reader that asks whether an item belongs on the
   agenda is asking the second; every reader that asks whether somebody earned
   something is asking the first. `adr/0048` records how the schema forces the
   distinction rather than leaving it to be remembered.
2. **A lapsed chore stops nagging, which is the point and also the risk.** Work
   that genuinely should have been done can be made to disappear by one tap.
   The record of who did it and when is what makes that visible rather than
   silent, and R135 is what makes it reversible.
3. **The row leaves the default view at once**, which is a thing moving on
   screen for a reason one frame old. R140 is the answer, and it is the same
   answer completing already gives.
4. **An item can hold a completion record and a lapse at once.** Complete,
   reopen, lapse is a legal sequence and the completion is kept, because R49
   kept it so the activity feed would not lose history. Such an item is lapsed,
   and the leaderboard must say so.

## Open questions

- **Whether a list's owner can revoke earned points.** Unchanged from 0004;
  R133 settles only the lapse path, as 0005 R49 settled only the reopen one.
- **Whether a lapsed chore should be easy to find across lists**, the way
  overdue work now is. Deliberately not answered: the first version makes
  lapsing possible, and a cross-list "what did we give up on" panel is a
  question about whether anybody looks back.
- **Whether a recurring rule should notice that its occurrences keep lapsing.**
  Something that is lapsed every week is a rule that is wrong, and the product
  knows enough to say so. Out of scope here.

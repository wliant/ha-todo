# 0008 - A navigable My day

Status: accepted
Date: 2026-11-06
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) R44
(three ranges) and R45 (undated items beside all three)
Amends: [0005](0005-completion-detail-recurrence-and-planning-views.md) R44 -
its ranges were *today*, *this week* and *this month*; they are now a day, a
week and a month, any of them.
Answers: [0007](0007-calendar-feeds-on-my-day.md)'s open question about whether
My day should be navigable.

## Problem

My day shows the current period and only the current period. That was a
defensible simplification while the only content was this household's own
chores: a chore nobody has dated yet is on the undated panel, and a chore dated
for next month is something nobody can act on today anyway.

Calendar feeds (0007) removed the defence. **The events on My day are not this
household's to reschedule**, and the questions people actually ask about them
are about other weeks: is anybody free next Saturday, what does the week after
half-term look like, when was that appointment we are trying to remember. A
calendar that can only show the current month is the one calendar in the house
that cannot answer them - and every member already has three that can.

It is also the single most obvious thing missing. A member looking at a month
grid tries to page it; there is nothing to page it with, and nothing explains
why. 0007 predicted this would be the first thing somebody asked for.

**The labels are wrong too, and in the same way.** "Today", "This week" and
"This month" name the *contents* of three tabs, so a tab that can show another
week is a tab whose label is a lie. They name a range: **Day**, **Week**,
**Month**.

## Who uses it

- **A member planning**, usually a few days to a few weeks out: deciding whether
  to agree to something, or working out which weekend is free. They move
  forward, in small steps, and they want to get back to now without counting
  backwards.
- **A member remembering**, going the other way: what happened last Tuesday, who
  did the bins a fortnight ago. Less common, and it needs less range.
- **A member who followed a link**, to a particular day somebody else sent them.
  The period has to be in the address or there is nothing to send.

## Requirements

### Must have

- **R73 The three ranges are a day, a week and a month - any of them.** They are
  named for the range and not for the present: **Day**, **Week**, **Month**. The
  range chosen and the period being looked at are two separate things, and a
  member changing one does not lose the other.
- **R74 A member moves to the period either side.** Previous and next, within
  the chosen range: the day before and after, the week before and after, the
  month before and after. One control each, in one place, and they do the
  obvious thing from the keyboard and from a screen reader - this is the part a
  member will use dozens of times in a sitting.
- **R75 Getting back to now is one action, and it says where it goes.** A single
  control returns to the current period, labelled for the range it will land on
  - *Today*, *This week*, *This month*. It is unavailable when the member is
  already there, so it never reads as a thing that will do nothing.
- **R76 The period being looked at is in the address.** Reloading the page keeps
  it, the browser's back button walks it, and a member can send somebody else a
  link to a particular day. Switching range keeps the period: the week
  containing the day you were on, the month containing that week.
- **R77 A period the product cannot answer for says so.** Events are kept for a
  bounded window either side of today (0007), and beyond it there is nothing to
  show - not because nothing is happening, but because nothing has been read.
  The product says which, rather than drawing an empty grid. An empty week that
  means "we do not know" must never look like an empty week that means "nothing
  is on".

### Explicitly not required

- A date picker, or jumping to an arbitrary month. Stepping is what the use
  cases above need; a picker is a second way to do the same thing, and the one
  people reach for on a phone is the browser's own.
- A year view, or any fourth range. Three is already the most a phone can
  usefully carry.
- Paging the **undated** panel. It is not a period and has no neighbours (R45).
- Changing what each range *shows*. The day view's full rows, the week's seven
  columns and the month's six-by-seven grid are unchanged (0005 R44), as is the
  merge with calendar events (0007 R67).
- Infinite scroll, swipe gestures, or animated transitions between periods. A
  swipe conflicts with the week view's horizontal scroll on a phone, which is a
  real control a member is already using.
- Loading the neighbouring period in advance. A step is one request against a
  cache that already holds what has been visited.
- Keyboard shortcuts beyond the controls being ordinary focusable buttons.

## Consequences accepted deliberately

1. **Two things that used to be the same are now different**: the period a
   member is *looking at*, and the period they are *in*. Anything that means
   "now" - which day is highlighted, what the return control does, whether an
   item is overdue - has to keep asking for the present rather than reading the
   period on screen. That is a mistake this product can now make and could not
   before.
2. **The further forward a member looks, the less true the picture is**, and
   not only because of R77's window. Recurring chores are materialised a few
   days ahead (0005 R42), so next month's bins genuinely do not exist yet. A
   member planning four weeks out sees the events and almost none of the work.
   R77 covers the half the product knows it does not know; this half it cannot
   usefully quantify, and over-explaining it on every view would be worse than
   the gap.
3. **Every step is a request.** Cached per period, so walking back and forth is
   free after the first visit, but a member stepping through twelve months
   makes twelve requests. At household volume that is nothing; it is still a
   change from one request per visit.
4. **A link to a day outlives the day.** R76 means an address sent in March
   still opens March when followed in June, which is right for "look at this
   day" and surprising for a bookmark somebody made of "my day". Accepted: the
   control that returns to now is one tap (R75).

## Open questions

- Whether the week view should start on the day the member is looking at rather
  than on a Monday. Monday is hard-coded today and shared with the month grid;
  a rolling seven days is a different view, not a setting.
- Whether stepping should be bounded at the edge of the events window rather
  than allowed past it with a notice (R77). Bounding is tidier and makes the
  product look like it is hiding something; this prefers the notice, and the
  choice is worth revisiting once somebody has actually walked off the end.
- Whether earned points can be revoked by a list's owner. Unchanged from 0005,
  0006 and 0007.
- Whether a shared list should show who else has it open. Unchanged from 0004
  onwards.

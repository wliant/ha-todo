# 0013 - Filtering and sorting a list

Status: accepted
Date: 2026-11-11
Refines: [0004](0004-lists-items-and-personal-settings.md) - whose R21-R22
declare the fields this document filters on, and
[0005](0005-completion-detail-recurrence-and-planning-views.md) - whose R38 put
a completed item in place rather than in a section, and whose To do / Done / All
toggle this document replaces.

## Problem

A list shows every item it has, in the order somebody put them in, and offers
exactly one way to narrow that: a three-way toggle between To do, Done and All.
Everything else the list knows about an item - who it is for, when it is due,
what each of its costs says - is drawn on the row and cannot be used to find
anything.

That is tolerable at a dozen items and useless at two hundred. The grocery list
of a household that shops weekly, a chore list a recurrence rule has been
filling in every morning for three months, a list where four people each have
their own work: in all three the question a member arrives with is narrower than
"show me everything". *What is mine.* *What is late.* *What can I finish in
five minutes.* *What did nobody pick up.* The product holds the answer to each
one and makes the member read the whole list to find it.

Sorting is the same gap from the other side. A list is in manual order, which is
the order of creation unless somebody has moved things, and manual order is the
right default - it is the only one a member controls. But it is also the only
one on offer, so "what is due soonest" cannot be asked of a list even though
every item on it carries a due date (R40, `adr/0022`).

And the status toggle is the wrong shape for what it does. It is three exclusive
buttons for what is really a two-valued set, so "To do and Done" has to be
spelled "All" - a third word for the absence of a filter - and it sits apart
from every other way of narrowing the list because it was the only one. Once
there are filters, status is one of them.

## Who uses it

- **The member with four people's work on one list.** "Mine" is one tap, and so
  is "nobody's yet".
- **The member with five minutes.** The list declares a cost called Effort with
  choices 5 min / 30 min / 60 min; filtering to 5 min turns the list into the
  part of it they can actually do now.
- **The member catching up.** Late work, soonest first, without reading past
  everything that is not due yet.
- **The member checking what was done.** Done only, newest completion first,
  which today means reading the whole list for the struck-through rows.
- **The member who linked somebody the list.** The address they paste should
  open the same view they were looking at, filter included.
- **The member who ignores all of this.** The list must look and behave exactly
  as it does today until they touch a control.

## Requirements

### Must have

- **R107 A list can be filtered, and the filter is always visible.** The
  controls sit above the items, on one bar, and the bar says how many filters
  are active when any are. A list that is showing a member less than it holds
  must never look like a list that holds that little.
- **R108 Status is a filter, and it is multi-select.** To do and Done are two
  values a member picks any combination of; selecting both, or neither, shows
  everything. The three-way To do / Done / All toggle is gone - "All" was a
  word for an empty filter, and its exclusivity made the one genuinely useful
  combination unreachable. To do alone stays the default, so a member who
  touches nothing sees what they see today.
- **R109 A list can be filtered by assignee, and "unassigned" is one of the
  choices.** Multi-select over the list's members. Work nobody has taken is the
  case this filter exists for as much as "mine" is, and it is not a member, so
  it is offered explicitly rather than inferred from an empty selection.
- **R110 A list can be filtered by each cost it declares.** A cost answered by
  choices is filtered by multi-select over those choices; a cost answered by a
  number is filtered by a range, either bound optional. Each cost field is its
  own filter: a list declaring Effort and Money is filtered by both at once.
- **R111 An item that has not answered a cost is only shown when the member
  asks for it.** "Not answered" is a choice on every cost filter, selectable
  alongside the real ones. An active filter on a cost otherwise hides the items
  that said nothing about it - a blank is not a small amount, and a member
  asking for "5 min" is not asking for "unknown".
- **R112 A list can be filtered by due date.** Overdue, today, the next seven
  days, undated, and an explicit range between two days. Days are the
  household's days (`app.timezone`, `adr/0036`), inclusive of both ends of a
  range, so a member filtering "today" sees a chore due at 09:00 until midnight
  rather than until 09:01.
- **R113 A list can be sorted.** One key at a time - manual order, due date,
  description, status, assignee, or any cost the list declares - in either
  direction. Manual order is the default and is what the list has today.
- **R114 An item with nothing to sort by sorts last, in both directions.** An
  undated item, an unassigned one, one that never answered the cost being
  sorted on: reversing the direction must not bring a crowd of blanks to the
  top of the list.
- **R115 The filter and the sort are in the address.** A member who reloads,
  navigates away and back, or sends somebody the link sees the same view. The
  default view - To do, manual order, nothing else - leaves the address clean.
- **R116 An address that does not parse opens the list unfiltered.** A truncated
  link, a filter naming a field the list no longer declares, a member whose
  grant was revoked: each falls back to the default view and shows the list.
  Nothing here is allowed to be a screen a member cannot get past.
- **R117 A filtered list that matches nothing says so, and says it is
  filtered.** "Nothing matches these filters", with the way to clear them,
  never "Nothing to do here" - which is a confident statement about the list
  itself and is false.
- **R118 Clearing every filter is one action.** Available wherever the member
  is when they want it, including from the empty state R117 shows.
- **R119 Finishing something is confirmed when the row leaves.** With a status
  filter of To do, completing an item takes it out of the view at once. The row
  disappearing cannot be the only feedback, so what happened is said in words -
  the same rule the agenda already follows (0010 R87).

### Should have

- **R120 The bar offers only what the list declares.** No assignee filter on a
  list without the assignee field, no cost filter for a cost it does not have,
  and no sort key for either.
- **R121 Selection follows what is on screen.** The checkboxes select items
  (R46); an item filtered out of view is not selected and cannot be claimed in
  bulk. A member acting on "4 selected" must be acting on four rows they can
  see.
- **R122 The filter bar is usable on a phone.** It is the first thing above a
  list a member is scrolling on a 360px screen, so it collapses to a single
  row of controls that open into menus rather than a panel of permanently
  expanded inputs.

### Out of scope

- **Filtering or sorting on the server.** The items endpoint already returns
  every item of a list with everything this filters on; a household's list is
  not large enough to justify a second implementation of these predicates in
  SQL. See `adr/0044`, which records the list size at which that stops being
  true.
- **A status that is not To do or Done.** Blocked, waiting, in progress: status
  here is the completion the product already models (R34, R49), not a new
  per-list field with choices of its own. See `adr/0046`.
- **Remembering a member's filter between visits.** The address carries it
  (R115); a filter that outlives the session is a list that is mysteriously
  missing items. See `adr/0045`.
- **Sorting by more than one key.** One key and a direction answers every
  question above.
- **Saved filters, named views, or a filter shared with the whole household.**
  A link is already a saved filter.
- **Filtering the agenda, a collection, or the lists index.** The agenda has its
  own scope control (0008) and collections have search (0006).
- **Filtering on the detail field's text.** Searching an item's rich text is a
  different feature with a different mechanism - see 0006, where it was built
  for entries.

## Open questions

- Whether a list should be able to declare its own default sort, so a list that
  is always worked in due-date order opens that way for everybody.
- Whether the filter bar should say how many items it is hiding, not only how
  many filters are on.
- Whether the activity and recurring tabs want any of this. Neither has a
  filter today and neither has asked for one.

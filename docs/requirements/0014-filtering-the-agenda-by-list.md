# 0014 - Filtering the agenda by list

Status: accepted
Date: 2026-11-11
Refines: [0008](0008-a-navigable-agenda.md) - whose R73-R78 made the agenda a
thing a member moves around in, and
[0011](0011-overdue-work-on-the-agenda.md) - whose overdue strip is the third
cross-list read this document has to narrow alongside the period and the
undated panel.

## Problem

The agenda reads across every list a member can see, and that is the whole
reason it exists: a household's Thursday is one thing, not one thing per list.
But it offers exactly one way to narrow what it shows - the Everything / Mine
toggle, which asks about a person.

A household of four with a grocery list, a chore list, a workout log and a
garden list gets all four interleaved in one Day column, in each of the week
grid's seven, in each of the month grid's forty-two, and in the overdue strip.
Every row names the list it came from, so the information is there; finding the
four rows that belong to one list means reading forty.

The question that cannot be asked is a small and ordinary one. *What does the
grocery list owe me today.* *Is anything from the garden late.* *What is on the
chore list this month.* A member can get part of the way by opening the list
itself - 0013 made a list filterable and sortable, and it answers all three
within its own rows. What a list cannot do is what the agenda is for: put the
answer in a week grid, beside a month, next to the appointments the household
has already committed to. A chore is not planned against its list, it is planned
against Thursday evening.

The Mine toggle is also not a substitute, and it is worth being clear why. Most
items have no assignee - the field is optional and defaults to not required
(`adr/0022`) - so "Mine" is empty on exactly the lists this product is about.
Narrowing by list is the dimension every item actually carries.

## Who uses it

- **The member doing one thing.** They are going to the shops. They want the
  grocery list's chores on today and this week, and nothing else, without
  losing the fact that there is a dentist appointment at four.
- **The member who owns a list nobody else touches.** A workout log on a shared
  agenda is noise to three people and the whole point to one. Both of them can
  now have the agenda they want out of the same data.
- **The member catching up.** Twenty-two things are overdue across five lists.
  The strip is a wall. One list at a time is a plan.
- **The member who never touches it.** The agenda opens exactly as it did:
  every list, no filter, nothing extra in the address.

## Requirements

### Must have

- **R123 The agenda can be narrowed to a chosen set of lists.** Several at
  once, chosen from every list the member can see - not one at a time, because
  "the two food lists" is as ordinary a question as "the grocery list", and not
  from a shortened set, because a filter that cannot offer a list is a filter
  that hides work without saying so.
- **R124 Choosing no list means every list, and the control is always on
  screen.** An empty selection is an absent filter, the same rule the list's
  bar follows (`adr/0046`), and the way back to the whole agenda is one tap and
  is named rather than being the absence of ticks. The control is drawn before
  the lists have loaded and when nothing is chosen, so an agenda showing less
  than it holds can never look like an agenda that holds that little.
- **R125 The filter reaches every chore on the agenda.** The Day, Week and
  Month views, the overdue strip and the undated panel. A surface left
  unfiltered contradicts the chip on screen, and the overdue strip is the one
  that would hurt: a member narrowing to the garden list does not want to be
  told about the kitchen's arrears.
- **R126 Calendar events are never hidden by it.** An event belongs to a
  calendar feed and a member, not to a list (`0007`), so it has no list to be
  narrowed by. Hiding somebody's appointments because they asked to see one
  list would take the half of the agenda they did not filter.
- **R127 An agenda emptied by the filter says that is why.** A day holding four
  chores from other lists must not read "nothing is due". The empty states name
  the filter where there are words to name it in - the week and month grids keep
  their empty cells, where the chip's own filled label and count is the cue.
- **R128 A filter naming a list that is gone shows the whole agenda.** A link
  that outlives a deleted list, or a member whose grant was revoked, degrades
  to the unfiltered agenda rather than to an empty one. This is the landing
  page: a stale address must never be a member who cannot see their day.

### Should have

- **R129 The control names what is picked, not just that something is.** One
  list and the chip reads that list's name; several and it reads the count. The
  colour is never the only cue.
- **R130 Changing the filter costs no request.** Ticking a list re-draws what
  is already on screen. A member deciding what to look at should not be waiting
  on a network, and an agenda that refetches per combination of lists is an
  agenda that is slowest exactly when somebody is exploring it.

### Out of scope

- **Filtering by calendar feed.** The agenda merges two dimensions and this
  document narrows one of them. A feed filter is a real and separate feature
  with its own control; it is not what "filter by list" means. See `adr/0047`.
- **Remembering the filter between visits.** It lives in the address, so a
  narrowed agenda is a link somebody can keep or send, and the landing page is
  the whole agenda every time. The same rule `?on=` follows (`adr/0037`,
  `adr/0045`).
- **Collections.** A data list has no dated entries and never appears on the
  agenda at all (`adr/0029`), so there is nothing here for it to narrow.
- **Narrowing the server's read.** The agenda filters in the browser, over the
  payload it already fetched. See `adr/0047`, which also records the day that
  stops being true.

## Open questions

- Whether the two narrowing controls should combine into one bar once there is
  a third, rather than a toggle group and a chip sitting side by side.
- Whether a member who lives in one list would be better served by that list's
  own agenda - a week grid on the list screen - than by filtering the household
  one down to it every time.

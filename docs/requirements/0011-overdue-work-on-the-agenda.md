# 0011 - Overdue work on the agenda

Status: accepted
Date: 2026-11-07
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) - whose
dashboard (R44, R45) buckets work by the day it is due, and
[0008](0008-a-navigable-agenda.md) - which made that surface navigable and named
the thing this document fixes.

## Problem

The agenda answers "what is left", and today it answers it only for the period
on screen. The ranged query is half-open on the window the client asked for, so
a chore due last Tuesday exists in last Tuesday's window and **nowhere else**. A
member who missed it sees no sign of it: not on today, not in the undated panel
(which is `dueAt IS NULL`, and an overdue chore has a date), not in any count.
It is reachable only by navigating back to the day it was due, which nobody
does, because nothing tells them there is anything back there.

So the one failure mode a shared household list exists to catch - work that was
due and did not happen - is the one the landing page hides. Worse than hiding it,
the agenda is *reassuring*: an empty today reads as "nothing is on" when it may
mean "three things were on and are now behind you".

`0008` already saw this coming. Its first accepted consequence lists "whether an
item is overdue" among the things that must keep asking for the present rather
than reading the period on screen. Nothing was ever built that asks.

## Who uses it

- **The member who opens the app in the morning.** They want today, and they
  want to know in the same glance whether yesterday left anything behind. They
  will not go looking.
- **The member catching up after a few days away.** Four days of missed chores
  are spread across four windows they would have to visit one at a time.
- **The member who dated something optimistically.** "Fix the shelf, Sunday" was
  three Sundays ago. Nothing in the product has mentioned it since.
- **Nobody planning ahead.** A member looking at March is not asking about
  overdue work, and must not be made to.

## Requirements

### Must have

- **R88 The agenda says when work is late.** Wherever a member is standing in
  it, the agenda shows that there is overdue work and how much, before it draws
  the period. An empty day must never read as a clear day when it is not.
- **R89 Late is reckoned from today, not from the period on screen.** The set of
  overdue chores is the same whether the member is looking at this week or at
  February. Navigating does not change what is late; only time and work do.
- **R90 A member can see which chores are late and act on them there.** The
  overdue work is readable in place - what it is, which list it came from, and
  how long ago it was due - and carries the same completion gesture the agenda's
  own rows carry (0010 R83, R84), so a member can clear it without going
  anywhere.
- **R91 Late means a day has passed, not a minute.** A chore due at nine this
  morning is today's work until today is over. It stays in today's row and is
  never simultaneously drawn as overdue.
- **R92 It is honest about how much it is not showing.** The household's first
  sight of this will be a long list, so what is on screen is capped and the real
  total is stated, as the undated panel's is (0005 R45).
- **R93 It is scoped like the rest of the agenda.** "Everything" and "Mine"
  apply to overdue work too, and only lists the member has access to are read -
  the same guard, in the same place, as every other cross-list read (`adr/0010`).

### Explicitly not required

- **Changing an item's due date, or carrying it forward.** The product does not
  silently redate a member's work; "it was due Sunday" is information, and a
  chore that quietly becomes due today has lost it. Rescheduling is an edit, and
  edits live on the list.
- **Overdue calendar events.** An event is not work and is never late (0007
  R63). This reads items only.
- **A notification, a badge on the installed icon, or an email.** Nothing in this
  product pushes, and the agenda is the landing page - a member sees this when
  they open the app, which is the moment it is useful.
- **A fourth range tab.** The tabs are periods. Overdue is not a period, it has
  no neighbours, and `?on=` would mean nothing for it.
- **Hiding or snoozing an item from the overdue set.** A way to make late work
  stop being mentioned is a way to make this feature pointless. The way out is
  to do it, or to change its date on the list.
- **Overdue anywhere but the agenda.** A list already shows its own items with
  their dates, in its own order.

## Consequences accepted deliberately

1. **The agenda grows a surface that is not about the period on screen**, and
   therefore does not move when the member navigates. That is R89 working, and
   it will look briefly wrong to anybody who expects every pixel of a dated view
   to belong to the date.
2. **A member standing on a past day can see the same chore twice** - once in
   the grid, where it is genuinely due, and once as overdue. Accepted: the
   alternative is an overdue set that changes as you navigate, which is R89
   inverted and a worse lie than a duplicate.
3. **The first load after this ships will be unflattering.** Every item ever
   dated and never finished arrives at once. R92 is what keeps that honest
   rather than endless, and the list shortens by being worked, which is the
   point.
4. **This is a surface a member cannot turn off.** Deliberate, per the
   non-requirement above, and the reason the collapsed form has to be small
   enough to live above every view without being in the way.

# 0021 - An agenda that keeps itself current

Status: accepted
Date: 2026-11-19
Refines: [0005](0005-completion-detail-recurrence-and-planning-views.md) - whose
R44 made the dashboard read across every list a member can see, and
[0008](0008-a-navigable-agenda.md) - which made the period on screen something a
member chooses.

## Problem

The agenda is the landing page, and it is **stale from the moment it is drawn**.

There is exactly one thing in the product that makes it less so: the browser
refetches when the tab regains focus. Everything else is a consequence of the
member's own act - every write from the agenda invalidates what it changed. So a
member looking at today, who has not touched anything and has not switched away
and back, is looking at the household as it was when the page loaded. Ten
minutes ago. An hour ago. Since the app is installed as a PWA and lives in the
foreground on a phone, often: since this morning.

That is the wrong behaviour for this product specifically. The premise of
`ha-todo` is several people touching the same lists at once -
[0001](0001-shared-household-todo.md) says to design every surface around that
and not around a single-user todo app. The agenda is the one surface whose whole
job is "what does this household owe today", and it is the one surface that
cannot notice the household answering.

The recurrence job makes it worse in a way nobody can even attribute. It creates
chores on a cron, with no member involved
([0005](0005-completion-detail-recurrence-and-planning-views.md) R42). An agenda
left open overnight is simply wrong by morning, and gives no sign of it - there
is nothing on screen that distinguishes "nothing is on" from "I have not looked
since before the things were added".

And there is a second, smaller problem hiding inside the first. Even once the
agenda refreshes itself, a row appearing silently under somebody's thumb is not
the same as being told. A phone screen shows a handful of rows; a chore added to
next Tuesday, on a surface showing today, arrives with no trace at all. Refresh
answers "is this current"; it does not answer "did something just happen".

## Who uses it

- **The member with the agenda open on the kitchen counter.** The tablet shows
  today. Somebody adds "collect the parcel" while they are standing there. They
  should not have to know to pull down.
- **The member who was just asked to do something.** Their partner adds it to a
  shared list from another room and says "I've put it on the list". The list is
  on their screen. It should be there.
- **The member who wrote the standing instructions.** The job runs at 01:05 and
  fills the week. They are the person most likely to have the agenda open, and -
  on any naive reading of "don't tell me about my own additions" - they are the
  one person who would never be told, because the rule carries their name.
- **The member on a private list.** They must never learn that anything happened
  on a list they hold no grant on, and nothing about this feature may become a
  second way to find out.

## Requirements

### Must have

- **R179 Work added by somebody else appears on an open agenda by itself** - no
  reload, no navigation, no pull-to-refresh, no control to press. Within seconds
  rather than minutes.
- **R180 The agenda says what arrived, by name.** A message naming the chore and
  the list it went on, not a count and not a silent change. Several arriving at
  once are each named (R184).
- **R181 It stays quiet about the member's own additions.** The add form, the
  duplicate form and the share target already confirm what they did; a second
  message for the same act is noise, and on a second device of the member's own
  the *refresh* must still happen even though the message does not.
- **R182 It announces the work the standing instructions created** - including,
  and especially, to the member who wrote the instruction. Nobody *added* those
  chores; the job did.
- **R183 A member is only ever told about work on a list they hold a grant on.**
  This is the same rule every read in the product follows
  ([adr/0010](../adr/0010-per-list-access-instead-of-household-scoping.md)), and
  it is not weakened by the message being small: a chore's description is list
  content.
- **R184 Several chores arriving at once are named, then counted.** A handful are
  each named; beyond that the remainder is a number. The 01:05 run must not
  produce a queue somebody has to sit through.
- **R185 A message about somebody else's chore never displaces the confirmation
  of something the member just did.** Completing, lapsing, saving an edit and a
  refused write all say so, and the row vanishing is otherwise the only feedback
  ([adr/0039](../adr/0039-the-agenda-writes-through-the-items-own-endpoint.md)).
- **R186 If the connection cannot be made, the agenda still brings itself up to
  date.** R179 must not depend on infrastructure outside this repository. What
  may be lost in that case is R180 - the naming - and nothing else.
- **R187 None of this is a second way to read the household's work.** The agenda
  refreshes by asking the endpoints it already asks, which check the grant the
  way they already do. Nothing is drawn from what arrives.

### Explicitly not required

- **Being told about work that was finished, lapsed, changed or deleted by
  somebody else.** The requirement is that new work appears. A chore another
  member completes stays on screen until the next refresh, as it does today.
  This is a deliberate first cut, not an oversight - but it is the obvious next
  ask, so the mechanism should be able to carry it without being rebuilt.
- **The same behaviour on a list screen.** A list is opened to be worked on
  rather than watched, and the member is usually the one changing it. The agenda
  is the surface somebody *leaves open*.
- **Telling a member only about chores on the period, the scope or the lists
  currently on screen.** A chore added for next month would then arrive with no
  sign at all, which is the failure this feature exists to fix. The message says
  something was added; it does not promise it is visible.
- **A notification when the app is closed.** Push notifications to a device are a
  different feature with a different permission model and a different failure
  mode. The requirement is about an agenda somebody is looking at.
- **A record of what arrived.** There is no feed, no unread count and no badge.
  The message is transient and so is the state behind it.

## Consequences accepted deliberately

1. **The product acquires a connection that stays open.** Until now every
   exchange was a request and a response. One open connection per open agenda is
   new, and it is held by that surface alone.
2. **A second backend instance becomes more expensive.** It already breaks
   sessions ([adr/0003](../adr/0003-defer-redis-and-minio.md)) and double-runs the
   recurrence job
   ([adr/0025](../adr/0025-recurrence-is-materialised-by-a-scheduled-job-on-one-instance.md)).
   It would now also deliver to *half* the household, silently. Sessions still
   break first, so this is not what blocks a second instance; it raises the cost
   of one.
3. **The message's promptness depends on infrastructure this repository does not
   contain.** Both environments sit behind an HTTPS front door configured
   elsewhere. R186 exists because of this, and the fallback it requires is a
   genuine behaviour rather than a safety net nobody ever exercises.
4. **A chore's description now travels on a channel of its own.** R183 is
   therefore not a UI rule but a routing rule, and the thing being routed must
   stay small enough that its leak would be an embarrassment rather than a
   disclosure.

## Open questions

- **Whether completion should be announced too.** It is the obvious second
  event, and the argument against is only that "Ana finished the bins" on a
  surface that then removes the row may be more noise than information.
- **Whether a list screen should be live.** The threshold is somebody asking,
  and at that point the mechanism moves rather than being copied.
- **Whether the agenda should show that it is connected.** Today the member
  cannot tell a quiet household from a dead connection, and R186's fallback
  makes that distinction less important than it would otherwise be - but not
  nonexistent.

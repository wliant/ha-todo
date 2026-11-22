# 0022 - Home Assistant acts as a household member

Status: accepted
Date: 2026-11-20
Refines: [0001](0001-shared-household-todo.md) - whose premise is several people
touching the same lists at once, and
[0002](0002-accounts-and-administration.md) - whose accounts this adds a
non-human one to.

## Problem

Everything this product knows is reachable **only through a browser**.

A household keeps three kinds of thing here now: lists of work, collections of
things it looks up, and trackers of numbers it records over months. Every one of
them is written by somebody holding a phone, deliberately - the whole product is
a few screens designed for a thumb.

Meanwhile the same household already runs an automation system, and that system
is where the physical facts of the house arrive. A voice assistant hears
"remind me to buy milk" at the kitchen counter, with wet hands and no phone. A
scale in the bathroom knows a number somebody would otherwise type in twice a
week and stop typing by March. A button by the door knows the bins went out. A
calendar automation knows the rubbish collection changed this week.

None of it can reach a list. The gap is not that the product lacks a feature -
every one of those acts is an ordinary item, entry or reading that the product
already models exactly. The gap is that the only way in is a session cookie and
a React bundle, and a doorbell cannot hold either.

So the household does the translation by hand. Somebody hears the voice
assistant's own list read back, and copies it into this one. Somebody reads the
scale and types the number. The automation and the household's actual record of
itself sit side by side, each half right, and the one that is wrong is always
the one nobody opened today.

There is a second problem underneath, and it is the one that makes this worth
designing rather than scripting. A household member who wires this up with a
shell script and a hard-coded cookie has given their automation system **the
run of the house**: every list, every private log, every number. The product
has had a precise answer to "who may see this" since
[adr/0010](../adr/0010-per-list-access-instead-of-household-scoping.md) - a
grant, per list, given by its owner - and a script with somebody's credentials
goes around it. Whatever reaches in from the automation system has to be bound
by the same rule as everybody else, or the rule stops meaning anything.

## Who uses it

- **The member at the kitchen counter with wet hands.** They say "add milk to
  the groceries list" to the speaker that is already there, and it lands on the
  groceries list - the real one, the one their partner will read in the shop.
  They do not learn a new phrase, a new app or a new list.
- **The member who weighs themselves.** The scale records the number. They
  never type it, and six months later the chart has six months in it rather
  than the three weeks their enthusiasm lasted.
- **The member who owns a private list and does not want the robot in it.**
  They share nothing with the automation account, and nothing about it appears
  in the automation system. This is not a setting they have to find; it is the
  absence of a grant they never gave.
- **The member who wired it up.** They set it up once, by picking the product
  from a list of integrations and typing a name, a password and an address. They
  are not editing YAML, and they are not holding an admin credential to do it.
- **The household on the morning the backend was redeployed.** Nobody notices.
  The automation signed itself back in.

## Requirements

### Must have

- **R188 Home Assistant acts as an ordinary household member**, with a username,
  a password and an account the admin created the same way as everybody else's.
  It is not a special principal, it holds no role, and it gains nothing by being
  a machine. Deliberately so: the alternative is a second authorization story
  running beside the real one, and the second one is always the one nobody
  audits.
- **R189 It reaches only what has been shared with it**, by the owner of each
  list, collection and tracker, through the access screens they already use. The
  rule is
  [adr/0010](../adr/0010-per-list-access-instead-of-household-scoping.md)'s and
  it is not weakened, extended or special-cased - a thing nobody shared is a
  thing the automation system cannot see, name or discover the existence of.
- **R190 A shared list appears in Home Assistant as a to-do list**, in whatever
  reads to-do lists there - the voice assistant, the dashboard, an automation.
  Not as a custom thing requiring custom handling: the household's existing
  speaker should work against it on the day it is installed, with no phrase
  written by hand.
- **R191 It can add a chore to a shared list.** A description, and a due date if
  one was spoken. That is the act the whole feature exists for.
- **R192 It can mark a chore on a shared list complete**, and the points go to
  the account that did it, which is the automation - because that is true.
  Completion is the product's existing one: the record, the credit and the
  activity feed all behave as they do for anybody else.
- **R193 A chore the product will not accept is refused in the product's own
  words.** A list may insist on an assignee, a detail or a cost, and Home
  Assistant has none of those to offer; the refusal says which, in the sentence
  the product already shows a member. A generic failure against a server the
  member cannot browse from where they are standing is undebuggable.
- **R194 It can add an entry to a shared collection** - a name, a description
  and tags. The link somebody shares, the recipe an automation clipped.
- **R195 It can record a reading on a shared tracker**, into the bucket the
  **server** decides, not one the automation calculated. A number arriving from
  a machine twice a day is the case trackers were worth building for.
- **R196 It reads a shared tracker's current figure, and says nothing when
  nothing was recorded.** An empty bucket is unknown, never zero: a missed
  weigh-in is not 0 kg, and a number that reads as a crash is worse than no
  number ([adr/0051](../adr/0051-a-tracker-is-its-own-aggregate.md) - a unit
  here is a label and never a conversion, so nothing may rescale it either).
- **R197 It says how much work is overdue**, as the one figure an automation
  could act on - a light, a reminder at six, a nudge on the tablet. It is the
  count the product counts, not the length of a page of it.
- **R198 A dated chore means the household's day.** The household has one
  timezone and the server serves it
  ([adr/0036](../adr/0036-the-household-has-one-timezone.md)); an automation
  system configured for somewhere else must not be able to file a chore on the
  wrong day. A date with no time means the **end** of that day, as it does
  everywhere else in the product.
- **R199 It never rewrites a chore's detail.** The detail field is rich text
  with pictures in it, written by a member on a real screen. Nothing reaching in
  from the automation system may replace it, and that includes replacing it as a
  side effect of changing something else.
- **R200 Losing its session is not an error.** Sessions are in memory
  ([adr/0003](../adr/0003-defer-redis-and-minio.md)), so every deploy signs the
  automation out; it signs itself back in and says nothing. A household that
  gets a broken-integration notice every time somebody deploys will turn the
  notice off, and then it will miss the real one.
- **R201 It deletes nothing.** Not an item, not an entry, not a reading, not a
  list. Everything else here is recoverable by hand in a few taps; a deletion is
  not, and a misheard word should not be able to reach it.

### Explicitly not required

- **Reopening or lapsing a chore.** Both are judgements about work somebody
  meant to do - a lapse in particular is a member deciding nobody is going to
  get to it ([0015](0015-lapsing-work-nobody-got-to.md)), which is not a thing a
  motion sensor knows. Completing is the only state change with an unambiguous
  physical fact behind it.
- **Assigning a chore to a member.** An automation does not know who is going
  to do it, and guessing produces a chore somebody else now owns without being
  asked. A list that *insists* on an assignee is therefore a list Home Assistant
  cannot add to, which R193 is how the member finds out.
- **Answering a cost.** Same reason, plus points: a wrong cost is credited
  points somebody did not earn, on a leaderboard other people read.
- **Searching a collection.** Collections are worth keeping because of the
  search ([0006](0006-data-lists-and-entry-search.md)), and that search is a
  screen with a text box. Writing an entry is the half an automation has.
- **Editing anything it wrote.** Correcting a reading or an entry is a screen
  somebody is looking at. An automation that can rewrite its own history makes
  a chart nobody can trust.
- **Being told about changes without asking.** The product has a push channel
  already ([0021](0021-an-agenda-that-keeps-itself-current.md)) and this does
  not use it. Asking on an interval is enough for an automation; the open
  question below is whether that stays true.
- **Appearing as a calendar, an agenda or a dashboard.** The automation system
  has its own surfaces for those and the product's agenda is a designed thing
  ([0008](0008-a-navigable-agenda.md)). Re-drawing it badly somewhere else is a
  second product.
- **Being installed, distributed or deployed by this repository's scripts.**
  That is a separate decision with its own packaging and its own release story,
  deferred deliberately.

## Consequences accepted deliberately

1. **The household's sign-in screen gains somebody who is not a person.** The
   roster is public and says only who exists
   ([adr/0009](../adr/0009-opaque-sign-in-roster.md)), so an account named for
   the automation sits in the list of faces a member picks from, and in every
   assignee picker on every list shared with it. It is the honest consequence of
   R188: an ordinary member is an ordinary member everywhere. Hiding it would
   mean a second kind of account, which is the thing R188 refuses.
2. **The automation system holds a real password for a real member.** Whoever
   can read that configuration can sign in as the automation and reach
   everything shared with it. That is strictly less than any alternative a
   member would otherwise improvise, and strictly more than nothing;
   `requirements/0002`'s security posture already says reaching the deployment
   is the control.
3. **Revoking the automation's reach means changing its password or deleting its
   account.** There is nothing finer-grained to revoke, because there is no
   token - which is also what makes it simple. Deleting the account is the big
   hammer and it behaves correctly: the grants go with it.
4. **A tracker kept per member can only ever be written as the automation's own
   series.** Which series a reading lands in is derived from the tracker and the
   caller, deliberately, so there is no parameter to tamper with
   ([adr/0052](../adr/0052-a-reading-is-a-number-in-a-declared-bucket.md)). A
   scale cannot file a number under Ana. The modelling that works is a shared
   tracker per person, and the household has to be told that, because the
   obvious arrangement is the one that disappoints.
5. **A chore somebody lapsed disappears from the automation system with no
   trace.** There are two terminal states here and only one there, and neither
   of the two available answers is true - so it leaves, the way it leaves every
   planning surface in the product.
6. **The product acquires a client nobody in this repository can see.** Until
   now the only consumer of the API was the SPA in the next directory, changed
   in the same commit. From here a response shape is somebody else's running
   system, and a field removed is a household whose bins stop being recorded.

## Open questions

- **Whether a reading should ever be able to name its member.** Consequence 4 is
  the sharpest limitation here, and the fix is a product decision rather than an
  integration one: it would mean a reading carrying a member id, which
  `adr/0052` refused on purpose. The threshold is somebody actually wiring up a
  scale and finding the answer unacceptable.
- **Whether the automation should be told rather than ask.** `/api/live/stream`
  exists, carries almost nothing on purpose, and is exactly the doorbell an
  interval poll is a worse version of. The argument for waiting is that an
  automation has no eyes and a minute is not a long time to it.
- **Whether a machine account should be hidden from the roster and the assignee
  pickers.** It is consequence 1, and the counter-argument is that the moment
  there is a second kind of account there is a second set of rules about it.
- **Whether a password should be replaced by something revocable.** This is the
  question behind consequences 2 and 3 and it is really "is there a second
  machine client". With one, a password is the cheapest honest thing.

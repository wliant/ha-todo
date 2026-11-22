# 0023 - A list as data rather than a to-do list

Status: accepted
Date: 2026-11-22
Refines: [0022](0022-home-assistant-acts-as-a-household-member.md) - whose R190
made a shared list a native to-do list, which is the one decision this reverses.

## Problem

0022 R190 asked that a shared list appear in Home Assistant as a to-do list,
and it does. The argument was that the household's voice assistant, dashboard
cards and automation blueprints already speak to that shape, so the integration
would work on the day it was installed with nothing configured.

Deployed and looked at, the shape is the problem. A to-do item in Home
Assistant has four fields - a description, a status, a due date and a note. A
chore here has an assignee, a due date, answered costs, points credited to
whoever did the work, a completion note, a rich-text detail, and **three**
states. So what reached the automation system was a description and a date, and
everything that makes a chore worth looking at was dropped on the floor without
a word.

The third state is the sharpest of it. Because the platform has two statuses
and a lapse is neither of them,
[adr/0060](../adr/0060-a-lapse-has-no-status-to-map-to-so-it-leaves.md) decided
a lapsed chore would simply vanish - which was the least wrong of three wrong
options, and is still a chore disappearing with no explanation.

And there is a second thing underneath, which is about who draws the screen. A
native entity comes with a card, and that card can tick off the household's
real chores. For a household that intends to build its own view - a tablet on
the kitchen wall, a template card, something shaped like its own week - the
native shape is not a head start. It is a constraint plus a surface nobody
asked for.

## Who uses it

- **The member building the kitchen tablet view.** They want the chore, all of
  it: who it is for, when it is due, what it is worth, whether anybody got to
  it. They will lay it out themselves and they do not want a stock card
  deciding what a chore looks like.
- **The member writing an automation about effort.** "If more than three chores
  on the cleaning list are overdue, turn the hall light amber." That needs
  counts and dates, not a list of strings.
- **The member who gave up on a chore.** They lapse it in the product, and the
  automation system says it was lapsed rather than silently losing it.
- **The member who liked saying "add milk to the shopping list".** They lose
  that, knowingly, and that loss is recorded here rather than discovered.

## Requirements

### Must have

- **R202 A shared list appears as data, not as a to-do list.** One sensor per
  list, whose value is the number of open chores, and whose attributes carry
  the list. There is no `todo` entity, and the household draws its own view
  ([adr/0062](../adr/0062-a-list-is-a-rich-sensor-the-households-own-ui-reads.md)).
- **R203 A published chore carries everything except its rich text.** Its
  state, its due date, who it is assigned to, what it earned, who finished or
  lapsed it and when, and its answered costs. Not its detail and not its
  completion note: those are sanitized HTML with images in them, and 0022 R199
  and
  [adr/0061](../adr/0061-the-detail-field-is-deliberately-not-round-tripped.md)
  are unchanged - only *whether there is a detail* is published, so a screen
  can offer to go and read it.
- **R204 A lapse is named rather than hidden.** `state` is one of three values.
  A chore nobody got to says so, which is the whole reason the product grew a
  second terminal state.
- **R205 An answered cost is published with the label its list gave it.** A
  field id and a choice id are correct and useless to anything drawing a
  screen, so the list's declared fields are read and the cost is resolved
  against them. The declared fields are published too, so a form can be drawn
  without asking a second question.
- **R206 Every change to a chore is an explicit action, aimed at a list the
  member picks.** Adding, completing, lapsing and reopening are named actions
  that do one thing each, and each targets the list's own entity rather than
  taking a list name typed from memory - a household's lists cannot be
  enumerated in a static service description, but they can be targeted.
  A chore is then named by its description within that list, or by its id when
  two share one. Nothing about a list changes as a side effect of a card being
  tapped, because there is no card the product supplied.
- **R207 A chore can be lapsed and reopened from the automation system**, which
  0022 explicitly did not require. Both become available because the state is
  now representable; the reasoning that they are judgements rather than
  physical facts still stands, which is why they are actions somebody calls
  deliberately and never anything inferred.
- **R208 A truncated list says that it is truncated.** The published chores are
  bounded, because the whole payload is pushed to every connected screen every
  time it changes. A list that silently shows some of itself is worse than one
  that admits the cut.
- **R209 Voice for lists is given up, and the cost is written down.** Home
  Assistant's built-in list intent speaks only to to-do entities, so "add milk
  to the shopping list" stops working until somebody writes sentences of their
  own over R206's actions. This is a deliberate trade, not an oversight, and
  nothing here is designed to make it look smaller than it is.

### Explicitly not required

- **A view of a list that the product supplies.** That is the point: the
  household draws it. There is no card, no panel and no default dashboard, and
  adding one later would be a new decision rather than a completion of this
  one.
- **Writing an assignee or a cost.** Still refused, for 0022's reasons: an
  automation does not know who is going to do a chore, and a wrong cost credits
  points somebody did not earn on a leaderboard other people read. Worth
  revisiting once a real screen exists and a person is doing the choosing.
- **Custom voice sentences.** R209 records the loss; replacing it is somebody's
  own automation work and not part of this.
- **Deleting a chore.** 0022 R201 stands.
- **History of a list's shape over time.** The sensors are meant to be excluded
  from the recorder, and the counts are deliberately not statistics.

## Consequences accepted deliberately

1. **There is nothing to look at until somebody builds it.** Installing this
   now yields a sensor whose state is a number. Everything legible comes from a
   template card, a markdown card or a custom card the household writes.
2. **The spoken path is gone.** Consequence of R209, and the thing most likely
   to be missed first, because it was the scenario 0022 led with.
3. **The published payload is pushed on every poll.** It is capped and it
   excludes rich text for exactly this reason, and the cap is a bound rather
   than a page.
4. **Excluding these sensors from the recorder is an instruction, not a
   mechanism.** Past 16KB of attributes the recorder stores nothing and only
   logs about it, so a list that grows produces empty history and no error. The
   integration cannot enforce the exclusion; the README asks for it.
5. **An item id is part of the contract now.** Actions take one, read from a
   sensor's attributes. A version deliberately is not published, so a caller
   cannot hand back a stale one.
6. **Removing a platform leaves its entity behind.** Home Assistant keeps the
   registry row, so the upgrade left an unavailable `todo.*` entity that had to
   be deleted by hand.

## Open questions

- **Whether a WebSocket command should replace the attributes.** It has no size
  limit and no recorder interaction, and it is the right shape for a bespoke
  card. The attribute budget becoming the binding constraint is the threshold.
- **Whether the assignee should be writable** once there is a real screen with
  a person in front of it, which is a different situation from an automation
  guessing.
- **Whether the product should supply one reference card** after all - not as a
  default, but as something to copy.

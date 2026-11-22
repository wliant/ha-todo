# 0059 - A list is a to-do entity and the other two kinds are services

Status: superseded by [0062](0062-a-list-is-a-rich-sensor-the-households-own-ui-reads.md) for the list half only - the
decision that collections and trackers are services and sensors stands and is
unaffected.
Date: 2026-11-20

## Context

[requirements/0022](../requirements/0022-home-assistant-acts-as-a-household-member.md)
R190-R197 ask for four writes and two reads across the product's **three**
kinds: lists of work, collections of reference entries, and trackers of
numbers. Each is its own aggregate with its own grant
([0029](0029-a-data-list-is-its-own-aggregate.md),
[0051](0051-a-tracker-is-its-own-aggregate.md)).

Home Assistant models things as *entities* on *platforms*. It has a `todo`
platform whose `TodoListEntity` is close to a perfect fit for a list of work -
and the fit matters far more than it looks, because the household's existing
voice assistant, dashboard cards and automation blueprints already speak to
`todo` entities. An integration that invents its own shape gets none of that
and every household has to write phrases by hand.

It has no platform for "a reference entry" and none for "append a number to a
bucketed series". It has `sensor` for a reading, `number` for a settable value,
and plain *services* for anything that is an action rather than a state.

## Decision

**A shared list is a `TodoListEntity`.** One entity per list, so the voice
assistant, the dashboard and every blueprint work on the day it is installed.

**A shared tracker is a `sensor`, and writing to it is a service.** The current
bucket's rollup is state and reads naturally as a sensor; recording a reading is
an append and is `ha_todo.record_tracker_reading`. A `PER_MEMBER` tracker gets
**one sensor per series**, because "the tracker's value" is undefined when four
people each have one.

**A collection is neither.** Adding an entry is `ha_todo.add_collection_entry`
and there is no entity at all.

Both services resolve their target by **name, case-insensitively, or by UUID**,
and an unknown or ambiguous name fails naming the ones that are shared.

## Consequences

- **R190 comes free and is the feature's whole value.** "Add milk to the
  groceries list" works through the household's existing speaker with nothing
  configured, because the speaker already knows what a `todo` entity is.
- **The to-do platform's vocabulary is now a constraint on this integration.**
  Its item has a summary, a status, a due date and a description, and that is
  all - which is what forces [0060](0060-a-lapse-has-no-status-to-map-to-so-it-leaves.md)
  and [0061](0061-the-detail-field-is-deliberately-not-round-tripped.md) to be
  decisions rather than details. Assignees, costs and points have nowhere to go
  and are simply absent.
- **A collection is write-only from Home Assistant**, and deliberately: the
  thing that makes collections worth keeping is the search
  ([0006](../requirements/0006-data-lists-and-entry-search.md)), which is a
  screen with a text box, not an entity's state.
- **Resolving a target by name is a worse contract than an `entity_id`.** Home
  Assistant cannot offer a picker for something that is not an entity, so a
  service field is free text, and two collections with the same name - possible,
  since the uniqueness is per owner - are ambiguous. The failure message naming
  what *is* shared is what keeps that debuggable from inside Home Assistant,
  where the member cannot browse the product.
- **A tracker kept per member yields several sensors with no obvious ordering**,
  named for members. Correct, and more entities than a household might expect.

## Alternatives considered

- **A collection as a `TodoListEntity` too.** The platform would accept it and
  the entries would look like items. Rejected: an entry is *never* completed,
  assigned, dated or scored - that is the whole of `adr/0029` - and the first
  thing anybody would do with a to-do entity is tick one off. A collection that
  can be ticked off is a different product.
- **A tracker as a `number` entity.** A settable value, which is almost what
  this is. Rejected because a reading is an **append into a bucket**, not an
  assignment: `number` has one current value to overwrite, and several readings
  per bucket is R163. It would quietly turn a series into a single cell.
- **Everything as services, no entities at all.** Simplest to build and it
  throws away the reason to build it: no voice assistant, no dashboard, no
  blueprint, and a household writing REST calls in YAML after all.
- **Hidden entities for collections and trackers so services could take an
  `entity_id`.** A better service contract, at the price of inventing entities
  for things that have no state - and a collection entity with no state is a row
  in the household's entity list that does nothing and cannot be explained.

# 0062 - A list is a rich sensor the household's own UI reads

Status: accepted
Date: 2026-11-22

Supersedes [0059](0059-a-list-is-a-todo-entity-and-the-other-two-kinds-are-services.md)
for the list half only, and [0060](0060-a-lapse-has-no-status-to-map-to-so-it-leaves.md)
entirely.

## Context

[0059](0059-a-list-is-a-todo-entity-and-the-other-two-kinds-are-services.md)
made a shared list a `TodoListEntity`, on the argument that the native platform
is what the household's existing voice assistant, dashboard cards and
blueprints already speak to - and that argument was and remains true.

What it underweighted is how little of a chore that platform can hold. A
`TodoItem` has four fields: a summary, a status, a due date and a description.
An item here has an assignee, a due date, answered costs, points credited to
whoever did the work, a completion note, a rich-text detail with images, and
**three** states. So the native entity published the description and the due
date and silently discarded everything else - and
[0060](0060-a-lapse-has-no-status-to-map-to-so-it-leaves.md) had to drop a
lapsed chore from the integration altogether, because neither of the two
statuses available was true of it.

Deployed and looked at, that is a thin thing. The household's actual ask was
the opposite of what the platform offers: the whole chore, as data, so its own
dashboard can draw it.

## Decision

A shared list is a **`sensor`**, not a `todo` entity. The `todo` platform is
removed; `todo.py` is deleted rather than left behind a flag.

- The **state** is the number of open chores - the one number worth a badge or
  an automation trigger.
- The **attributes** carry the list: per-state counts, an `overdue` count on
  the product's own day boundary, the declared `fields` so a form can be drawn
  without a second request, and `items` - each with its state, due date,
  assignee, points, and costs **resolved to the labels its list declared**.
- Every write is an **explicit action**: `ha_todo.add_item`,
  `complete_item`, `lapse_item`, `reopen_item`. Each returns the item it wrote.
- `items` is capped at 200 and says `truncated` when it bit.

Rich text is still never carried. `detail_html` and `completion_note_html` are
absent and only `has_detail` is published - [0061](0061-the-detail-field-is-deliberately-not-round-tripped.md)
is unaffected by any of this.

The list's field configuration **is** now fetched, which `design/0022` said it
never would be. It is cached per list for the life of the config entry rather
than re-read each poll.

## Consequences

- **The whole chore is available, and a lapse is nameable.** `state` is one of
  three values and the integration no longer has to pretend the third does not
  exist. That is the single biggest thing this buys.
- **Assist voice for lists is given up.** Home Assistant's built-in
  `HassListAddItem` intent targets `todo.*` entities and nothing else, so "add
  milk to the shopping list" stops working until somebody writes custom
  sentences and an `intent_script` over `ha_todo.add_item`. This is a real loss
  and it is the cost this ADR accepts: it was `0059`'s main argument, and the
  household chose fidelity over it knowingly. **The stock To-do card goes with
  it**, and so does anything that targets `todo.*` generically.
- **The household owns the UI, and therefore has to build one.** There is no
  default view of a list any more - a template or markdown card reading
  `state_attr(...)['items']`, or a custom card, is now required to see a chore
  at all.
- **The payload is kept out of the recorder by the integration itself**, via
  `_unrecorded_attributes = {"items", "fields"}`. The recorder caps stored
  attributes at 16384 bytes and, past that, logs a warning and stores `{}` - so
  history quietly empties while the live state still looks correct. At roughly
  400 bytes a chore that cap breaks at about **forty**, which a real household
  list passes without anybody noticing, and a deployment keeping a year of
  history on an SD card pays for it twice over in size and in writes. This was
  first written as an instruction in the README, which was the wrong shape: an
  instruction that is not followed fails silently and blames the reader. Only
  the recorder is affected - templates, automations and cards read the live
  state and see everything - and the counts stay recorded, because an
  open-chore count over a year is the one part worth graphing.
- **An attribute payload is pushed to every connected browser on every poll**,
  which is why `items` is capped and why the rich text stays out. The cap is
  published rather than hidden, because a truncated list that does not say so
  is a screen that is confidently wrong.
- **An item id is now part of the contract.** The state-changing actions take
  one, read from a sensor's `items`. A version is deliberately **not**
  published: one read from an attribute seconds ago is exactly the stale write
  the server exists to refuse, so the version comes from the coordinator's
  cache instead.
- **Removing the platform orphaned its entity.** Home Assistant keeps a
  registry row when an entity stops being produced, so the old `todo.*` entity
  sat there unavailable after the upgrade and had to be deleted. Any future
  change that removes a platform will do the same.

## Alternatives considered

- **Keep the `todo` entity alongside the sensor**, or behind an option
  defaulting to on. This was the recommendation: the two are not exclusive, it
  preserves voice for nothing, and it makes the household's preference a
  setting rather than a code fork. Declined deliberately - the entity's
  *existence* was part of the objection, since a stock To-do card can tick off
  the household's real chores, and a toggle leaves two surfaces to keep
  consistent.
- **Enrich the `todo` entity's `description` with the dropped fields**, packed
  as text. Keeps voice and shows more. Rejected: it writes our formatting into
  a field the product owns, it is unparseable by an automation, and
  `SET_DESCRIPTION_ON_ITEM` would then make it writable - which is
  [0061](0061-the-detail-field-is-deliberately-not-round-tripped.md)'s
  destructive round trip wearing a different hat.
- **One entity per chore.** Full fidelity with no attribute budget and no cap.
  Rejected: chores are created and finished constantly, so the entity registry
  would fill with rows for things that no longer exist - exactly the orphan
  problem above, continuously.
- **A custom WebSocket command instead of attributes.** No size limit, no
  recorder concern, and the right answer for a bespoke Lovelace card.
  Rejected *for now* because it is unreadable from a template or a markdown
  card, which is what a household actually builds first. It remains the thing
  to add if the attribute budget becomes the binding constraint.

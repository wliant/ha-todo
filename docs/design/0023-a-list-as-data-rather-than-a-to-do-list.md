# 0023 - A list as data rather than a to-do list

Design for [requirements/0023](../requirements/0023-a-list-as-data-rather-than-a-to-do-list.md)
(R202-R209). **Inside `hass/` only**: no backend change, no migration, and no
new endpoint - though it does start calling one the integration previously
refused to.

One ADR records the decision:
[0062](../adr/0062-a-list-is-a-rich-sensor-the-households-own-ui-reads.md),
which supersedes [0059](../adr/0059-a-list-is-a-todo-entity-and-the-other-two-kinds-are-services.md)
for the list half and [0060](../adr/0060-a-lapse-has-no-status-to-map-to-so-it-leaves.md)
entirely.

## The shape of it, in one paragraph

`todo.py` is deleted and `Platform.TODO` goes with it. Each shared list becomes
a `ListSensor` whose state is the open-chore count and whose attributes carry
the list - counts, the declared fields, and the chores themselves with their
state, due date, assignee, points and labelled costs. Four new actions do the
writing, as entity services **targeting that sensor**: `add_item`,
`complete_item`, `lapse_item`, `reopen_item`, each returning what it wrote. The coordinator now also fetches each
list's field configuration, once, so a cost can be published with the label its
list declared for it.

## Scope

| Piece | Where | New? |
|---|---|---|
| The to-do entity | `custom_components/ha_todo/todo.py` | **deleted** |
| A list as a sensor | `custom_components/ha_todo/sensor.py` | new |
| The richer chore | `custom_components/ha_todo/models.py` | changed |
| The overdue rule | `custom_components/ha_todo/mapping.py` | changed |
| Field configuration | `api.py`, `coordinator.py` | new |
| The four actions | `sensor.py` (entity services), `services.yaml`, `strings.json` | new |
| Collections and trackers | unchanged | - |
| Everything in `app/` and `web/` | unchanged | - |

## What changed about what is already there

`design/0022` said the integration **never** reads
`GET /api/lists/{id}/fields`, because posting and letting the server's own
validation speak is better than a second copy of a rule. That reasoning still
holds for *writing* - nothing here pre-empts a refusal, and R193's "refused in
the product's own words" is unchanged. But R205 needs the field's **label** in
order to publish an answered cost legibly, and no amount of server-side
validation provides that. So the fields are fetched, and the earlier statement
is overridden rather than quietly broken.

It is fetched **once per list**, cached for the life of the config entry, and
dropped when a list stops being visible. A field configuration changes when its
owner edits it, which is rare; re-reading it every minute would be one wasted
request per list forever.

## Step 1 - the chore, enriched

`models.Item` grows the assignee, the points, who finished or lapsed it and
when, and its costs. `Item.as_dict` is the **published** shape and is
deliberately narrower than the dataclass:

- **No `detail_html`, no `completion_note_html`.** Only `has_detail`, a
  boolean. `adr/0061` is untouched by this feature.
- **No `version`.** It is held, because every write needs it, but publishing it
  invites a caller to hand back one read seconds ago - which is exactly the
  stale write the server exists to refuse. The version comes from the
  coordinator's cache instead.

`models.CostValue` resolves `{fieldId, choiceId}` or `{fieldId, amount}`
against the list's declared fields into a label, a value and points.

## Step 2 - the overdue rule, reused rather than reinvented

`mapping.is_overdue` is **day-granular in the household's zone**, which is
[adr/0040](../adr/0040-overdue-is-a-day-boundary-read-from-the-server-clock.md)'s
rule: a chore due at 09:00 stays today's until midnight rather than going late
over breakfast. Anything else and the sensor's `overdue` count disagrees with
the overdue strip on the household's own agenda, which is worse than having no
count.

A chore in either terminal state is never overdue. `mapping.is_shown` and
`wanted_state` are **gone**: the first existed only to hide a lapse, and the
second to turn a boolean into a state when there were only two.

## Step 3 - the sensor

`ListSensor.native_value` is the open count. `extra_state_attributes` is the
list. Three things about it:

- **`available` is false when the list is absent from the payload**, not when
  it is empty. A grant revoked mid-poll drops the list rather than emptying it,
  and "unavailable" is the truth where `0` would be a lie.
- **`items` is capped at `MAX_PUBLISHED_ITEMS` (200) and reports
  `truncated`** (R208). The whole payload is pushed to every connected browser
  on every change, so this is a bound by design - and a cut that says nothing
  about itself is a screen that is confidently wrong.
- **`fields` publishes kind, label and required** so a form can be drawn
  without a second request (R205).
- **Each chore carries its own `overdue` flag**, computed here through
  `is_overdue`. This is the point: without it every template that wants to name
  late work reimplements the day boundary as `due_at < now()`, which is listed
  below as a silent failure. Publishing the answer makes the mistake
  unnecessary rather than merely documented.

The pending-trackers sensor likewise publishes a `trackers` attribute naming
them, so a reminder can say which rather than how many.

## Step 4 - the four actions, targeting the list

Registered in `sensor.py` as **entity services** on the `sensor` platform, not
as plain services in `__init__.py`. That is what makes the list a **picker in
the UI** rather than a name somebody types: `services.yaml` is a static file
and cannot enumerate a household's lists, but it can declare a target.

```yaml
add_item:
  target:
    entity:
      integration: ha_todo
      domain: sensor
```

`SupportsResponse.OPTIONAL` on each, so a script can act on what it just wrote
without waiting for the next poll.

**Entity services are shared by every platform in a domain**, so registering
these on `sensor` offers them to the tracker and the two count sensors too.
`required_features=[ListFeature.ITEMS]` is what refuses those - Home Assistant
answers "does not support this service" instead of an `AttributeError` from
inside the call. `services.yaml` cannot narrow the *picker* the same way: its
filters are domain, integration, device class and supported features, and the
first two are all that apply here - a sensor device class would be a lie
(`adr/0051`: a unit is a label, never a conversion) and a custom integration's
feature enum is not something the frontend can resolve. So the picker lists all
of this integration's sensors and the wrong one fails cleanly, which is the
trade.

Note `required_features` is checked against the **live entity**
(`helpers/service.py`), not the entity registry - so a registry row written
before the feature flag existed is cosmetic. HA does not publish
`supported_features` in a sensor's state attributes at all.

`async_add_item` takes a description and at most one of `due_date` /
`due_datetime`; a bare date becomes 23:59 household time through the same
`to_instant`. A list insisting on an assignee, a detail or a cost refuses it in
the product's own words.

`async_complete_item`, `async_lapse_item` and `async_reopen_item` share
`_set_state`, which owns the conflict rule that used to live in `todo.py`:

1. If the item the server hands back is **already in the wanted state**, this
   is a **no-op success** - somebody got there a second earlier, which is the
   outcome that was asked for.
2. Otherwise retry **once**, with the version from the response.
3. Never a third time: two automations and a member could otherwise hold a
   chore in a loop nobody asked for.

Each takes the chore **by description or by id**, resolved by `_resolve`
against *this list only*. Scoping to the target is what makes a description
usable at all: two chores called "bins" on two different lists are no longer
ambiguous, and two on the **same** list are refused by name - "2 chores on
Cleaning are called bins" - rather than resolved by whichever came first.

## Step 5 - the recorder

`ListSensor` declares:

```python
_unrecorded_attributes = frozenset({"items", "fields"})
```

Past 16384 bytes of attributes the recorder logs a warning and stores `{}`, so
history empties while the live state stays correct - verified by reading
`recorder/db_schema.py`, not assumed. At roughly 400 bytes a chore that is
about **forty** chores, which a real list passes unnoticed; the cap on
published items is 200, so the payload could reach ~80KB.

This started as a `recorder: exclude` instruction in the README and that was
the wrong shape. An instruction that is not followed fails silently, and the
first deployment it would have hurt is a production Home Assistant with a
**17GB** database and `purge_keep_days: 365` on an SD card - where the cost is
paid in writes as well as bytes. Declaring it on the entity means the
deployment cannot get it wrong.

Only the recorder is affected: templates, automations and cards read the live
state and see everything. The counts stay recorded, because an open-chore count
over a year is the one part worth graphing. Measured on a three-chore list:
**1642 bytes recorded before, 305 after**.

## What fails silently

1. **`or 0` where a list is missing from the payload.** A revoked grant then
   reads as a household that finished all its chores. `available` is what keeps
   those apart.
2. **Publishing `version`.** Everything works until two people act at once, at
   which point a caller hands back a version it read a minute ago and the write
   is refused for reasons nobody can see from the automation.
3. **Registering the actions without `required_features`.** They are shared by
   every `sensor` of this integration, so targeting the overdue count would
   reach a method that does not exist - an `AttributeError` from inside Home
   Assistant rather than "does not support this service".
3. **Reading `< now()` for overdue** instead of the day boundary. Correct at
   midnight and wrong by breakfast, and it disagrees with the product's own
   agenda rather than failing.
4. **Dropping the `truncated` flag** because the cap "never" bites.
5. **Forgetting the recorder exclusion.** No error, no warning the household
   will see, and an empty history discovered weeks later.
6. **Caching the field configuration without dropping it** when a list stops
   being visible - the labels then outlive the grant.
7. **Letting a lapse fall into a boolean** again. `state` is three values now;
   anything that reduces it to done/not-done re-introduces exactly the defect
   `adr/0048` created the third state to prevent.

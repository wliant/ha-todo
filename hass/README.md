# ha-todo for Home Assistant

A Home Assistant custom integration that lets the household's automation reach
its lists. It signs in as an **ordinary household member** and can see only
what has been **shared with that member** - the same per-list grant everybody
else is bound by.

It imports nothing from `app/`, `web/` or `e2e/`, and talks to a deployed
system over HTTP only. See `docs/requirements/0022`, `docs/design/0022` and
`docs/adr/0057`-`0061` in this repository.

## What it can do

A shared **list** becomes a `sensor` whose state is the open-chore count and
whose attributes carry the list. There is deliberately **no `todo` entity**: a
to-do item holds four fields and a chore here has a dozen, so the native shape
dropped the assignee, the costs, the points and a whole third state. You draw
your own view (`adr/0062`).

| | |
|---|---|
| `sensor.<entry>_<list>` | per-state counts, `overdue`, the declared `fields`, and `items` |
| `ha_todo.add_item` | **target** the list's sensor; description, `due_date` or `due_datetime` |
| `ha_todo.complete_item` / `lapse_item` / `reopen_item` | **target** the list's sensor; the chore by description or id |
| `ha_todo.add_collection_entry` | collection, name, description, tags |
| `ha_todo.record_tracker_reading` | tracker, amount, note, date |
| `sensor.<entry>_<tracker>` | a shared tracker's current bucket, per series |
| `sensor.<entry>_overdue` | how much is late, household-wide |
| `sensor.<entry>_trackers_pending_today` | daily trackers with nothing recorded |

The four chore actions are **entity services**, so the list is a picker in the
UI rather than a name you type:

```yaml
action: ha_todo.add_item
target:
  entity_id: sensor.ha_todo_dev_home_assistant
data:
  description: Milk
  due_date: "2026-11-25"
```

```yaml
action: ha_todo.complete_item
target:
  entity_id: sensor.ha_todo_dev_home_assistant
data:
  item: Milk          # or the chore's id, if two share a description
```

The picker lists every sensor this integration owns, including the trackers and
the two counts - `services.yaml` has no filter that can tell them apart - so
aiming one of those at a chore action answers "does not support this service".

Each action returns what it wrote, so a script can act on it without waiting
for the next poll. `collection` and `tracker` on the other two actions are
still typed names, because a collection is not an entity at all.

What it deliberately **cannot** do: delete anything, assign a chore, answer a
cost, or touch a chore's **detail** or **completion note** - those are rich
text with pictures in them and a plain-text round trip would flatten somebody's
writing (`adr/0061`). Only `has_detail` is published.

**You lose Assist voice for lists.** Home Assistant's built-in list intent
speaks only to `todo.*` entities, so "add milk to the shopping list" needs
custom sentences over `ha_todo.add_item`. That trade is recorded in
`requirements/0023` R209.

## Drawing a list

There is no card supplied - that is the point. The smallest useful one:

```yaml
type: markdown
content: >-
  {% set s = 'sensor.ha_todo_dev_home_assistant' %}
  **{{ state_attr(s,'list_name') }}** - {{ states(s) }} open,
  {{ state_attr(s,'overdue') }} overdue
  {% for i in state_attr(s,'items') if i.state == 'OPEN' %}
  - {{ i.description }}{% if i.assignee %} ({{ i.assignee }}){% endif %}
  {%- endfor %}
```

Each chore also carries **`overdue`**, computed by the integration rather than
left to a template - the boundary is the start of today in the *household's*
timezone, and a template doing `due_at < now()` would disagree with the
product's own agenda every morning. `sensor.<entry>_trackers_pending_today`
carries a `trackers` attribute naming them, so a reminder can say which.

There is a worked script in [`examples/scripts.yaml`](examples/scripts.yaml) -
add an item to a list, due today before 5pm and tomorrow after - and four
automations in [`examples/automations.yaml`](examples/automations.yaml):
an overdue nudge, a morning digest grouped by list and assignee, a tracker
reminder, and a button that adds a chore. Substitute your own entity ids.

## History and the recorder

You do **not** need a `recorder: exclude` entry. The list sensors declare
`items` and `fields` as unrecorded, so the bulk never reaches the database -
only the counts do, which is the part worth graphing.

That is deliberate rather than tidy: the recorder caps stored attributes at
16384 bytes and, past that, logs a warning and stores `{}`, emptying history
while the live state still looks right. At ~400 bytes a chore that breaks at
about forty.

## Setting it up

1. **Create the account.** As the admin, add a user called `homeassistant`
   with a password. It is an ordinary member; it needs no role.
2. **Share things with it.** As each list's, collection's or tracker's owner,
   add `homeassistant` to its audience. Anything you do not share is invisible
   here - it cannot be seen, named, or found to exist.
3. **Install the integration.** Copy `custom_components/ha_todo` into your
   Home Assistant `config/custom_components/` directory and restart.
4. **Add it.** Settings -> Devices & Services -> Add Integration -> "ha-todo",
   then give it the address and that account's username and password.

**Use the `https://` address.** The session cookie is marked `Secure` in both
deployed environments, so it is only ever sent over https. Over plain `http://`
the *login* succeeds - 200, with a `Set-Cookie` - and then every authenticated
call 401s, because aiohttp stores the cookie and refuses to send it. The
integration checks that the cookie is actually usable and says so, rather than
looping on a 401 against a password that is correct.

If Home Assistant cannot **resolve** that hostname, the record may be
internal-only while the TLS terminator is still reachable by address. Map it in
the Home Assistant checkout's own compose rather than using the IP here - the
front door routes by `Host` and the certificate is issued for the name:

```yaml
    extra_hosts:
      - "todo-dev.example.org:192.168.9.10"
```

## Recording somebody else's numbers

A tracker whose scope is **per member** files a reading against *whoever wrote
it*, by design - which from here is always the Home Assistant account. So a
scale automation cannot record Ana's weight on a per-member tracker.

Use a **shared** tracker per person instead ("Ana's weight"). The reading is
then the household's, and the product records that Home Assistant wrote it.

## How often it asks

Once a minute by default, configurable from 30 to 600 seconds in the
integration's options. One pass is one request per shared list, one per shared
tracker, and five more - so about fifteen for a household with six lists and
four trackers.

## Tests

Two halves in **two environments**, because the Home Assistant test harness
pins `pytest`, `pytest-asyncio` and much else exactly, and the framework-free
half is deliberately kept out of its reach (`adr/0058`). `api.py`, `mapping.py`
and `models.py` must not import `homeassistant`.

```bash
# the framework-free half: the client, and the two conversions
UV_PROJECT_ENVIRONMENT=.venv-pure uv run --group dev --no-group ha pytest

# the Home Assistant half: loading the integration, the entities, the services
UV_PROJECT_ENVIRONMENT=.venv-ha uv run --group ha --no-group dev pytest
```

Each command runs the half that can run in its environment and ignores the
other; the conversions and the boundary check run in both. They report 36 and 52.

`tests/test_boundary.py` is what keeps the rule above true: it parses the four
framework-free modules and fails if one of them grows a `homeassistant`
import, which would otherwise work perfectly until the harness next moved.

## Deploying it

```bash
./scripts/deploy-hass.sh dev          # from the repository root
./scripts/deploy-hass.sh prod         # asks you to type it out
```

The component is streamed as a tarball, staged, then swapped into place - so a
half-written integration is never sitting where Home Assistant might import it,
and files a newer version stopped shipping actually go away.

Two target flavours, set by `HASS_FLAVOUR` in `deployment/.env.<env>`, because
the two instances have nothing in common:

| | `compose` | `haos` |
|---|---|---|
| What | Docker Compose, bind-mounted config | Home Assistant OS; ssh lands in `core-ssh` |
| Available | docker, rsync, python3 | `tar`, `curl`, `git` - **no rsync, no python3, no docker** |
| Validate | grep the log after restart | `ha core check` before restart |
| Restart | `docker compose restart` | `ha core restart` (takes the whole house down) |

`HASS_HOST` is separate from `DEPLOY_HOST` on purpose: **Home Assistant does not
necessarily run on the host the application is deployed to.** On dev they share
a box; on prod they do not.

There is no image and no registry push: this is Python that Home Assistant
imports from a directory.

## Keeping dev honest

Pin the dev container to the production instance's **exact** Home Assistant
version in its own `.env` (`HA_VERSION=2026.11.1`). Left on the floating
`stable` tag it drifts, and a rehearsal on a different build is not a rehearsal.

The test harness is pinned the same way: `pytest-homeassistant-custom-component`
is chosen for the Home Assistant it brings, which is also what decides
`requires-python`.

## Not done yet

HACS packaging and a release zip. Installation is this script or a directory
copy.

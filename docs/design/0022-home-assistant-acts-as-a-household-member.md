# 0022 - Home Assistant acts as a household member

Design for [requirements/0022](../requirements/0022-home-assistant-acts-as-a-household-member.md)
(R188-R201). **A new sub-project and nothing else**: no migration, no new
endpoint, no change to any existing DTO, and not one line altered in `app/` or
`web/`. Every endpoint this needs already exists, and the authorization rule it
needs has existed since `adr/0010`.

Five ADRs record the decisions:
[0057](../adr/0057-home-assistant-signs-in-as-an-ordinary-member.md) (how it
authenticates),
[0058](../adr/0058-the-integration-is-its-own-sub-project-that-imports-nothing.md)
(where it lives),
[0059](../adr/0059-a-list-is-a-todo-entity-and-the-other-two-kinds-are-services.md)
(how the three kinds map onto Home Assistant),
[0060](../adr/0060-a-lapse-has-no-status-to-map-to-so-it-leaves.md) (three
states into two) and
[0061](../adr/0061-the-detail-field-is-deliberately-not-round-tripped.md) (what
it refuses to carry).

## The shape of it, in one paragraph

A new top-level `hass/` holds a Home Assistant custom integration. It signs in
as an ordinary member over the sign-in flow the SPA uses - `GET /api/auth/csrf`
then `POST /api/auth/login` - and keeps the `JSESSIONID` cookie, re-signing in
silently whenever a 401 says the backend restarted. One
`DataUpdateCoordinator` polls the endpoints a member's own browser polls; each
shared list becomes a `TodoListEntity`, each shared tracker becomes a sensor,
and the two kinds Home Assistant has no platform for become two services. Every
write goes through the endpoint the SPA writes through, so the grant is checked
by the code that already checks it. Nothing is added to the backend, because
nothing needs to be.

## Scope

| Piece | Where | New? |
|---|---|---|
| The sub-project | `hass/` | new |
| The API client | `hass/custom_components/ha_todo/api.py` | new |
| The two conversions | `hass/custom_components/ha_todo/mapping.py` | new |
| The poll | `hass/custom_components/ha_todo/coordinator.py` | new |
| A list as a to-do list | `hass/custom_components/ha_todo/todo.py` | new |
| Trackers, overdue, pending | `hass/custom_components/ha_todo/sensor.py` | new |
| The two services | `hass/custom_components/ha_todo/services.yaml` | new |
| Setup | `hass/custom_components/ha_todo/config_flow.py` | new |
| The `homeassistant` account | the existing admin screen | unchanged |
| The grant | the existing access screens | unchanged |
| Every endpoint used | `app/` | unchanged |

## What is already there

Everything, which is the point. The integration reads and writes only:

- `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/me` - whose
  `timezone` is the **household's** (`app.timezone`), not the caller's.
- `GET /api/lists`, `GET /api/lists/{id}/items`,
  `POST /api/lists/{id}/items`, `PUT /api/items/{id}/state`.
- `GET /api/data-lists`, `POST /api/data-lists/{id}/entries`.
- `GET /api/trackers`, `GET /api/trackers/{id}/series`,
  `POST /api/trackers/{id}/readings`, `GET /api/trackers/pending`.
- `GET /api/dashboard/overdue?scope=all`.

The sign-in flow is already proven in Python, twenty lines of it, in
`e2e/conftest.py:77-97`. That is a **reference, not a dependency**: `hass/`
imports nothing from `e2e/`, for `adr/0058`'s reason.

Two things it deliberately does **not** fetch, and both absences are decisions:

- **A list's field configuration.** `GET /api/lists/{id}/fields` is never
  called. The integration posts what it has and lets the server's validation
  speak - `{"error": "This list needs every item assigned."}` is already a
  sentence written for a human (R193). Reading `required` to pre-empt it would
  be a second copy of a rule the server enforces anyway, and the copy is what
  goes stale when a list's owner changes their mind.
- **Where a tracker's bucket starts.** `TrackerReadingService` canonicalises
  `periodStart` through `TrackerPeriod.startOf`, so the client sends today's
  household date raw and the server decides which week it was (R195). A
  client-side `startOf` is a second definition of where a week starts, which is
  `adr/0036`'s two definitions of Thursday wearing another new face.

## Step 1 - the client, and the cookie that is easy to lose

`api.py` holds one class, `HaTodoClient`, owning its **own**
`aiohttp.ClientSession`.

Not Home Assistant's shared session. The shared jar is shared with every other
integration; this client holds a `JSESSIONID` and an `XSRF-TOKEN` it must be
able to clear and re-establish on its own, and it needs a jar option nothing
else should inherit:

```python
aiohttp.ClientSession(
    base_url=base_url,
    cookie_jar=aiohttp.CookieJar(unsafe=True),
    timeout=aiohttp.ClientTimeout(total=20),
)
```

`unsafe=True` is **load-bearing**. aiohttp's default `CookieJar` silently
refuses to store cookies for a bare IP-address host, and a household's
deployment is very often `http://192.168.x.x:5987`. Without it the login
returns 200, no cookie is kept, every following call is a 401, and the re-login
path below retries that forever - presenting as a wrong password that is in
fact correct.

`_request(method, path, json=None)`, in order:

1. No session cookie held -> `login()`.
2. Non-GET -> ensure a CSRF token and send it as `X-XSRF-TOKEN`. The backend
   uses `CookieCsrfTokenRepository.withHttpOnlyFalse()` with
   `CsrfTokenRequestAttributeHandler`, so the **raw** token is echoed; there is
   no BREACH masking to undo.
3. **401** -> clear the jar, `login()` once, retry once. A second 401 raises
   `ConfigEntryAuthFailed`, which is what makes Home Assistant ask for the
   password again instead of leaving a dead entity.
4. **403** -> the CSRF token went stale with the session. Refresh it, retry once.
5. **404** -> `HaTodoNotFound`. This means *no grant, or gone*: the product
   answers 404 rather than 403 so an ungranted list is not discoverable (R189).
   It must never be flattened into "the list is empty".
6. **409** with `code == "STALE_ITEM"` -> `HaTodoStale`, carrying
   `body.get("item")`, which is the item as it actually is.
7. **400** -> `HaTodoInvalid(body["error"])`, surfaced verbatim (R193). Every
   error body in the product is `{"error": "<prose>"}`.

`login()` is `GET /api/auth/csrf` -> `POST /api/auth/login` with the header ->
keep `user_id` and `timezone` off the `CurrentUserResponse`.

**Then assert the session cookie is actually *sendable*.** Not "is it in the
jar" - that was the first cut and it was wrong, which deploying to a real
environment is what found. aiohttp **stores** a `Secure` cookie received over
http and then refuses to send it, so the jar holds a `JSESSIONID` that can
never be used, the check passes, and the whole thing presents as an endless 401
against a password that is correct. The question has to be
`cookie_jar.filter_cookies(URL(base_url))`, which is the same question the next
request will ask. Both deployed environments set `SESSION_COOKIE_SECURE=true`,
so an address typed as `http://` is the likeliest setup mistake there is - and
it is indistinguishable, without this, from the IP-address jar problem above.
`test_api.py` serves a `Secure` cookie over http and pins it.

**Re-signing in is a normal event** (R200), logged at debug and never surfaced.
Sessions are in memory, so every deploy signs the household's automation out.

## Step 2 - the two conversions

`mapping.py`, and both halves of it are the kind of thing that works for months
and then is wrong once.

**A due date is the household's day** (R198). `dueAt` is an `Instant`; Home
Assistant hands over a `date` or a `datetime`. A bare date becomes **23:59 in
the household zone** - the rule `web/src/items/DueAtField.tsx:20` already
follows, because midnight belongs to the day *before* the one somebody meant
and a chore dated Thursday would read as overdue by Thursday lunchtime. In
Python this is `zoneinfo` and four lines, rather than the arithmetic
`dashboard/calendar.ts` needs:

```python
def to_instant(due: date | datetime | None, tz: str) -> str | None:
    if due is None:
        return None
    zone = ZoneInfo(tz)
    if isinstance(due, datetime):
        local = due.astimezone(zone) if due.tzinfo else due.replace(tzinfo=zone)
    else:
        local = datetime(due.year, due.month, due.day, 23, 59, tzinfo=zone)
    return local.astimezone(UTC).isoformat().replace("+00:00", "Z")
```

The zone comes from `GET /api/me` and **never** from `hass.config.time_zone`. A
Home Assistant set to the wrong zone must not be able to file a chore on the
wrong day; that is the whole of `adr/0036`.

**A lapse has nothing to map to, so it leaves** (`adr/0060`). `ItemState` is
`OPEN | COMPLETED | LAPSED` and `TodoItemStatus` is
`NEEDS_ACTION | COMPLETED`, so a lapsed item is **omitted entirely**:
`COMPLETED` would present work nobody did as done, which is the lie
`adr/0048` added a second terminal state to avoid, and `NEEDS_ACTION` would
have the automation nag about a closed chore forever, which is the permanent
reproach 0015 R131 exists to end. The mapping is an explicit three-way match;
`state != "OPEN"` collapsing to completed is the obvious line and it is wrong.

## Step 3 - one poll

`coordinator.py`: one `DataUpdateCoordinator`, 60 seconds by default, 30 to 600
from the options flow. A pass is `GET /api/lists`, then the items of each
shared list, then `GET /api/data-lists`, `GET /api/trackers`, each shared
tracker's series, `GET /api/trackers/pending` and
`GET /api/dashboard/overdue?scope=all` - `N + M + 5` requests a minute for N
lists and M trackers, around fifteen for a real household against a server on
the same network. It is stated in `hass/README.md` and it is why the interval
is configurable.

A `HaTodoNotFound` on one list is swallowed **for that list only**: the grant
was revoked between two polls, and the right response is to drop that entity,
not to fail the refresh and take every other entity unavailable with it.

## Step 4 - a list is a to-do list

`todo.py`: one `TodoListEntity` per shared list (R190), `unique_id` the list's
UUID, grouped under one `DeviceInfo` for the deployment. The feature flags, with
the exclusions as deliberate as the inclusions:

| Feature | | Why |
|---|---|---|
| `CREATE_TODO_ITEM` | yes | R191. |
| `UPDATE_TODO_ITEM` | yes | R192 - this is how Home Assistant marks complete. |
| `SET_DUE_DATE_ON_ITEM`, `SET_DUE_DATETIME_ON_ITEM` | yes | Every list declares `DUE_AT` (`adr/0022`). Both, so a spoken "tomorrow" gets the 23:59 rule while a real time keeps itself. |
| `SET_DESCRIPTION_ON_ITEM` | **no** | R199, and Step 5. |
| `DELETE_TODO_ITEM` | **no** | R201. The product refuses to put delete on the agenda (0020) because it destroys a record with no undo; a misheard word must not reach it. |
| `MOVE_TODO_ITEM` | **no** | Manual order *is* the fetched array's order (`adr/0044`) and `ItemResponse` carries no `position`. There is nothing to write - unimplementable, not merely unimplemented. |

Completion is `PUT /api/items/{id}/state` with `{version, state: "COMPLETED"}`
and no `noteHtml`: a note is only ever legal beside `COMPLETED` and an
automation has nothing to say about how the work went.

**The version comes from the coordinator's cached item**, because Home
Assistant hands the entity a `uid` and a wanted state and nothing else. On a
409:

1. If the item the server handed back is **already in the wanted state**, this
   is a **no-op success**. Somebody ticked it off a second earlier; that is the
   outcome the automation asked for, and raising would be noise.
2. Otherwise retry **once**, with the version from the response body.
3. A second 409 raises, carrying the server's sentence - which already names
   the other member.

Never more than one retry, and never a retry of anything but the state the
automation originally asked for.

## Step 5 - what it refuses to carry

**The detail is never round-tripped** (`adr/0061`, R199). `detailHtml` is
sanitized rich HTML whose `<img src>` points at `/api/images/{uuid}`
(`adr/0018`); Home Assistant's `description` is plain text. Exposing one as the
other means reading it back as text and writing it on the next update, which
**flattens a member's formatting and drops their pictures** - and Home
Assistant's update path sends the whole item, so changing a due date would do
it. Nothing fails. So the feature is not declared and `detailHtml` is not
exposed at all.

The consequence is accepted rather than hidden: a list insisting on `DETAIL`,
`ASSIGNEE` or a `COST` cannot have items **created** on it from here. The
entity still exists and completion still works; the create is refused in the
product's own words (R193). Assigning the item to the automation's own account
to satisfy a required assignee would be worse - it would hand a chore to a
member who is a machine, on a list other people read.

## Step 6 - the sensors

`sensor.py`, from each shared tracker's
`GET /api/trackers/{id}/series?from=<current bucket>&to=<today>&by=<period>`,
taking the last bucket.

A `SHARED` tracker is one sensor. A `PER_MEMBER` tracker is **one sensor per
series the server returns**, because "the tracker's value" is undefined when
four people each have one, and a single sensor would have to pick one silently.

- **An empty bucket is `None`, never `0`** (R196). `TrackerSeriesResponse` says
  so in as many words: a bucket nobody recorded is a `null` and never a zero,
  because a missed weigh-in is not 0 kg and a chart drawing it as one invents a
  crash. `native_value = None` so Home Assistant says `unknown`. An `or 0`
  here would look like tidying.
- `native_unit_of_measurement` from the tracker's `unit`,
  `suggested_display_precision` from its `decimals`.
- **No `device_class`.** A unit here is a label and never a conversion
  (`adr/0051`); `device_class: weight` would let Home Assistant render kg as lb
  and show the household a number nobody recorded.
- `state_class` is `MEASUREMENT`. A bucket's rollup is a reading, not a meter.

Two more: **overdue**, from `OverdueResponse.total` and **never**
`len(items)` - the page is capped and the undercount grows with the backlog,
which that record's own documentation warns about (R197) - and **pending
trackers**, `len(GET /api/trackers/pending)`, an endpoint that takes no date
because the server owns today.

## Step 7 - the two services

Collections and trackers have no Home Assistant platform, so they are services
(`adr/0059`), declared in `services.yaml` with `strings.json` and
`translations/en.json`.

**`ha_todo.add_collection_entry`** (R194) - `collection` (its name, matched
case-insensitively, or its UUID), `name` (required), `description`, `tags`. No
detail, for Step 5's reason. An unknown or ambiguous name raises **naming the
collections that are shared**, because a bare "not found" against a server the
member cannot browse from where they are is undebuggable.

**`ha_todo.record_tracker_reading`** (R195) - `tracker` (resolved the same
way), `amount` (required), `note`, and `period_start`, which when omitted means
**today in the household zone** and is then bucketed by the server.

**The limitation that goes on the label.** `SaveReadingRequest` carries no
member id by design - the series is derived from the tracker's scope and the
caller - so on a `PER_MEMBER` tracker this can only ever write **its own**
series. A scale cannot file a number under Ana. The arrangement that works is a
**`SHARED` tracker per person**: `member_id` is then NULL, the reading is the
household's, and `recorded_by` attributes it. `hass/README.md` has to say so,
because the obvious arrangement is the one that disappoints.

## Step 8 - setting it up

`config_flow.py`: `url`, `username`, `password`, `verify_ssl`, validated by
actually signing in and calling `GET /api/me`. The errors are distinct -
`cannot_connect`, `invalid_auth`, `no_session` (Step 1's cookie assertion) and
`unknown` - because they have four different fixes.

`async_set_unique_id(user_id)` then `_abort_if_unique_id_configured()`: the
member's UUID is stable across a hostname change and differs between
deployments, so dev and prod can both be added while the same account cannot be
added twice. `async_step_reauth` for the password changing underneath it; the
options flow carries the interval.

## Step 9 - the tests, and the line through the middle of them

`hass/tests/`, run with `uv` like `e2e/`, dependencies pinned exactly.

**`api.py`, `mapping.py` and `models.py` import nothing from
`homeassistant`** (`adr/0058`). That line is what lets everything worth testing
be tested with `aiohttp` alone, so the suite keeps running when
`pytest-homeassistant-custom-component` pins a Home Assistant that has moved
on. A convenience import of `homeassistant.util.dt` into `mapping.py` drags the
entire framework across it.

- `test_mapping.py` - the 23:59 rule across several household zones *and*
  several machine zones, the three-way state mapping, unknown-not-zero. Pure.
- `test_api.py` - against a small `aiohttp` test server: the CSRF dance,
  re-login on 401 retried exactly once, the 409 no-op and the 409 retry, the
  unusable-cookie assertion (a `Secure` cookie served over http), and a 404 not
  becoming an empty list.
- `tests/ha/test_integration.py` - loading the integration for real, the
  entities it produces and the two services. Needs
  `pytest-homeassistant-custom-component`, which pins `pytest`,
  `pytest-asyncio` and much else exactly - so it is a **separate dependency
  group and a separate environment**, declared `conflicts` in
  `pyproject.toml`. One venv for both does not work: `pytest-socket` arrives
  with the harness and blocks `test_api.py`'s real server.
- `test_boundary.py` - the import rule above, parsed rather than imported so it
  is the same answer in either environment.

## Step 10 - deploying it

`scripts/deploy-hass.sh <env>` ships `hass/custom_components/ha_todo` to the
target and calls the Home Assistant checkout's own `deploy.sh`, which rsyncs
the component into `config/custom_components` and restarts the container. All
SSH goes through `ssh_run` in `scripts/lib/common.sh`, like every other script
here: a deployment step belongs in a script rather than in somebody's shell
history. `HASS_DIR` and `HASS_PORT` come from `deployment/.env.<env>`.

There is **no image and no registry push**. The integration is Python that Home
Assistant imports from a bind-mounted directory, so there is nothing to build
and nothing to pull - which is why this is its own script rather than a step in
`scripts/deploy.sh`.

The script then greps the Home Assistant log for `ha_todo` errors, because a
custom component that fails to import says so **once, at startup**, and is
otherwise simply absent from the list of integrations - there is no second
symptom.

## Two things the first real deployment taught us

Neither was predictable from reading the code, and both are environmental.

**The address must be the https hostname, and that is not negotiable.** Both
deployed environments set `SESSION_COOKIE_SECURE=true`, so the session cookie
is only ever sent over https. Pointing the integration at
`http://<host>:<port>` lets the *login* succeed - 200, with a `Set-Cookie` -
and then every authenticated call 401s. This is what the sendable-cookie check
in Step 1 exists to name.

**The front door may not be resolvable from the machine Home Assistant runs
on.** It is configured outside this repository
([adr/0054](../adr/0054-new-work-reaches-an-open-agenda-over-sse.md)), and its
record can be internal-only - so the container could reach the terminator's
address and present a valid certificate, while being unable to look the name
up. The fix belongs in the Home Assistant checkout's own compose, as an
`extra_hosts` entry, and **not** in the integration: the address has to stay
the hostname, because the front door routes by `Host` and the certificate is
issued for the name.

## What fails silently

In roughly descending order of how long it would take to find.

1. **Asking whether the session cookie was *stored* rather than whether it can
   be *sent*.** This one shipped. aiohttp keeps a `Secure` cookie it received
   over http and will not send it back, so the check passes and every call
   401s - against a correct password, on a deployment whose only mistake was a
   missing `s`. The re-login path then retries it forever.
2. **A default `aiohttp.CookieJar` against an IP address.** Login succeeds, no
   cookie is stored, every call 401s, the re-login path loops, and the report is
   "it says my password is wrong and it isn't". Step 1's assertion exists to
   catch exactly this and the `http://`-against-`Secure` case with it.
3. **The detail field round-tripped as plain text.** Somebody's formatted
   detail and their pictures are replaced with flattened text as a side effect
   of an automation touching a due date. No exception, no test, and the member
   who notices is the one who wrote it.
4. **`state != "OPEN"` collapsing to completed.** Work nobody did reads as
   done, which is the distinction 0015 exists to draw.
5. **`or 0` on an empty tracker bucket.** A missed weigh-in becomes a crash on
   a chart, and it looks like defensive coding.
6. **Counting `len(items)` for overdue instead of `total`.** Correct for small
   households and quietly wrong for exactly the ones with a backlog worth
   automating against.
7. **Reading the zone from `hass.config.time_zone`.** Right in the household
   that configured both the same way, and off by a day for anybody who did not.
8. **A client-side copy of `TrackerPeriod.startOf`.** Agrees with the server
   until one of them changes, and then readings land in a week the household
   does not recognise.
9. **Flattening a 404 into an empty list.** A revoked grant and an empty list
   become the same thing, and the automation stops writing with no sign.
10. **A 401 surfaced as an error.** Correct behaviour, reported as a broken
    integration after every deploy, until the household silences the notices.
11. **`device_class` set on a tracker sensor.** Home Assistant converts the
    unit and shows a number nobody recorded, confidently.
12. **Retrying a 409 more than once.** Two automations and a member can keep a
    chore in a retry loop whose outcome nobody asked for.
13. **A `homeassistant` import creeping into `mapping.py`.** The pure tests
    stop being pure, and the next Home Assistant pin takes the whole suite dark
    at once. This is the one item on this list that has been made to fail
    loudly instead: `tests/test_boundary.py` parses for it.

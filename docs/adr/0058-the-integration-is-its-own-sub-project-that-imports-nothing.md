# 0058 - The integration is its own sub-project that imports nothing

Status: accepted
Date: 2026-11-20

## Context

[requirements/0022](../requirements/0022-home-assistant-acts-as-a-household-member.md)
adds a Home Assistant custom integration. It is Python, it runs inside somebody
else's process on somebody else's release train, and it talks to this product
over HTTP.

The repository already has a precedent for exactly that shape: `e2e/` is a
standalone pytest project with its own `pyproject.toml`, its own lockfile, its
own README and no imports from `app/` or `web/`, deliberately - if a change
breaks the contract, it should notice the way a family member would.

Two things made the boundary worth deciding rather than assuming:

- **`e2e/conftest.py` already contains a working Python implementation of the
  sign-in dance** - the CSRF fetch, the header, the cookie-keeping client. It
  is twenty lines and it is right, so the temptation to import it is real.
- **A Home Assistant custom component imports `homeassistant`**, a large
  framework pinned to a specific version by its test harness
  (`pytest-homeassistant-custom-component`), which moves roughly monthly.
  Anything importing it inherits that cadence.

## Decision

A new top-level **`hass/`**, self-contained the way `e2e/` is: its own
`pyproject.toml` with exactly pinned dependencies, its own `uv.lock`, its own
README, its own `tests/`. It imports nothing from `app/`, `web/` or `e2e/`, and
nothing imports it.

`e2e/conftest.py`'s sign-in code is a **reference, not a dependency**. The
client is written again, in the shape an async integration needs.

And a second boundary, inside the sub-project: **`api.py`, `mapping.py` and
`models.py` must not import `homeassistant`.** The client, the two conversions
and the data shapes are framework-free; only `__init__.py`, `config_flow.py`,
`coordinator.py`, `todo.py` and `sensor.py` know Home Assistant exists.

Packaging and distribution - HACS, a release zip, anything in `scripts/` or
`deployment/` - is deferred and recorded nowhere yet.

## Consequences

- **The integration can be changed without the backend's build.** No Gradle, no
  pnpm, no shared lockfile to drift.
- **The logic worth testing is testable without Home Assistant.** The due-date
  conversion, the three-into-two state mapping, the retry rules and the error
  translation are the parts that will be wrong, and all of them are reachable
  with `aiohttp` alone. When the Home Assistant pin moves and
  `pytest-homeassistant-custom-component` breaks, those tests keep running.
- **That inner boundary is one import away from gone**, so it is enforced
  rather than merely written down. A convenience
  `from homeassistant.util import dt` in `mapping.py` would work perfectly and
  silently take the pure tests across the line with it, and nothing would fail
  until the harness next pinned a Home Assistant that had moved on.
  `tests/test_boundary.py` parses the four modules and asserts it, which makes
  the failure a red test on the commit that causes it. It also refuses a module
  that is *neither* pure nor Home-Assistant-importing, so the list cannot go
  stale as the package grows.
- **The sign-in flow now has two implementations in Python** - `e2e/` and
  `hass/` - and a third in TypeScript in `web/`. If the flow changes, three
  places change. Accepted: they are three different clients of a public
  contract, which is the same reason `e2e/` imports nothing in the first place.
- **Distribution is unsolved.** HACS installs `custom_components/*` from a
  repository root, and this one is four directories down. A member installing
  it today copies a directory by hand.

## Alternatives considered

- **`custom_components/ha_todo/` at the repository root**, with a `hacs.json`
  beside it. The layout HACS installs from with no packaging at all, and the
  reason to reconsider the day distribution matters. Rejected for now because
  it puts a Python package with no sub-project boundary next to `app/`, `web/`
  and `e2e/`, and solves a problem that has been explicitly deferred.
- **A separate repository.** What a widely distributed integration would get,
  and the right answer if this is ever published. Rejected while the integration
  and the API it depends on change together: a response shape and its only
  consumer should be reviewable in one diff for as long as that is possible.
- **A shared Python client package imported by both `e2e/` and `hass/`.**
  Attractive once and wrong twice: it would give `e2e/` an import from inside
  the repository, which is the one thing that suite is defined by not having,
  and it would couple a test suite's dependency set to a Home Assistant
  integration's.
- **Writing it as a Home Assistant "command line" or REST/template
  integration** in YAML, with no custom component at all. Genuinely possible for
  the writes. Rejected because none of R190's native to-do behaviour, none of
  the version handling, and no part of the config flow can be expressed that
  way - the household would get four shell-shaped services and no list.

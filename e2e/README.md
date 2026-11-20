# ha-todo end-to-end suite

Drives a **deployed** ha-todo through a real browser. It imports nothing from
`app/` or `web/` on purpose — if a change breaks the contract, this suite should
notice the same way a family member would.

## Setup

```bash
uv sync
uv run playwright install chromium
```

## Running

The target environment is whatever `E2E_BASE_URL` points at. Per CLAUDE.md the
intended target is **dev**; point it at the local stack while developing.

```bash
# local stack (scripts/dev-up.sh)
E2E_BASE_URL=http://localhost:5173 uv run pytest

# dev environment
E2E_BASE_URL=https://dev.example.invalid uv run pytest

uv run pytest -m smoke                       # smoke subset
uv run pytest tests/test_smoke.py::test_demo_member_can_sign_in
uv run pytest --headed --slowmo 300          # watch it run
```

## Credentials

Demo accounts come from the `db/seed` Flyway location, which is active in the
local and dev profiles only. Override with `E2E_USERNAME` / `E2E_PASSWORD`.

## Test data

Never assume data already exists, and never leave data behind. Use the
`test_data` fixture (see `conftest.py`): it gives every run a unique prefix so
parallel and repeated runs cannot collide, and deletes what it registered at the
end of the session.

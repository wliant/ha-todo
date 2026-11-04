# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

**Skeleton, as of 2026-11-04.** Build, local stack, session auth, deploy scripts and all three test
harnesses are real and passing. The household / list / item domain (requirements R1-R6) is **not**
implemented — `/lists` is a placeholder page. Keep this file true: when a decision here turns out wrong,
update it in the same change.

## What this product is

`ha-todo` is a shared household todo list. A family/household has many **lists** (grouping is a core
feature, not a label); members add **items** to a list, items can be **assigned** to a member, and any
member can read any list in their household and mark items complete. Design every API and UI around
"multiple people touching the same list concurrently", not around a single-user todo app.

## Monorepo layout

```
app/           Spring Boot 4.1.2 backend (Gradle, Groovy DSL, Java 21)
web/           React 19 + TypeScript frontend (Vite, pnpm, oxlint)
e2e/           Standalone pytest + Playwright end-to-end suite — runs against dev, not local mocks
deployment/    docker-compose files + env templates per environment
scripts/       Shell entrypoints for build/deploy; the only supported deploy path
docs/          Specs, designs, ADRs (see Spec-driven workflow)
```

`app/` and `web/` own their own unit and integration tests. `e2e/` is deliberately a separate project with
its own dependency set and no imports from the other two — it talks to a deployed system over HTTP only.

Each module is self-contained: `app/` has its own Gradle wrapper, `web/` its own lockfile. There is no root
pnpm workspace — one JS package does not need one, and it would move the lockfile out of the image build
context.

## Commands

Backend (`app/`):
```bash
./gradlew build                       # compile + unit + integration tests
./gradlew test                        # unit tests only
./gradlew integrationTest             # integration tests (Testcontainers-backed)
./gradlew test --tests '*AppUserDetailsServiceTest.mapsStoredUserOntoUserDetails'  # single test
./gradlew bootRun --args='--spring.profiles.active=local'   # needs infra up (see Local stack)
./gradlew check                       # test + integrationTest
```

Gradle **auto-provisions JDK 21** via the foojay resolver in `settings.gradle`; no system JDK 21 needed.
Integration tests need a running Docker daemon (Testcontainers).

Frontend (`web/`):
```bash
pnpm install
pnpm dev                              # Vite dev server
pnpm test                             # unit tests (watch off in CI)
pnpm test path/to/File.test.tsx       # single test file
pnpm lint && pnpm typecheck           # oxlint, then tsc -b --noEmit
pnpm build
```

`pnpm` enforces a 24h `minimumReleaseAge` on lockfile entries, so a just-published dependency fails
`--frozen-lockfile` inside the Docker build. Pin to an aged release rather than waiting it out.

E2E (`e2e/`) — pytest driving a real browser via Playwright:
```bash
uv sync
uv run playwright install chromium    # once, after install
E2E_BASE_URL=http://localhost:5173 uv run pytest      # against the local stack
E2E_BASE_URL=https://<dev-host> uv run pytest         # against dev (the intended target)
uv run pytest tests/test_smoke.py::test_demo_member_can_sign_in
uv run pytest -m smoke
uv run pytest --headed --slowmo 300   # watch it run while debugging
```

Local stack:
```bash
./scripts/dev-up.sh                   # infra, then app + web, waiting for health
./scripts/dev-down.sh                 # add --volumes to discard the local database
```
web on http://localhost:5173, API on http://localhost:8080. Demo sign-in `ana@example.com` /
`password123`, seeded from `db/seed` in the local and dev profiles only.

## Architecture notes

**Backend** is a layered Spring Boot service: REST controllers → application services (transaction
boundary, authorization) → domain → Spring Data JPA repositories against Postgres. Schema changes go
through versioned migrations (Flyway); never rely on JPA auto-DDL outside tests.

**Authorization is household-scoped.** Every read and write must be checked against the caller's household
membership, enforced in the service layer — not only in the controller and not only in the UI. A list id in
a URL is never sufficient proof of access.

**Item completion and assignment are the contended writes.** Two members may complete or reassign the same
item at once; use optimistic locking (`@Version`) and return a conflict the frontend can reconcile rather
than silently last-write-wins.

**Authentication is session-based.** `POST /api/auth/login` takes JSON, BCrypt-checks against `app_user`,
rotates the session id, and stores the context in the HTTP session. Anonymous API calls get **401**, never a
redirect. CSRF stays on (the credential is a cookie): `GET /api/auth/csrf` issues the `XSRF-TOKEN` cookie
and clients echo `X-XSRF-TOKEN`. `ERROR` dispatches are permitted in the chain — without that, a 500 inside
a controller is forwarded to `/error`, blocked, and surfaces as a misleading 401. See
`docs/adr/0002-session-auth-postgres-users.md`.

**Redis and MinIO are deferred, not optional-and-maybe-present.** Neither is wired in; both sit commented out in
`deployment/docker-compose.infra.yml` and `.env.example`. Adding either requires a concrete need and a new
ADR — see `docs/adr/0003-defer-redis-and-minio.md`. The likeliest triggers are a second backend instance
(in-memory sessions break) and item attachments.

**The frontend reaches the backend same-origin in every environment.** The SPA only ever uses relative
`/api` paths - never an absolute API URL, never `VITE_API_URL`. nginx in the `web` image proxies `/api` and
`/actuator/health` to `app:8080`, and the Vite dev server proxies the same paths, so cookies, `SameSite`
and CSRF behave identically everywhere and CORS is never needed. In dev and prod the `app` service
publishes **no** host port at all (`expose` only); locally it is bound to `127.0.0.1` purely so `pnpm dev`
can proxy to it. Do not add a published app port or an absolute API base URL - that reintroduces a
cross-origin path the session cookie is not configured for.

**Frontend** is a React SPA consuming the REST API; keep server state in a query cache (React Query-style)
so a list refetches cleanly after another member's change, and keep the list-grouping view model separate
from the item components.

## Environments

Two environments only: **dev** and **prod**. Postgres (and Redis/MinIO if ever needed) is *provided
infrastructure* in both — the app points at it via environment configuration and never provisions it.
Flyway's `db/seed` location is active in **local and dev only**, so demo accounts can never reach prod. Local development is
the exception: `deployment/docker-compose.infra.yml` stands up throwaway equivalents.

Configuration is environment variables injected by the compose file for the target environment; no secrets
and no environment-specific hostnames in committed Spring profiles or frontend bundles.

## Deployment

Deployment is **scripted, from the local machine, over SSH**:

1. `scripts/build.sh` builds the `app` and `web` images locally.
2. `scripts/push.sh <env>` tags and pushes them to the registry.
3. `scripts/deploy.sh <env>` does both, copies the compose + env files over, runs `docker compose pull`
   and `up -d` on the target via SSH, then polls `/actuator/health` until it reports UP.

Rules:
- **Never open an interactive SSH session to a server to do deployment work by hand** — if a step is
  missing, add it to the script. All SSH goes through the single `ssh_run` helper in
  `scripts/lib/common.sh`. `deploy.sh prod` requires typing `deploy prod` to proceed.
- Targets live in uncommitted `deployment/.env.dev` / `.env.prod`; `deployment/.env.example` documents
  every variable. Both environments run on **`wliant@192.168.1.248`**, sharing
  `/home/wliant/myapps/ha-todo` — the compose/env filenames and the project names (`ha-todo-dev`,
  `ha-todo-prod`) keep them apart. **dev is port 5987, prod is 5986.**
- The registry is `192.168.1.248:5000` over **insecure HTTP**; both this machine and the target already
  list it under `insecure-registries`.
- Each environment has its own Postgres role and database on `192.168.1.248:5432`: `hatodo_dev` and
  `hatodo_prod`. Flyway creates every table, so the role only needs to own its database.
- Both environments are fronted by HTTPS: dev at **https://todo-dev.wliant.org**, prod at
  **https://todo.wliant.org**, terminating TLS upstream and proxying to ports 5987 / 5986. So
  `SESSION_COOKIE_SECURE=true` in both, and the app sets `server.forward-headers-strategy: framework`
  while nginx forwards the browser's original scheme (`$http_x_forwarded_proto`, not `$scheme`, which is
  always plain http inside the container).
- A consequence: hitting a deployed environment **directly on its port over http:// cannot log in** — the
  browser drops the `Secure` cookie. Always use the https:// hostname. The TLS terminator must send
  `X-Forwarded-Proto`.
- Compose on the target is always invoked with `--env-file .env.<env>`. `env_file:` only populates the
  container; without `--env-file`, `${REGISTRY}` and `${WEB_PORT}` interpolate to empty and the pull fails.
- Rollback is `./scripts/deploy.sh <env> <older-tag>` — that is why `IMAGE_TAG` is an explicit argument.
- Deploys are outward-facing and hard to reverse. Confirm with the user before running one, especially prod.

## Testing strategy

- **Unit** and **integration** tests live inside `app/` and `web/`. Backend integration tests use real
  Postgres via Testcontainers rather than mocks of the data layer, and live in the separate
  `src/integrationTest` source set so `./gradlew test` stays fast and Docker-free.
- **E2E** runs from `e2e/` against the **dev** environment, written as `pytest` tests driving Playwright
  (`pytest-playwright`) through the real UI. Assert on user-visible state, and prefer role/label selectors
  over CSS so a restyle doesn't break the suite. Multi-member scenarios (one member assigns, another
  completes) need two browser contexts in one test — that is the suite's main reason to exist.
- E2E needs explicit **test data management**: use the `test_data` fixture in `e2e/conftest.py`. It gives a
  run a unique prefix (`TestData.name()`) so parallel and repeated runs cannot collide, and
  `defer_cleanup()` registers teardown that runs at session end. Seed through the API with
  `authenticated_api`. Never assume pre-existing data in dev, and never leave residue behind.
- Two members acting at once is the suite's reason to exist: the `second_member_page` fixture gives a second
  browser context with its own session.

## Development workflow

- `main` is the default branch; every feature gets its own branch off `main`.
- **Verify locally before opening a PR.** `./scripts/dev-up.sh`, then exercise the actual change in a
  browser through the `playwright` MCP server (`mcp__playwright__*`, registered in `.mcp.json`) — click the
  real flow a family member would, not just the happy-path unit test. Unit and integration tests passing is
  not sufficient evidence that a UI change works. Then `./scripts/dev-down.sh`, and report what was actually
  exercised in the PR description.
  If the MCP server is unavailable (it needs one-time approval via `claude mcp list`), fall back to running
  `e2e/` against the local stack and say which you used — don't claim a browser check you didn't do.
- **Spec-driven**: before implementing a feature, write the requirement and design into `docs/`, and record
  non-obvious technical choices as an ADR (`docs/adr/NNNN-title.md` from `template.md`, immutable once
  accepted — supersede rather than edit). Implementation should be traceable back to a spec document.
  Start from `docs/requirements/0001-shared-household-todo.md`; its open questions (how a member joins a
  household) block the household-scoping work.

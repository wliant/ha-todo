# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

**Skeleton, accounts and the app shell, as of 2026-11-04.** Build, local stack, deploy scripts and all
three test harnesses are real and passing. **Accounts and administration (R8-R12) are implemented**:
username identity, optional per-user passwords, a pick-a-user sign-in screen, and a single admin who
adds and deletes users. **The PWA and UI shell (R13-R17) are implemented**: installable as "List",
responsive with a navigation bar, master-detail administration, an Android share target, and a sign-in
roster that describes nobody. The list / item domain (requirements R1-R6) is still **not**
implemented — `/lists` is a placeholder page, and a shared link is parked on the device because there
is no item to make of it yet.

**Lists, items and personal settings (R18-R35) are specified, not built, as of 2026-11-05.**
`docs/requirements/0004` and `docs/design/0004` describe the whole feature and `docs/adr/0010`-`0019`
record its decisions; delivery is phased, one PR per phase, and only phase 0 (the specification) has
landed. Read design/0004 before writing any of it. Keep this file true: when a decision here turns out
wrong, update it in the same change.

## What this product is

`ha-todo` is a shared household todo list. **The deployment is the household** — there is one, every
user belongs to it, and there is no household entity (`docs/adr/0010`). A household has many **lists**
(grouping is a core feature, not a label); each list is seen by the people its creator grants access
to, so a shared grocery list and a private workout log are both ordinary. Members add **items** to a
list, items can be **assigned** to a member, and any member with access can mark any of that list's
items complete. Design every API and UI around "multiple people touching the same list concurrently",
not around a single-user todo app.

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
pnpm icons                            # redraw public/icons/*.png, then commit them
```

`pnpm` enforces a 24h `minimumReleaseAge` on lockfile entries, so a just-published dependency fails
`--frozen-lockfile` inside the Docker build. Pin to an aged release rather than waiting it out.

E2E (`e2e/`) — pytest driving a real browser via Playwright:
```bash
uv sync
uv run playwright install chromium    # once, after install
E2E_BASE_URL=http://localhost:5173 uv run pytest      # against the local stack
E2E_BASE_URL=https://<dev-host> uv run pytest         # against dev (the intended target)
uv run pytest tests/test_smoke.py::test_passwordless_member_signs_in_with_one_click
uv run pytest -m smoke
uv run pytest --headed --slowmo 300   # watch it run while debugging
```

Local stack:
```bash
./scripts/dev-up.sh                   # infra, then app + web, waiting for health
./scripts/dev-down.sh                 # add --volumes to discard the local database
```
web on http://localhost:5173, API on http://localhost:8080. Sign-in is a list of people: pick **Ana**
(password `password123`) or **Ben** (no password at all), seeded from `db/seed` in the local and dev
profiles only. The admin is `admin` / `admin` locally, from `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

## Architecture notes

**Backend** is a layered Spring Boot service: REST controllers → application services (transaction
boundary, authorization) → domain → Spring Data JPA repositories against Postgres. Schema changes go
through versioned migrations (Flyway); never rely on JPA auto-DDL outside tests.

**Seed data is a repeatable migration (`db/seed/R__*.sql`), not a versioned one.** Flyway runs repeatable
migrations after every versioned one and re-runs them when their content changes, so demo data can be
edited freely and can never sit in front of a schema change. The old versioned seed at `V900` is why the
schema series resumes at **V901** — a lower version would be out of order on any database that applied it,
and why local/dev set `flyway.ignore-migration-patterns: "*:missing"`. Prod never applied it and keeps
strict validation. New schema migrations continue from V902.

**Authorization is per-list access, not household-scoped.** Every read and write is checked against the
caller's grant on that list, enforced in the service layer — not only in the controller and not only in
the UI. A list id in a URL is never sufficient proof of access. Two predicates: `readable` (the caller
owns the list or holds a `list_access` row) and `managed` (the caller owns it). `managed` is deliberately
**not** `owner || isAdmin()` — the admin administers accounts, not list contents, and gains nothing on a
list they were not granted. No access answers **404**, because an ungranted list must not be discoverable;
access without ownership answers **403**. Filter list queries in SQL by joining `list_access`, never by
fetching and filtering in Java. See `docs/adr/0010-per-list-access-instead-of-household-scoping.md`.

**Item completion and assignment are the contended writes.** Two members may complete or reassign the same
item at once; use optimistic locking (`@Version`) and return a conflict the frontend can reconcile rather
than silently last-write-wins.

**Authentication is session-based.** `POST /api/auth/login` takes JSON, rotates the session id, and stores
the context in the HTTP session. Anonymous API calls get **401**, never a redirect. CSRF stays on (the
credential is a cookie): `GET /api/auth/csrf` issues the `XSRF-TOKEN` cookie and clients echo
`X-XSRF-TOKEN`. `ERROR` dispatches are permitted in the chain — without that, a 500 inside a controller is
forwarded to `/error`, blocked, and surfaces as a misleading 401. See
`docs/adr/0002-session-auth-postgres-users.md`.

**Identity is a `username`, and a password is optional.** There is no `email` column. `password_hash IS
NULL` *is* "no password required" — there is deliberately no companion boolean, because a boolean that
disagrees with the hash is an auth bypass. `LoginService` branches on it: a stored hash goes through
`DaoAuthenticationProvider`, a null one is signed in directly and any submitted password ignored. Keep that
bypass as one obvious `if`, not a custom `AuthenticationProvider`. See
`docs/adr/0005-pick-a-user-login-with-optional-passwords.md`.

**The sign-in roster is public, and says only who exists.** `GET /api/auth/users` is `permitAll` because the
login screen is a list of people. It returns `id`, `username`, `displayName` and **nothing else**: no role,
no `passwordRequired` — publishing which account is the admin and which have no secret is a list of free
passes sorted by name. Never add a field to it. The client therefore cannot know whether a password is
needed and finds out by posting the login without one and reading the **401**, so a deliberate 401 is part
of a successful sign-in and must not be read as an attack on its own. See
`docs/adr/0009-opaque-sign-in-roster.md`. The older consequence is unchanged: anyone who can reach the
deployment can see the household and sign in as any passwordless member
(`docs/requirements/0002-accounts-and-administration.md`); do not widen it, and keep both environments
behind the HTTPS front door.

**One admin, from the environment.** `role` is `ADMIN` | `MEMBER`, and a partial unique index makes a
second admin unrepresentable. `AdminAccountInitializer` reconciles the admin row from `ADMIN_USERNAME` /
`ADMIN_PASSWORD` on every startup and **fails startup** if no password is set; that is also the rotation
path. `/api/admin/**` is `hasRole("ADMIN")`, and the service refuses to delete the admin row regardless of
caller. See `docs/adr/0006-admin-account-from-environment.md`.

**The app is an installable PWA whose service worker caches nothing.** `web/public/manifest.webmanifest`
(name "List", standalone, `start_url: /lists`), the iOS-only meta tags in `index.html`, and
`public/sw.js`, which exists solely to satisfy the install criteria. Offline use is out of scope, and a
precaching worker would serve a stale `index.html` behind nginx's `no-store` — so do not add caching to
it without replacing `docs/adr/0007-installable-pwa-without-offline-caching.md`. Icons are drawn by
`web/scripts/generate-icons.mjs` (`pnpm icons`, no dependencies) and the PNGs are committed.

**Receiving a shared link is Android-only, and the link is parked.** The manifest declares a **GET**
share target at `/share?title=&text=&url=`; `SharePage` normalises it (Chrome often puts the URL in
`text`, not `url`) and, because items do not exist yet, holds it in `localStorage` per user via
`src/share/parkedLinks.ts`, shown on `/lists` as waiting. iOS Safari does not implement Web Share
Target, so an installed iPhone app cannot appear in the share sheet — a platform limit, not a gap to
work around. When R1-R5 land, the `park()` call becomes the create-item mutation and that module is
deleted. See `docs/adr/0008-share-target-for-links.md`.

**The signed-in UI is one shell with one breakpoint.** Every authenticated route renders inside
`AppLayout`, so the navigation bar mounts once. The breakpoint is `48rem` and lives in CSS only: below
it navigation is a bottom bar and master-detail shows one pane at a time, above it navigation sits in
the top bar and both panes are columns. There is no viewport hook and no JS layout state; adding one is
how the two layouts start to drift. Colours come from the tokens in `index.css`, which honour
`prefers-color-scheme`; components own none.

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
- `ADMIN_PASSWORD` is required in every environment's env file. `scripts/deploy.sh` refuses without it,
  rather than letting the app fail its health check after the images are already built and pushed.
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
- E2E targeting the admin screens needs `E2E_ADMIN_PASSWORD`; those tests **skip** without it rather than
  guessing a credential that is environment configuration, not seed data.
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
  Start from `docs/requirements/0001-shared-household-todo.md`, then `0004` which refines it. 0001's
  open question — how a member joins a household — is **closed, not answered**: there is no household to
  join (`docs/adr/0010`), so nothing blocks the domain work any more.

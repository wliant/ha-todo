# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

**Accounts, the app shell and Material Design, as of 2026-11-05.** Build, local stack, deploy scripts and
all three test harnesses are real and passing.

Implemented: **accounts and administration (R8-R12)** — username identity, optional per-user passwords, a
pick-a-user sign-in screen, and a single admin who adds and deletes users. **The PWA (R13, R14)** —
installable as "List", with an Android share target. **The UI shell (R15-R17)** — now a sidebar rather
than the original bottom bar, master-detail administration, and a sign-in roster that describes nobody.
**R18-R35 are implemented, bar one field.** Material Design throughout; the list entity with per-list
access grants, pins, recency and search; the per-list field configuration including named costs;
profile pictures in object storage; and **items** — values for every declared field, assignment bounded
by access, completion with points credited to whoever did the work, and a shared link becoming a real
item.

The exception is **the detail field's rich text and its images (R21)**. A list can declare a detail
field and an item can carry text in it, but it is stored and rendered as plain text, not HTML — so
`docs/adr/0018`'s sanitizer is specified and not yet written, and nothing embeds a picture in an item
yet. `todo_item.detail_html` is named for where it is going. Do not start storing HTML there without
the sanitizer.

`docs/design/0004` describes the whole feature and `docs/adr/0010`-`0021` record the decisions.

Keep this file true: when a decision here turns out wrong, update it in the same change.

## What this product is

`ha-todo` is a shared household todo list. **The deployment is the household** — there is one, every user
belongs to it, and there is no household entity (`docs/adr/0010`). A household has many **lists**
(grouping is a core feature, not a label); each list is seen by the people its creator grants access to, so
a shared grocery list and a private workout log are both ordinary. Members add **items** to a list, items
can be **assigned** to a member, and any member with access can mark any of that list's items complete.
Design every API and UI around "multiple people touching the same list concurrently", not around a
single-user todo app.

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

**Pin new dependencies to an exact, aged version.** A `minimumReleaseAge` would make a just-published
dependency fail `--frozen-lockfile` *inside the Docker build* while succeeding on the host — the worst
place to find out. It is **not** configured today (`pnpm config get minimumReleaseAge` is `undefined` on
pnpm 10.30.3), so the hazard is latent rather than active; pinning exactly is cheap insurance either way,
and a caret range on a shared lockfile invites drift onto a fresh release. Verify any dependency change
with a real `docker build web/` before opening the PR.

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

**Seed data is a repeatable migration (`db/seed/R__*.sql`), not a versioned one.** They are numbered
(`R__seed_01_demo_users.sql`, `R__seed_02_demo_lists.sql`) because **Flyway orders repeatable
migrations by description**: unprefixed, the lists seed sorted first and, on a fresh database, found no
users to own the lists - inserting nothing and failing silently. Flyway runs repeatable
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

**Item completion and assignment are the contended writes, and this is how they work.** Both `TodoList`
and `TodoItem` carry `@Version`, and every mutating request carries the version its view was drawn from,
checked *before* anything is mutated so the 409 is deterministic rather than dependent on flush timing.
An item conflict answers `{error, code: "STALE_ITEM", item}` — the item as it actually is, read in a
fresh transaction because the one that threw is being rolled back, and the prose names the other member.
The client takes the server's item outright and raises a snackbar; nothing is retried behind the
member's back. Two browser contexts cover it in `e2e/tests/test_items.py`.

Changing a list's children — its grants, its fields — calls `TodoListService.markChanged`, because
Hibernate will not dirty-check a parent whose own columns are untouched and a second editor would
otherwise never be told.

**A list's field configuration is typed rows, replaced by merging on id.** `list_field` (+
`list_field_choice`) rather than JSONB: the kinds are closed, and item values will reference a field and
a choice by foreign key. The `PUT` sends existing ids back and the service matches on them - recreating
the rows would orphan every item's costs, silently. Singleton kinds match by *kind* instead, because
there can only be one. Position uniqueness is `DEFERRABLE INITIALLY DEFERRED` so a reorder can rewrite
every position in one statement; partial unique indexes cannot be deferred, which is why those are
table constraints. See `docs/adr/0011` and `docs/adr/0012`.

**Boxed types in request records.** Jackson rejects a *missing* primitive in a record, so optional
`boolean`/`int` components are `Boolean`/`Integer` with a defaulting accessor (`isRequired()`,
`pointsOrZero()`). In a request that replaces a configuration, "not mentioned" is genuinely different
from "false" anyway.

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

**Receiving a shared link is Android-only, and the link becomes an item.** The manifest declares a
**GET** share target at `/share?title=&text=&url=`; `SharePage` normalises it (`src/share/sharedLink.ts`
— Chrome often puts the URL in `text`, not `url`) and the member picks which list it goes on, with the
one they touched most recently already chosen. No "Inbox" list is invented: a link landing somewhere
nobody looks is the failure that would replace. Nothing is parked on the device any more; `parkedLinks`
is gone, as `adr/0008` said it would be. iOS Safari does not implement Web Share Target, so an
installed iPhone app cannot appear in the share sheet — a platform limit, not a gap to work around. See
`docs/adr/0008` and `docs/adr/0020`.

**The signed-in UI is one shell with one sidebar.** Every authenticated route renders inside
`AppShell`, so `SideNav` mounts once. The breakpoint is `48rem`, named as `sm` in the MUI theme
(`breakpoints.values.sm = 768`) so it is one number in one place. Above it the sidebar is a permanent
`Drawer`; below it a temporary one behind a hamburger.

`AppShell` owns the **only** piece of layout state in the app and makes the **only** `useMediaQuery`
call. A drawer opens and closes, which is behaviour rather than placement, so the old CSS-only rule
could not express it — see `docs/adr/0015`. The rule is narrowed, not dropped: nothing else may acquire
layout state, and everything else responsive stays CSS. The drawer's open state is *derived* from the
pathname (`openedOver === pathname`), so a navigation closes it by construction, back button included.
The bottom bar is gone; a second navigation surface is how two layouts start to drift.

**Styling is the MUI theme, and nothing else.** `web/src/theme/theme.ts` is the single source of truth
for colour, radius and type; `index.css` is ~30 lines of box model, iOS text inflation, and the
safe-area and `dvh` units a theme cannot express. The old `:root` token block and every component class
were **deleted, not mapped** — two names for one colour is how the old one quietly survives. From here
on no component introduces a global class: use `sx`, a styled component, or a theme override. Icons are
imported one per path (`@mui/icons-material/ChecklistRounded`), never from the barrel, which is ~11,000
modules. See `docs/adr/0014`.

**Object storage is wired in; Redis is still deferred.** Profile pictures live in an S3 store
(`app.storage.*`, from `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET`), and the detail
field's images will. **SeaweedFS** provides that API — locally, in the integration tests, and on the
host — because MinIO's own images stopped allowing anonymous pulls; nothing in the code is specific to
either, and the client library is still minio-java used as a plain S3 client. See `docs/adr/0013` and
`docs/adr/0021`. Redis remains deferred on `docs/adr/0003`'s reasoning, and still needs a concrete need
and a new ADR; its likeliest trigger is a second backend instance, where in-memory sessions break.

**The app creates its own bucket at startup and fails closed.** `S3_BUCKET` is named explicitly per
environment, never derived from the Spring profile, so a misconfigured profile cannot put prod's images
in dev's bucket. An unreachable store stops the app from starting, deliberately: a deployment that
passes its health check and silently cannot save a picture is a bug report nobody can diagnose.
`scripts/deploy.sh` refuses without the four variables, before building anything.

**Image bytes are streamed through `GET /api/images/{id}`, never a presigned URL.** The store is not
published through either HTTPS front door, so a presigned URL is unreachable from a phone and blocked
as mixed content — and it is a capability that outlives a revoked grant. The type is decided by
sniffing the bytes, never the filename or `Content-Type`; PNG, JPEG and WebP only, and **SVG is refused
by name**, because an SVG served same-origin is script execution in our own security context.

**The frontend reaches the backend same-origin in every environment.** The SPA only ever uses relative
`/api` paths - never an absolute API URL, never `VITE_API_URL`. nginx in the `web` image proxies `/api` and
`/actuator/health` to `app:8080`, and the Vite dev server proxies the same paths, so cookies, `SameSite`
and CSRF behave identically everywhere and CORS is never needed. In dev and prod the `app` service
publishes **no** host port at all (`expose` only); locally it is bound to `127.0.0.1` purely so `pnpm dev`
can proxy to it. Do not add a published app port or an absolute API base URL - that reintroduces a
cross-origin path the session cookie is not configured for.

**Frontend** is a React SPA consuming the REST API; keep server state in TanStack Query so a list
refetches cleanly after another member's change, and keep the list-grouping view model separate from the
item components.

**`api/client.ts` is split per feature** — `api/http.ts` holds the axios instance and the error helpers
(`isUnauthorized`, `isConflict`, `errorMessage`); `auth.ts` / `admin.ts` / `profile.ts` hold their own DTOs
and calls. There is deliberately **no barrel re-exporting them**: tests spy by module namespace
(`import * as authApi; vi.spyOn(authApi, 'fetchCurrentUser')`), and a re-exported live binding is exactly
what `vi.spyOn` cannot intercept. Add a feature's module; never add an index.

**Only the caller's own routes take no id.** `/api/me/*` acts on the session's principal, so there is no
user id in the path to tamper with. Services reach the caller through `CurrentUserProvider`, never a
threaded username — see `docs/adr/0016`.

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

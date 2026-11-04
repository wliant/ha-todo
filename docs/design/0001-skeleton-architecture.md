# 0001 - Skeleton architecture

Status: accepted
Date: 2026-11-04
Implements: [requirements/0001](../requirements/0001-shared-household-todo.md) R7 only

## Scope

This design covers the skeleton that is actually built: the monorepo layout,
request path, authentication, and the local/dev/prod story. The household / list
/ item domain (R1-R6) is deliberately out of scope and lands on its own feature
branch.

## Shape

```
browser ──> nginx (web image) ──┬── /            static SPA bundle, SPA fallback
                                └── /api, /actuator  proxied to app:8080
                                                      │
                                       Spring Boot (app image)
                                       controller → service → repository
                                                      │
                                              provided Postgres
```

Same-origin by design, in **every** environment: nginx proxies `/api` so the
session cookie and CSRF token work without CORS, and the Vite dev server proxies
the same paths so development behaves identically to a deployed environment. The
SPA only ever issues relative `/api` requests.

This is enforced by what is published, not just by convention:

| | `app` published port | `web` published port | browser reaches API via |
|---|---|---|---|
| local (docker) | `127.0.0.1:8080` only | `5173` | `http://localhost:5173/api` |
| local (`pnpm dev`) | `127.0.0.1:8080` only | `5173` (Vite) | Vite proxy to `/api` |
| dev / prod | **none** (`expose` only) | `WEB_PORT` | nginx `/api` |

In dev and prod an external TLS terminator fronts that published port
(`https://todo-dev.wliant.org` → 5987, `https://todo.wliant.org` → 5986). The
session and CSRF cookies are `Secure` there, so Spring must see the browser's
real scheme: `server.forward-headers-strategy: framework` reads
`X-Forwarded-Proto`, and nginx passes the incoming value through rather than
overwriting it with `$scheme`, which is always `http` inside the container.

Health checks follow the same path: `scripts/deploy.sh` polls
`http://localhost:${WEB_PORT}/actuator/health` on the target rather than the
app port, because there is no app port to poll.

## Request path in the backend

`controller → application service → domain → Spring Data JPA repository`.

The application service is the transaction boundary and the place authorization
is enforced. Controllers translate HTTP; they do not make access decisions.

**Household scoping (for when the domain arrives).** Every read and write will be
checked against the caller's household membership in the service layer. A list id
in a URL is never proof of access, and the UI hiding a list is not a control.

**Concurrency.** Completion and assignment are the contended writes. Entities
will carry `@Version`, and a conflicting write returns a conflict the frontend
reconciles by refetching, rather than last-write-wins.

## Authentication

Session-based: `POST /api/auth/login` takes JSON, authenticates against
`app_user` via `DaoAuthenticationProvider` with BCrypt, rotates the session id
to defeat fixation, and stores the context in the HTTP session. `GET /api/me`
returns the signed-in member. Anonymous API calls get **401**, never a redirect
to a login page.

CSRF stays enabled because authentication rides on a cookie. The token is issued
in a JS-readable `XSRF-TOKEN` cookie via `GET /api/auth/csrf`; the client echoes
it in `X-XSRF-TOKEN`, which is axios's default behaviour for same-origin
requests.

Rationale and alternatives: [adr/0002](../adr/0002-session-auth-postgres-users.md).

## Frontend state

TanStack Query owns server state. The signed-in member is a query (`null` means
signed out, `undefined` means not yet known) so route guards can distinguish
"checking" from "denied". Because another member can change a list at any moment,
refetch-on-focus is the default rather than an opt-in.

## Schema and data

Flyway owns the schema; JPA is set to `validate` and never generates DDL.
Migrations live in `classpath:db/migration`. Demo accounts live in a separate
`classpath:db/seed` location, included in the local and dev profiles and
excluded from prod, so seed data can never reach production.

## Environments

| | local | dev | prod |
|---|---|---|---|
| Postgres | throwaway container | provided | provided |
| Images | built from source | from registry | from registry |
| Seed data | yes | yes | **no** |
| e2e target | while developing | **yes** | no |

Configuration is entirely environment variables supplied by the compose file for
that environment. No hostnames or secrets in the repo.

## Testing

- **Unit** - fast, no Docker: `./gradlew test`, `pnpm test`.
- **Integration** - real Postgres via Testcontainers: `./gradlew integrationTest`.
  Covers the login path end to end, including CSRF rejection.
- **E2E** - `e2e/`, pytest + Playwright through the real UI against a deployed
  environment, with namespaced seed-and-teardown test data.

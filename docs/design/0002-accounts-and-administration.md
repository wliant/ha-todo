# 0002 - Accounts and administration

Status: accepted
Date: 2026-11-04
Implements: [requirements/0002](../requirements/0002-accounts-and-administration.md) R8-R12
Builds on: [design/0001](0001-skeleton-architecture.md)

## Scope

Account identity, the admin's user administration surface, and the pick-a-user
sign-in screen. The household / list / item domain (R1-R6) is still out of
scope; every user here is implicitly in the one household.

Decisions behind this design:
[adr/0005](../adr/0005-pick-a-user-login-with-optional-passwords.md) (identity,
optional passwords, public roster),
[adr/0006](../adr/0006-admin-account-from-environment.md) (admin bootstrap).

## Data

```
app_user
  id             uuid pk
  username       varchar(50)  not null   -- unique on lower(username)
  display_name   varchar(100) not null
  password_hash  varchar(100) null       -- NULL = no password required
  role           varchar(16)  not null   -- 'ADMIN' | 'MEMBER'
  enabled        boolean      not null
  created_at / updated_at
```

Two invariants are held by the database, not by application code:

- `ux_app_user_username` - unique on `lower(username)`, as `email` was.
- `ux_app_user_single_admin` - partial unique index on `(role) WHERE role = 'ADMIN'`.
  One admin is a requirement (R8), so a second one is unrepresentable rather
  than merely unreachable.

`password_hash IS NULL` is the single source of truth for "no password
required"; there is no companion boolean. See adr/0005 for why.

### Migration path

`V901__accounts_and_roles.sql` in `db/migration` renames `email` to `username`,
backfills existing values to the part before the `@`, drops `NOT NULL` from
`password_hash`, and adds `role` plus both indexes.

**It is numbered 901, not 2**, and that is the interesting part. The retired
demo seed was a *versioned* migration at `V900`, so on any database that had
already applied it - local and dev both had - a schema migration at version 2 is
out of order and Flyway refuses to run it. This was found by deploying the local
stack, not by reading: the app failed to start with "Detected resolved migration
not applied to database: 2".

The convention is what was wrong, so the convention changed rather than being
worked around with `outOfOrder=true`:

- The seed is now a **repeatable** migration, `db/seed/R__seed_demo_users.sql`.
  Flyway runs repeatable migrations after every versioned one, and re-runs them
  when their content changes. Seed data can therefore be edited freely, can
  never block a schema change, and no longer needs a reserved version range.
- The schema series resumes **above** the retired mark, at V901. New schema
  migrations continue from V902.
- The deleted `V900` is still in the history table of existing databases, so the
  two profiles that carry the seed location set
  `spring.flyway.ignore-migration-patterns: "*:missing"`. Prod never applied it
  and keeps strict validation.

This path was exercised on a database that had already applied V900: V901
carried the old rows over (`ana@example.com` to `ana`), and the repeatable seed
then ran last and normalised them - which is what strips the password hash Ben
arrived with.

The seed demonstrates both sign-in paths: `ana` requires a password
(`password123`), `ben` requires none.

## Backend

```
GET    /api/auth/users      permitAll    roster for the sign-in screen
POST   /api/auth/login      permitAll    { username, password? }
GET    /api/me              authenticated
POST   /api/auth/logout     authenticated
GET    /api/admin/users     ROLE_ADMIN
POST   /api/admin/users     ROLE_ADMIN   { username, displayName, password? }
DELETE /api/admin/users/{id} ROLE_ADMIN
```

`/api/admin/**` is gated by `hasRole("ADMIN")` in the filter chain *and* the
service layer refuses to delete the admin row regardless of caller - the
standing rule from design/0001 is that the controller is not where access
decisions live, and R12 is an invariant, not a permission.

**Login** (`LoginService`) resolves the user, then branches on
`password_hash`: present means the existing `AuthenticationManager` path,
NULL means the service constructs an authenticated token itself and ignores any
submitted password. Either way the session id is rotated and the context saved
exactly as before. A missing user and a wrong password are both an opaque 401.

Authorities are derived from `role` as `ROLE_ADMIN` / `ROLE_MEMBER`, replacing
`NO_AUTHORITIES`. `GET /api/me` and the login response now include the role so
the SPA knows whether to offer the admin screen.

**The roster is filtered, not whole rows.** `GET /api/auth/users` returns only
id, username, display name, role and `passwordRequired`, for enabled users. It
must never grow a field that is not needed to draw the sign-in screen - it is
readable by anyone who can reach the deployment.

**Admin bootstrap** is an `ApplicationRunner` (`AdminAccountInitializer`) that
reconciles the single admin row from `ADMIN_USERNAME` / `ADMIN_PASSWORD` on
every startup, and fails startup if the password is absent. See adr/0006.

## Frontend

```
/login   LoginPage    roster → pick a user → (password?) → session
/lists   ListsPage    members; links to /admin when role is ADMIN
/admin   AdminPage    ROLE_ADMIN only; list, add, delete users
```

`LoginPage` is a two-state screen rather than a form: the roster as a list of
buttons, and - once a password-requiring user is chosen - that user's name with
a single password field and a way back to the list. Choosing a passwordless user
submits immediately. Nothing in the UI reveals *why* a sign-in failed.

`AdminRoute` wraps `ProtectedRoute` and additionally requires
`user.role === 'ADMIN'`, sending members to `/lists`. It is a convenience, not a
control - the control is `hasRole("ADMIN")` on the server.

Server state stays in TanStack Query. The roster is its own query, invalidated
after an add or delete so the sign-in screen cannot show a user the admin just
removed. On delete the admin's own row is not offered, matching R12.

## Testing

- **Unit (backend)** - the authority mapping and the passwordless `UserDetails`
  placeholder; `LoginService`'s branch, both directions; the initializer's
  create / update / fail-closed cases; the admin-undeletable rule.
- **Unit (frontend)** - roster renders, passwordless click signs in with no
  password field, password-requiring click reveals the field and submits, admin
  add/delete call the API and invalidate.
- **Integration** - against real Postgres: passwordless login succeeds with no
  password and with a junk password; a member calling `/api/admin/users` gets
  403 while the admin gets 200; deleting the admin is refused; the roster is
  readable anonymously and leaks no hash.
- **E2E** - the pick-a-user screen as a member actually meets it, for both
  password and passwordless users, plus the admin adding and removing a user and
  seeing the sign-in list change.

# 0006 - Admin account from the environment, ensured at startup

Status: accepted
Date: 2026-11-04
Implements: [requirements/0002](../requirements/0002-accounts-and-administration.md) R8

## Context

Exactly one admin account exists, it always needs a password, and it is the only
account that can add or delete users - so it has to exist before anyone can do
anything, including on a brand-new prod database that no seed migration ever
touches (seed data is excluded from prod by
[design/0001](../design/0001-skeleton-architecture.md)).

That makes bootstrapping the admin a chicken-and-egg problem with three bad
answers available: ship a default credential, hand-run SQL on the server, or let
the first visitor claim the account.

Deployment constraints that bound the choice: configuration is environment
variables injected by the compose file, no secrets in the repository, and
**nobody opens an interactive SSH session to a server to do deployment work by
hand** - if a step is missing it goes in a script.

## Decision

`ADMIN_USERNAME` and `ADMIN_PASSWORD` come from the environment, and an
`AdminAccountInitializer` reconciles the admin row on every startup:

- no admin row: create it, BCrypt-hashing the configured password;
- admin row present: update its username and display name, and re-hash the
  password if the configured one no longer matches the stored hash;
- `ADMIN_PASSWORD` blank or missing: **fail startup** with an explicit message.

Failing closed is deliberate. The alternative - start up with a disabled or
unset admin - produces a running, apparently healthy deployment that nobody can
administer, discovered later.

The reconcile is idempotent, so it is also the rotation path: change
`ADMIN_PASSWORD` in `deployment/.env.<env>` and redeploy. No SSH, no SQL, no
extra script.

`application-local.yml` sets a default of `admin` / `admin` so the local stack
comes up with no setup. Local only - dev and prod inherit the no-default
behaviour from `application.yml` and refuse to start without the variables.

## Consequences

- A fresh prod database is administrable immediately after the first deploy,
  with a credential that was never in git.
- The admin password is in `deployment/.env.<env>` on the deploying machine,
  alongside `DB_PASSWORD`, which is already the trust boundary for deployment.
  It is also visible in the container's environment to anyone who can
  `docker inspect` on the target - the same exposure every other secret in this
  stack already has.
- A typo in `ADMIN_USERNAME` renames the existing admin rather than creating a
  second one, because the row is found by role. That is the intended behaviour
  and the reason the lookup is not by username.
- Startup now depends on two more variables. `scripts/deploy.sh` already polls
  `/actuator/health` until UP, so a missing variable surfaces as a failed deploy
  rather than a silent one.
- The admin's password is the only thing standing between a visitor and the
  ability to add accounts, given
  [adr/0005](0005-pick-a-user-login-with-optional-passwords.md). It should be a
  real password, not `admin`, anywhere but local.

## Alternatives considered

**Seed the admin in a Flyway migration with a known hash.** Simple and needs no
new code. Rejected: it puts a working credential for the most privileged account
in git, and it would reach prod, where the seed location exists precisely to be
excluded. "Force a change on first login" does not help - the window is between
deploy and first login, which is exactly when nobody is watching.

**Admin authenticated purely from config, not a database row.** Keeps the
privileged credential out of Postgres entirely. Rejected because R11 puts the
admin in the same pick-a-user list as everyone else; a parallel auth path and a
synthetic roster entry would mean two code paths for the one account that most
needs to be boring.

**First-run claim flow.** Whoever reaches the app first sets the admin password.
Rejected: on a LAN deployment "whoever reaches it first" is not necessarily the
owner, and it fails silently and permanently if it loses that race.

**An interactive `scripts/create-admin.sh` over SSH.** Rejected by the standing
rule against hand-run deployment steps; the initializer makes it unnecessary.

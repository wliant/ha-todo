# 0001 - Tech stack and monorepo layout

Status: accepted
Date: 2026-11-04

## Context

Greenfield household todo app. One developer, two environments (dev and prod),
self-hosted infrastructure with a private Docker registry on the LAN. Needed a
stack decision before any code existed.

## Decision

- **Monorepo** with `app/`, `web/`, `e2e/`, `deployment/`, `scripts/`, `docs/`.
- **Backend**: Spring Boot 4.1.2 on Java 21, Gradle with the Groovy DSL.
- **Frontend**: React 19 + TypeScript on Vite, pnpm, oxlint (the Vite template's
  default linter), Vitest for unit tests.
- **Database**: Postgres, schema owned by Flyway.
- **E2E**: a standalone pytest + Playwright project that imports nothing from
  `app/` or `web/`.

Java 21 is reached through **Gradle toolchain auto-provisioning** rather than a
system JDK: no JDK 21 was installed on the development machine (only 17 and 27),
and auto-provisioning keeps the declared target honest on any machine.

## Consequences

- One checkout, one branch, one PR per change across backend and frontend.
- `app/` carries its own Gradle wrapper, and `web/` its own lockfile, so each
  module builds standalone and the Docker builds stay simple.
- There is no root pnpm workspace: with a single JavaScript package it would add
  indirection and move the lockfile away from the image build context. Revisit
  if a second JS package appears.
- Boot 4 renamed starters (`spring-boot-starter-webmvc`, granular `*-test`
  modules) and moved `TestRestTemplate` into `spring-boot-resttestclient`, which
  also needs `spring-boot-restclient`. Expect Boot-3-era snippets not to apply.

## Alternatives considered

- **Maven** - more conventional for Spring Boot, but Gradle was preferred for
  incremental builds and the toolchain feature.
- **Polyrepo** - rejected; a two-person-scale project does not need the
  coordination cost of cross-repo changes.
- **Next.js / SSR** - rejected; this is an authenticated app behind a login with
  no SEO or first-paint requirement, so a static SPA plus nginx is simpler.

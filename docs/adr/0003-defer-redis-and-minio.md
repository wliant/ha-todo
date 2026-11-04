# 0003 - Defer Redis and MinIO until something needs them

Status: superseded by [0013](0013-minio-for-images-served-through-the-api.md)
for MinIO only - the decision to defer Redis stands and is unaffected.
Date: 2026-11-04

## Context

Redis and MinIO are available as provided infrastructure, and the project brief
lists them as usable "if needed". The skeleton has no cache, no cross-instance
session sharing, no pub/sub, and no file uploads.

## Decision

Neither is wired in. The local infra compose file carries both as commented-out
service definitions pointing back at this ADR, and `.env.example` carries their
connection variables commented out.

Adding either requires a concrete, named need and a new ADR.

The two foreseeable triggers:

- **Redis** - running more than one backend instance, which breaks in-memory
  sessions ([0002](0002-session-auth-postgres-users.md)); or live list updates
  needing pub/sub between instances.
- **MinIO** - items gaining photo attachments.

## Consequences

- Fewer moving parts to run, deploy, monitor, and reason about.
- The commented-out definitions keep the eventual wiring cheap without pretending
  the dependency exists today.
- A future horizontal scale-out is a deliberate decision with a migration, not an
  accident.

## Alternatives considered

- **Wire both up now** - rejected. Unused infrastructure still has to be
  deployed, secured, and kept alive, and an unused dependency rots: the first
  real use would find configuration that was never exercised.
- **Redis-backed sessions from day one** - defensible, and the likeliest of the
  two to be needed. Rejected because a single backend instance is the plan and
  Spring Session can be added without touching application code.

# 0013 - MinIO for images, served through the API

Status: accepted
Date: 2026-11-05
Supersedes: [0003](0003-defer-redis-and-minio.md) - for MinIO only; Redis stays
deferred on that ADR's reasoning.
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md)
R21 (detail images), R32

## Context

adr/0003 deferred MinIO until something needed it, and named photo attachments as
the foreseeable trigger. Two requirements now pull it in: R32 gives every member a
profile image, and R21's detail field is rich text with images in it. Both are
binary data that does not belong in Postgres rows or in the image build.

The deferral's condition is met and its mechanism is ready: a commented service in
`deployment/docker-compose.infra.yml`, commented `MINIO_*` variables in
`deployment/.env.example`, both pointing here. MinIO is also already running on
`192.168.1.248` alongside the provided Postgres, so dev and prod need a bucket
each, not an installation.

The non-obvious question is not whether to use MinIO. It is how bytes reach the
browser, because the deployment has a shape that rules out the usual answer.

## Decision

**MinIO is enabled, one bucket per environment, named explicitly.** `MINIO_BUCKET`
is its own environment variable - `ha-todo-local`, `ha-todo-dev`, `ha-todo-prod` -
and is never derived from the active Spring profile. A profile misconfiguration is
a plausible mistake; it must not be able to put prod's images in dev's bucket.

**The app creates its own bucket at startup.** A `MinioBucketInitializer`
`ApplicationRunner` calls `bucketExists` and `makeBucket`, and **fails startup** if
MinIO is unreachable - the same fail-closed choice as `AdminAccountInitializer`
(adr/0006), for the same reason: a deployment that passes its health check and
silently cannot store an image is worse than one that refuses to start. Nobody
creates a bucket by hand over SSH, because adr/0004 forbids it and this makes it
unnecessary. `scripts/deploy.sh` checks the MinIO variables are set before building,
alongside its existing `ADMIN_PASSWORD` check.

**Bytes are streamed through the backend at `GET /api/images/{id}`. Presigned URLs
are not used.** Three reasons, in order of how hard they are to work around:

1. MinIO is not published through either HTTPS front door. A presigned URL would be
   `http://192.168.1.248:9000/...` - unreachable from a phone off the LAN, and
   blocked as mixed content on `https://todo.wliant.org`.
2. It violates the standing rule that the SPA only ever uses relative `/api` paths
   (design/0001). That rule is why cookies, `SameSite` and CSRF behave identically
   in every environment and CORS is never needed.
3. A presigned URL is a bearer capability with its own lifetime. Per-list access is
   the point of adr/0010; handing out URLs that outlive a revoked grant would undo
   it in the one place where the data is most personal.

The endpoint checks entitlement per purpose: an `AVATAR` is readable by any
authenticated member, since avatars appear next to names everywhere; a `DETAIL`
image requires `readable` on the list that owns the item it belongs to. It sets
`Cache-Control: private, max-age=86400` and a strong `ETag` derived from the image
id. Ids are immutable - a replacement image is a new row and a new id - so the
`ETag` can never be stale, and a device fetches each image about once.

**`io.minio:minio`, pinned to an exact version, behind an `ImageStorage` interface.**
The need is `putObject`, `getObject`, `statObject`, `removeObject`, `bucketExists`,
`makeBucket`.

**Uploads are validated by content, not by claim.** The type is sniffed from the
bytes' magic number, not from the `Content-Type` header or the filename, and must
be PNG, JPEG or WebP. **SVG is rejected by name**: an SVG served from our own
origin is script execution in our own security context, which is a stored XSS
wearing an avatar. The allowlist is repeated as a `CHECK` on `stored_image` so the
column cannot hold a type the endpoint would be unwise to serve.
`spring.servlet.multipart.max-file-size` is 5MB, and `web/nginx.conf` needs
`client_max_body_size 6m` in `location /api/` - nginx's 1MB default would otherwise
reject an upload with an HTML error page the SPA cannot parse.

## Consequences

- Redis remains deferred, and adr/0003's reasoning for it is untouched. This ADR
  supersedes only the MinIO half.
- **A MinIO outage takes the whole app down**, including a chore list that needs no
  images. Accepted: MinIO, Postgres and the app all run on the same host, so an
  outage of one is rarely an outage of only one, and a running app that cannot
  store an avatar is a bug report nobody can diagnose. The trade is reversible by
  changing the initializer to warn instead of throw.
- Image traffic passes through the JVM and its heap. Irrelevant at household scale
  with avatars and a few detail images, and bounded by the 5MB cap and the `ETag`.
  If it ever matters, the fix is an nginx-level cache in front of `/api/images/`,
  not presigned URLs.
- Local development now needs MinIO running. `docker-compose.infra.yml` uncomments
  it and `scripts/dev-up.sh` waits for it, so `./scripts/dev-up.sh` is still the
  only command.
- Integration tests run a `MinIOContainer`, consistent with using real Postgres
  rather than mocking the data layer.
- A crash between `putObject` and commit leaves one unreferenced object. Avatar
  replacement deletes the old object in an `afterCommit` hook, so a rolled-back
  transaction never deletes a live one. Detail images are genuinely two-step and
  will leave orphans; `stored_image` is the inventory needed to sweep them, and a
  sweeper is written only if the bucket actually grows.
- A MinIO-specific client is lock-in. Confined to `MinioImageStorage`.

## Alternatives considered

- **Keep deferring; store images as `bytea` in Postgres.** No new infrastructure
  and transactional with the row that references it, which removes the orphan
  problem entirely. Rejected because MinIO is already running and already
  anticipated by adr/0003: putting megabytes of binary into the rows that back
  every list query bloats backups and the shared buffer cache to avoid a dependency
  the deployment already has.
- **AWS SDK v2 (`software.amazon.awssdk:s3`).** The portable choice, and the usual
  one. Rejected: a much larger dependency tree whose main payoffs are provider
  portability and presigned URLs, and this ADR declines presigned URLs on purpose.
  Buying generality you have decided not to use is how an unused dependency rots -
  adr/0003's own argument.
- **Presigned URLs with a short expiry.** The standard way to keep bytes out of the
  application. Defeated by the deployment's shape, as above, before the security
  argument is even reached.
- **A mounted volume on the app container.** No client library at all. Rejected: it
  ties the app to one host, puts user data outside the backup story that covers
  Postgres and MinIO, and is a worse version of the object store already running.
- **Creating the buckets by hand, once, over SSH.** Two commands, never repeated.
  Rejected on adr/0004's rule - if a step is missing, it goes in the code, not in
  somebody's shell history - and because an undiscoverable manual prerequisite is
  how a fresh environment fails mysteriously.

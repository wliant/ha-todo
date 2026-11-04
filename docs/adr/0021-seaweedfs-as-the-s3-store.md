# 0021 - SeaweedFS as the S3 store

Status: accepted
Date: 2026-11-05
Amends: [0013](0013-minio-for-images-served-through-the-api.md) - everything it
decided stands except which server provides the S3 API.

## Context

adr/0013 enabled object storage for profile pictures and the detail field's
images, and chose MinIO because MinIO was what ran on the host. Everything else
it decided - bytes streamed through `GET /api/images/{id}` rather than presigned
URLs, one explicitly-named bucket per environment, the app creating its own
bucket at startup and failing closed if the store is unreachable - was about the
shape of the integration, not about the vendor.

Implementing it ran into something that ADR could not have known: **MinIO's
images on Docker Hub now refuse anonymous pulls.** Every tag, not only
`:latest`. That is fine for the server already running on the LAN, which nobody
needs to pull again, and fatal for the two places this repository needs an image
of its own:

- `deployment/docker-compose.infra.yml`, which exists so a developer can run
  the stack without the provided infrastructure.
- The integration tests, which run a real store rather than a mock because that
  is this project's standing position on the data layer.

Chainguard's rebuild of MinIO was tried first and works, but it is a hardened
image with no `curl` or `wget`, so the compose healthcheck silently always
failed - and a healthcheck that always fails lets the application start before
the store is ready, which matters precisely because the application fails
closed. Its free tier also publishes only `:latest`, which would have made it
the one unpinned image in the project.

## Decision

**SeaweedFS provides the S3 API**, in local development, in the integration
tests, and on the host. `chrislusf/seaweedfs` pulls anonymously and publishes
version tags, so both places pin one (`4.50`).

**The application does not know the difference.** It speaks S3, and
`app.storage.*` holds an endpoint, credentials and a bucket name. Everything
adr/0013 decided about *how* images are stored and served is unchanged, and the
integration tests that prove it pass against SeaweedFS exactly as written.

**The MinIO Java client stays**, now as what it always was - a plain S3 client.
It is pinned, aged, already integrated, and works against SeaweedFS unmodified.
Swapping it for the AWS SDK would buy a more honest package name at the cost of
a rewrite of the one class that touches the store, and adr/0013's reasons for
preferring a small client over the S3 SDK's dependency tree have not changed.
The class is named `S3ImageStorage` rather than for either vendor.

**The configuration is named for the protocol, not the product**: `S3_ENDPOINT`,
`S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`. Variables named `MINIO_*` would
now be describing something that is not MinIO.

## Consequences

- A developer can bring the whole stack up with `./scripts/dev-up.sh` and no
  registry credentials, which is what `deployment/docker-compose.infra.yml`
  exists for.
- Both containers in the integration suite are pinned again, so a new upstream
  release cannot change what the suite ran against.
- SeaweedFS binds its S3 port to the container's interface rather than to
  loopback, so the compose healthcheck probes `$(hostname -i)`. A `localhost`
  probe is refused however healthy the server is - which is exactly the silent
  always-failing healthcheck that made Chainguard's image unusable, and it would
  have been just as silent here.
- S3 compatibility is now a thing this project depends on rather than a
  coincidence. Nothing in the code is MinIO-specific, so the store can change
  again; the integration tests against a real one are what would catch it if a
  future store's S3 API diverged.
- The server on the LAN has to be a SeaweedFS instance with its S3 API exposed,
  on 8333 rather than MinIO's 9000. That is a deployment change, not a code one.
- The MinIO client library's name no longer describes what it talks to, which is
  mildly confusing to a reader. The class name and this ADR are the mitigation.

## Alternatives considered

- **Authenticate to Docker Hub for `minio/minio`.** Keeps adr/0013 exactly as
  written. Rejected because it puts a credential in the path of
  `./scripts/dev-up.sh` and of the test suite, for an image that is only ever
  used by developers - and the credential would have to be distributed to
  anyone who ever works on this.
- **Chainguard's MinIO rebuild.** Genuinely MinIO, actively maintained, pulls
  anonymously. Rejected on the two problems above: no shell utilities for a
  healthcheck, and `:latest` only.
- **Bitnami's archived MinIO image.** Works, pulls anonymously. Rejected because
  it is explicitly archived - choosing a dependency that is already announced as
  unmaintained is choosing a future migration.
- **Mirror a pinned `minio/minio` into the registry at `192.168.1.248:5000`.**
  Restores pinning and the official image, and the registry already exists.
  Rejected as making a developer's local stack depend on being on that LAN,
  which is the one thing the throwaway compose file is meant to avoid.
- **Keep MinIO on the host and use something else only for tests.** The suite
  would then be proving S3 compatibility against a store nobody runs. The point
  of testing against a real one is that it is the real one.

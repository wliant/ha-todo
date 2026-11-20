# 0004 - Scripted SSH deployment, never hand-run

Status: accepted
Date: 2026-11-04

## Context

Two self-hosted environments, a private Docker registry on the LAN
(`192.168.9.9`), and Docker Compose on each target host. No CI runners are set
up. The failure mode to avoid is the familiar one: a deploy that lives only in
someone's shell history, where the server drifts from the repo and nobody can say
what is actually running.

## Decision

Deployment is a script run from a developer machine:

1. `scripts/build.sh` builds both images locally.
2. `scripts/push.sh <env>` tags and pushes them to the registry.
3. `scripts/deploy.sh <env>` does both, copies the compose file and env file to
   the target, then `docker compose pull && up -d` **over SSH**, and polls
   `/actuator/health` until the service reports UP.

Rules this encodes:

- **Nobody SSHes into a server to run deployment steps by hand.** All SSH goes
  through one `ssh_run` helper in `scripts/lib/common.sh`. A missing step gets
  added to the script.
- Images are never built on the target host; it only pulls.
- Hostnames, deploy directories and credentials live in `deployment/.env.<env>`,
  which is gitignored. `deployment/.env.example` documents every variable.
- `deploy.sh prod` requires typing `deploy prod` to proceed.

## Consequences

- The deploy is reproducible and reviewable, and the repo is the source of truth
  for what runs where.
- A deploy depends on the developer's machine having Docker, network reach to the
  registry, and SSH access. Moving to CI later means re-pointing the same
  scripts, not rewriting them.
- Rollback is `scripts/deploy.sh <env> <older-tag>`, which is why `IMAGE_TAG` is
  an explicit argument rather than always `latest`.

## Resolved details

Confirmed 2026-11-04:

- Registry is `192.168.9.9:5000`, plain HTTP (`registry:3`). Both the developer
  machine and the target already list it under `insecure-registries`.
- Both environments deploy to `deploy@192.168.9.9` and **share**
  `/srv/ha-todo`. They stay separate because the compose files,
  env files, project names (`ha-todo-dev` / `ha-todo-prod`) and published ports
  all differ: dev `5987`, prod `5986`.
- Compose must be invoked on the target with `--env-file .env.<env>`. The
  `env_file:` key populates the container environment only; compose's own
  `${...}` interpolation reads the shell environment and `--env-file`. Without
  it the image name resolves to `/ha-todo-app:tag` and the pull fails.

## Alternatives considered

- **Hand-run `docker compose` on the host** - the status quo being rejected; see
  Context.
- **A CI pipeline (GitHub Actions) building and deploying** - better end state,
  but needs a runner with LAN access to the registry and target hosts. The
  scripts are the prerequisite either way.
- **Pushing source to the host and building there** - slower, needs a toolchain
  on every host, and makes "what is running" depend on the host's build cache.

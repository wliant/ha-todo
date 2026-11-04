# ha-todo

A shared household todo list. Family members add items to groupable lists,
assign them to each other, and mark them complete.

Working on this repo? Read [CLAUDE.md](CLAUDE.md) - it is the operational spec
for layout, commands, environments, deployment and workflow.

## Layout

| Path          | What it is |
|---------------|------------|
| `app/`        | Spring Boot backend (Gradle, Java 21) |
| `web/`        | React + TypeScript frontend (Vite, pnpm) |
| `e2e/`        | Standalone pytest + Playwright suite, run against a deployed environment |
| `deployment/` | Compose files and environment templates |
| `scripts/`    | Build and deploy entrypoints; the only supported deploy path |
| `docs/`       | Requirements, designs, architecture decision records |

## Quick start

```bash
./scripts/dev-up.sh      # postgres + app + web on Docker
# web http://localhost:5173, api http://localhost:8080
# demo sign-in: ana@example.com / password123
./scripts/dev-down.sh
```

Per-module commands live in [CLAUDE.md](CLAUDE.md#commands).

## Status

Skeleton. Authentication, health, build, local stack, deploy scripts and the
test harnesses are in place; the household / list / item domain is not yet
implemented.

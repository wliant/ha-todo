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
./scripts/dev-up.sh      # postgres + object store + app + web on Docker
# web http://localhost:5173, api http://localhost:8080
# sign-in is a list of people: pick Ana (password123), or Ben - no password
# admin / admin administers accounts
./scripts/dev-down.sh
```

Per-module commands live in [CLAUDE.md](CLAUDE.md#commands).

## Status

Built and running on dev. Accounts, the installable shell, lists with per-list
access, items with the fields each list declares, completion with points and a
note about how the work went, standing instructions that make their own items,
and a dashboard that reads across every list a member can see.

See [CLAUDE.md](CLAUDE.md#status) for what that means in detail, and `docs/` for
the requirements, designs and decisions behind it.

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
| `hass/`       | Standalone Home Assistant custom integration, talking to a deployment over HTTP |
| `deployment/` | Compose files and environment templates |
| `scripts/`    | Build and deploy entrypoints; the only supported deploy path (`deploy-hass.sh` for `hass/`) |
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
and a dashboard that reads across every list a member can see. The household's
automation reaches it too: `hass/` is a Home Assistant integration that signs in
as an ordinary member and sees only what has been shared with it.

See [CLAUDE.md](CLAUDE.md#status) for what that means in detail, and `docs/` for
the requirements, designs and decisions behind it.

## Security model — read this before deploying it

This is a family's own deployment, and it trades authentication strength for
usability on purpose. The consequences are recorded in
[requirements/0002](docs/requirements/0002-accounts-and-administration.md) and
[adr/0005](docs/adr/0005-pick-a-user-login-with-optional-passwords.md):

- **The sign-in roster is public.** The login screen is a list of people, so
  anyone who can reach the app sees the household's display names and faces.
- **A member may have no password at all**, in which case signing in as them is
  available to any visitor. There is no rate limiting and no lockout.
- **Reaching the app is therefore the only control.** Only the admin account —
  the one account that can change who exists — always has a password.

So do not put a deployment of this on the open internet. Put it on a LAN, behind
a VPN, or behind authentication at the front door. Credentials and hostnames in
this repo and in `docs/` are **placeholders**; the real ones live only in the
uncommitted `deployment/.env.dev` / `.env.prod`.

## License

[MIT](LICENSE).

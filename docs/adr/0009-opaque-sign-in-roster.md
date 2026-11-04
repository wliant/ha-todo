# 0009 - The sign-in roster says only who exists

Status: accepted
Date: 2026-11-04
Amends: [0005](0005-pick-a-user-login-with-optional-passwords.md) - the
password-optional model stands; the roster payload and the client flow do not.

## Context

adr/0005 made the sign-in screen a list of people and had
`GET /api/auth/users` serve `id`, `username`, `displayName`, `role` and
`passwordRequired`. The client used `passwordRequired` to decide whether to show
a password field, and the screen printed hints: "Admin · needs a password",
"Needs a password", "Tap to sign in".

That endpoint is `permitAll` - it has to be, the sign-in screen cannot be drawn
without it. So the hints are published to anyone who can reach the deployment.
They answer two questions an unauthenticated visitor should have to work for:
which account administers the household, and which accounts have no secret at
all. The second is worse than it sounds: it is a list of free passes, sorted by
name.

The exposure of *who exists* is inherent to pick-a-user sign-in and was accepted
in requirements/0002. The exposure of *what each account is worth attacking for*
was never required by anything; it was an implementation convenience for the
client.

## Decision

`LoginUserResponse` carries `id`, `username` and `displayName` - nothing else -
and the sign-in rows render the display name alone (R17).

The client stops knowing whether a password is needed and discovers it by
attempting the login: pick a person, post with no password, and treat a **401**
as "reveal a password field for this person". A password-required account already
fails that post, so no backend branch is added.

What adr/0005 decided is untouched: `password_hash IS NULL` is still the single
source of truth for "no password required", `LoginService.authenticate` still
branches on it in one explicit `if`, and the admin still always has a password.
`GET /api/admin/users` still carries `role` and `passwordRequired`, because it is
`hasRole("ADMIN")` and the admin screen needs them.

## Consequences

- An anonymous caller learns display names and login names, and nothing about
  what any of them requires. That is the minimum the screen cannot work without.
- A passwordless member still signs in with one tap, and types nothing.
- A password-required member pays one extra round trip - a 401 - before being
  asked. Unnoticeable, and it only happens to accounts that have a password.
- Probing still reveals per-account whether a password is required, to anyone
  willing to post one request per name. This raises the cost from "read the list"
  to "probe each account"; it does not make it secret, and nothing here claims
  to. The accepted consequence in requirements/0002 - a passwordless account is
  available to anyone who can reach the app - is unchanged.
- A deliberate 401 is now part of a successful flow, so neither logs nor metrics
  can read "401 on /api/auth/login" as an attack on its own.

## Alternatives considered

- **Always show a password field, submit it empty for passwordless members.**
  Leaks nothing at all, not even under probing, and needs no 401. Rejected on the
  requirement it exists to serve: R10's point is that a child ticking off chores
  is never asked for a secret, and a password box they must skip past is exactly
  the chore being designed out.
- **Keep `passwordRequired` but drop `role`.** Removes the smaller leak and keeps
  the list of free passes.
- **Authenticate the roster.** Circular: the screen that needs the roster is the
  one that exists to let someone authenticate.
- **Return 200 for a no-password post on a password-required account, with a
  "password needed" marker.** Same disclosure as the 401 while making a failed
  authentication look successful, and inventing a non-standard status for it.

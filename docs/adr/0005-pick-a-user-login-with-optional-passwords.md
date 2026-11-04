# 0005 - Pick-a-user login with optional passwords

Status: accepted
Date: 2026-11-04
Implements: [requirements/0002](../requirements/0002-accounts-and-administration.md) R10, R11

## Context

[adr/0002](0002-session-auth-postgres-users.md) chose session auth against
`app_user`, keyed on email, with a BCrypt password per user. In use that is the
wrong shape for a household:

- Members do not all have email addresses, and typing one on a tablet to look at
  a chore list is friction with no payoff - the app never sends mail.
- Some members (children) will not use the app at all if a password stands in
  the way.
- The set of users is tiny, known, and physically co-located. There is nothing to
  discover by listing them to each other.

Meanwhile the one account that *must* stay protected is the admin, because it is
the only account that can add or remove users.

The owner was asked to confirm the security trade this implies - a publicly
readable roster, and passwordless accounts anyone reachable can assume - and
confirmed it is intended.

## Decision

**Identity is a username, and `email` is gone.** `app_user.email` is renamed to
`username`, still unique case-insensitively. No email column replaces it; when
the product has a reason to mail someone, that is a new migration and a new
decision.

**`password_hash` becomes nullable, and NULL *is* the "no password required"
flag.** There is no separate boolean. A boolean plus a hash can disagree -
`password_required = true` with no hash, or `false` with a stale hash that still
authenticates - and one of those two states is a silent authentication bypass.
With a single column the state cannot be inconsistent, at the cost of being
unable to distinguish "no password wanted" from "password not set yet". For this
product those are the same thing.

**The login service branches on that column rather than one provider handling
both.**

- `password_hash` present: unchanged path - `AuthenticationManager` →
  `DaoAuthenticationProvider` → BCrypt.
- `password_hash` NULL: the service builds an authenticated token directly, and
  any password the client happened to send is ignored.

The branch lives in `LoginService`, not in a custom `AuthenticationProvider`,
because the bypass should be one obvious, commented, directly-testable `if`
rather than behaviour that emerges from provider ordering. `UserDetails` for a
passwordless user still carries an unmatchable placeholder hash, so if that
`if` is ever removed the request fails closed rather than authenticating.

**The roster is served by a public endpoint.** `GET /api/auth/users` is
`permitAll` and returns id, username, display name, whether a password is
required, and role, for enabled users only. The sign-in screen cannot be drawn
without it.

**One admin, enforced in the database.** A `role` column (`ADMIN` | `MEMBER`)
plus a partial unique index means a second admin row cannot be inserted even by
a bug or by hand.

## Consequences

- Choosing a passwordless member is a single click, which is the point.
- Any visitor who can reach the app can read the household's names and sign in
  as any passwordless member. The deployment's reachability is the real control.
  This is why both environments stay behind the HTTPS front door rather than
  being published openly.
- Authorization work is unaffected: a session is a session however it was
  obtained, so the household scoping planned in
  [design/0001](../design/0001-skeleton-architecture.md) still applies
  unchanged.
- CSRF still matters and stays on - arguably more, since a passwordless login
  can now be triggered cross-site. The token requirement on
  `POST /api/auth/login` is what stops a hostile page silently signing a
  visitor in as a household member.
- Anything beyond a family deployment would need this reversed. The requirement
  document says so explicitly rather than leaving it to be rediscovered.

## Alternatives considered

**Keep email, add a `password_required` boolean.** Rejected on both halves:
email is an identifier nobody here needs, and the boolean can contradict the
hash, which makes the inconsistent state an auth bypass.

**PIN instead of optional password.** A 4-digit PIN for everyone is uniform and
still fast. Rejected because it is security theatre at this scale - a shared
household PIN would be known to everyone it supposedly separates - while still
being a thing a child must remember.

**Preselect the user but always require a password.** Honest, and it keeps the
roster-exposure as the only trade. Rejected by the owner: it does not solve the
problem the requirement exists for.

**A custom `AuthenticationProvider` for passwordless users.** More idiomatic
Spring Security. Rejected because an authentication bypass configured by bean
ordering is harder to see, and far harder to prove absent, than one `if`.

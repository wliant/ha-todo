# 0057 - Home Assistant signs in as an ordinary member

Status: accepted
Date: 2026-11-20

## Context

[requirements/0022](../requirements/0022-home-assistant-acts-as-a-household-member.md)
R188 asks that the household's automation reach the product, and R189 that it
reach only what has been shared with it. That is the first client of this
product that is not a browser.

The authentication the product has is session-based
([0002](0002-session-auth-postgres-users.md)): `POST /api/auth/login` against
`app_user`, a `JSESSIONID` cookie, CSRF on every write because the credential
is a cookie, and sessions in memory ([0003](0003-defer-redis-and-minio.md)) so
every deploy ends all of them. `httpBasic` and `formLogin` are both explicitly
disabled and there is no token, key or bearer mechanism anywhere.

`0002` did consider and reject JWTs, but its reason was that **a browser**
storing a token invites XSS exfiltration where an HttpOnly cookie does not.
That argument says nothing at all about a server-to-server client, so the
question is genuinely open again rather than already answered.

Two constraints shaped the answer more than anything about transport:

- **The grant model already answers the hard question.** "What may the
  automation touch" is a per-list grant given by each owner
  ([0010](0010-per-list-access-instead-of-household-scoping.md)). Whatever
  authenticates has to end up as a `user_id` that predicate can be evaluated
  against, so a token would be a second way to arrive at the same subject.
- **There is one machine client, and it is on the household's own network.**
  Not a published API, not a third party, not something a member hands to a
  service they do not run.

## Decision

The integration **signs in as an ordinary member**, using the sign-in flow the
SPA uses: `GET /api/auth/csrf` for the readable `XSRF-TOKEN`, then `POST
/api/auth/login` echoing it in `X-XSRF-TOKEN`, then the `JSESSIONID` cookie on
every subsequent call.

The admin creates a `homeassistant` user with a password through the existing
admin screen - the username passes the existing `[A-Za-z0-9._-]+` pattern, and
nothing about the account is special. Each list, collection and tracker owner
shares their own thing with it through the access screens they already use. The
integration never calls `/api/admin/**`.

**No backend change.** No new table, no migration, no filter, no endpoint, no
DTO.

A 401 is treated as a **normal event**: clear the cookie jar, sign in once,
retry once. A second 401 is a real credential problem and raises
`ConfigEntryAuthFailed`.

## Consequences

- **The whole integration is a client.** It can be written, reviewed and
  changed without touching `app/`, and it cannot introduce an authorization bug
  in the product, because it holds no authorization logic - it is subject to
  the same predicate as a phone.
- **Every deploy signs the automation out, and that must not read as a
  failure.** Sessions are in memory. R200 exists because an integration that
  raises here produces a repair notice on every deploy, after which the
  household silences the notices and misses the real one.
- **The automation appears on the public sign-in roster**, which says only who
  exists ([0009](0009-opaque-sign-in-roster.md)) - so a non-person sits in the
  list of faces a member picks from, and in every assignee picker on every list
  shared with it. That is the honest price of "an ordinary member", and hiding
  it would mean inventing a second kind of account with a second set of rules.
- **The password is the only credential, so revocation is coarse**: change it,
  or delete the account. Deleting behaves correctly - the grants go with it -
  but there is nothing finer, and nothing that can be scoped to fewer lists
  than the grants already scope it to.
- **The password sits in Home Assistant's configuration.** Whoever can read
  that can reach what the automation can reach. `requirements/0002` already
  says reaching the deployment is the control; this does not widen that, and it
  is strictly better than the shell script with a copied cookie it replaces.
- **CSRF has to be carried by a non-browser**, which is two extra requests and
  a header. Cheap, and it means the backend's security configuration did not
  have to grow an exemption - an exemption is the thing that would have had to
  be got exactly right.

## Alternatives considered

- **A long-lived personal access token.** The textbook answer for a machine,
  and the one to revisit first. It means an `api_token` table, a migration, an
  authenticating filter, a stateless branch in the security chain, and UI to
  mint and revoke - a feature, not a client, and all of it to arrive at the
  same `user_id` the grant is checked against. **The threshold for revisiting
  is a second machine client, or wanting to revoke the automation's reach
  without changing a password.**
- **HTTP Basic on every request.** Tempting because it is stateless and
  `DaoAuthenticationProvider` is already wired. Rejected: it is explicitly
  disabled today, enabling it would make every account in the household
  reachable by basic auth including the admin, and it would put a bcrypt
  verification on every one of the integration's ~15 requests a minute.
- **An unauthenticated webhook with a secret in the path.** What a household
  would improvise, and the shape the calendar feed already warns about
  ([0035](0035-a-feed-address-is-a-write-only-secret-fetched-through-a-guard.md)):
  a capability URL that outlives every revocation and cannot be bound to a
  grant at all.
- **Giving the automation the admin account.** Rejected flatly. The admin
  administers accounts and deliberately gains nothing on a list it was not
  granted; handing it over would get the automation nothing it needs and
  everything it must not have.

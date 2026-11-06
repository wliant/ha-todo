# 0009 - Staying signed in

Design for [requirements/0009](../requirements/0009-staying-signed-in.md)
(R79-R82). Backend only; no schema, no API change, nothing the frontend knows
about.

Decision this implements rather than argues:
[adr/0038](../adr/0038-a-session-lasts-thirty-days-of-use.md).

## Scope

One piece. Two properties and one filter, which only make sense together: each
without the others produces something that looks right and is not.

## Three clocks, not one

The reason this is not a single property. A member is signed out when **any** of
these ends, and before this change they were 30 minutes, "whenever the browser
feels like it", and "the next deploy".

| Clock | Where it lives | R79-R81 |
| --- | --- | --- |
| Server inactivity timer | `server.servlet.session.timeout` | R79 |
| Cookie lifetime | `server.servlet.session.cookie.max-age` | R81 |
| Cookie *freshness* | `RollingSessionCookieFilter` | R80 |
| Application lifetime | in-memory sessions | **not addressed** |

## The properties

```yaml
server:
  servlet:
    session:
      timeout: ${SESSION_TIMEOUT:30d}
      cookie:
        max-age: ${SESSION_COOKIE_MAX_AGE:30d}
```

Both environment variables, because `requirements/0009`'s fourth consequence is
that thirty days is a guess nobody can evaluate. Equal defaults, so the pair
cannot disagree by accident; a deployment that shortens one gets the shorter of
the two, which fails safe.

`timeout` alone satisfies nothing visible: without `max-age` the cookie is a
*session cookie*, and the browser discards it when it decides its browsing
session ended. On Android that is not predictable or testable, so the thirty
days behind it would mostly never be reached (R81).

## The filter

`max-age` alone does not satisfy R80, and this is the part worth reading twice:
**Tomcat writes the session cookie once, at session creation, and never sends it
again.** So a thirty-day cookie expires thirty days after signing in regardless
of use, and a member opening the app daily is signed out on day thirty with
nothing to explain it - the same complaint as the thirty-minute timeout, further
away.

`RollingSessionCookieFilter` (`com.hatodo.config`) re-sends the cookie after the
response, so the browser pushes its expiry out:

- **After the chain**, so it never interferes with a handler, and so a logout
  that invalidated the session leaves nothing to refresh (R82).
- **`getSession(false)`**, never creating one: an anonymous request that minted
  a session would be a session per crawler.
- **At most once per day per session**, tracked in a session attribute. A
  `Set-Cookie` on every response is a header on every request the dashboard
  makes and a cookie-jar write per call, to move a date that moves once.
- **Every attribute copied from the same `ServerProperties` Tomcat used** -
  name, path, `HttpOnly`, `Secure`, `SameSite`, `Max-Age`. Inventing any of them
  would downgrade the cookie on the *next refresh* rather than at sign-in, which
  is a bug that surfaces a month after the change that caused it.
- **`Max-Age` falls back to the session timeout** when no cookie lifetime is
  configured, so the two can never drift apart.

Registered as an ordinary `@Component` filter. It is not part of the security
chain and makes no authorization decision: it re-issues a cookie the browser
already holds, for a session the container already accepted.

## Testing

### Unit - `RollingSessionCookieFilterTest`

The decision table, because this file's bugs are quiet ones: no session at all;
a session fresh enough to leave alone; one due a refresh; attribute fidelity
(`Secure`, `HttpOnly`, `SameSite`, path); at-most-once-per-interval across two
requests; the max-age fallback; and *not* marking the cookie `Secure` on a
plain-http local stack, which would be a session that never arrives.

### Integration - `AuthFlowIntegrationTest`

That a real sign-in returns `Max-Age=2592000`, `HttpOnly`. Asserted on the
actual `Set-Cookie`, because the thing being verified is what the container
emits, not what the properties say.

### E2E

`test_sessions.py`: a member signs in, the browser context is **closed and
reopened from saved storage state**, and they are still signed in - the one
assertion that proves R81 the way a member experiences it, and the one thing
neither a unit test nor curl can show. Signing out and reopening the same way
must land on the sign-in screen (R82).

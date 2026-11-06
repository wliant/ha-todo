# 0038 - A session lasts thirty days of use, and the cookie rolls with it

Status: accepted
Date: 2026-11-06

## Context

Nothing in this product set a session timeout, so it was the framework default:
**30 minutes of inactivity**. On a phone that means a household member re-picks
their name most times they open the app - which for an application whose sign-in
screen is a list of people, half of whom have no password at all
(`adr/0005`), is friction that buys close to nothing.

Three separate things end a session today, and they are easy to confuse:

1. **The server's inactivity timer** - 30 minutes, the default.
2. **The cookie's lifetime** - `JSESSIONID` carried no `Max-Age`, making it a
   *session cookie* that the browser drops when its browsing session ends. An
   installed PWA usually keeps one for a long time, at the browser's discretion.
3. **The server restarting** - sessions live in the application's memory
   (`adr/0003` defers Redis), so every deploy signs everybody out.

Lengthening only the first would have been invisible: the cookie would still go
when Chrome decided its session was over. Setting a plain `Max-Age` alongside it
is also not what was asked for, because Tomcat writes the session cookie **once,
at session creation**, and never re-sends it - so a 30-day cookie expires 30
days after signing in, no matter how much the member used the app in between.

## Decision

**Thirty days of inactivity, on both the session and its cookie, and the cookie
is re-issued as the member keeps using the app.**

- `server.servlet.session.timeout: ${SESSION_TIMEOUT:30d}` - the server-side
  inactivity window, reset by every request the SPA makes.
- `server.servlet.session.cookie.max-age: ${SESSION_COOKIE_MAX_AGE:30d}` - a
  persistent cookie, so closing the browser or the installed app does not end
  the session.
- `RollingSessionCookieFilter` re-sends the cookie when the one the browser
  holds is more than a day old, so continued use keeps pushing the expiry out.
  Without it the cookie's thirty days would run from sign-in rather than from
  last use, and a member using the app daily would still be signed out on day
  thirty for no reason they could see.

The filter refreshes **at most once a day per session** - tracked in a session
attribute - rather than on every response. A `Set-Cookie` on every API call is
bytes on every request and a cookie jar write on every response, for a value
that changes once a day at most.

## Consequences

- **A member signs in about as often as they change phones.** That is the
  point.
- **Deploys still sign everybody out**, because sessions are in memory. This
  decision does not change that, and it is now the *most likely* reason somebody
  is asked to pick their name again. Persisting sessions - Spring Session JDBC
  against the Postgres that is already there - is the fix, and it is deliberately
  not bundled here: it is a dependency, a table and a revision of `adr/0003`.
- **A signed-in phone is now a thirty-day credential.** For this product that is
  a smaller step than it sounds: the sign-in screen lists every member and half
  the accounts have no password (`adr/0005`, `requirements/0002`), so anybody
  holding the phone could sign in as that member anyway. The exposure that
  changes is a *lost* phone, where the session outlives the member's awareness
  of the loss. Signing out still ends it immediately, and the admin can delete
  the account.
- **The cookie is now persistent**, so it is written to disk rather than held in
  memory. It stays `HttpOnly`, `Secure` in both deployed environments, and
  `SameSite=Lax`.
- **Both numbers are environment variables**, so a deployment that wants a
  shorter leash does not need a code change.
- **The timeout and the cookie can disagree if only one is changed.** They are
  set from two properties with the same default for readability; a deployment
  that shortens one and not the other gets the shorter of the two, which fails
  safe.

## Alternatives considered

**Leave the cookie as a session cookie and only lengthen the server timeout.**
Simplest, and mostly ineffective: the thirty days would be at the mercy of
whenever the browser decided its session had ended, which on Android is not
something the product can predict or test.

**A plain 30-day `Max-Age` with no rolling refresh.** One line, and it breaks
the requirement this was asked for - "as long as the member uses the app it
should refresh". It would log out a daily user on day thirty.

**Spring Security's remember-me.** A second credential path with its own token,
its own failure modes and its own place to get authorization wrong. `adr/0005`
keeps the passwordless branch as one obvious `if`; adding a parallel
authentication mechanism to save a cookie attribute is the opposite of that.

**Refreshing the cookie on every response.** Correct and wasteful: a `Set-Cookie`
header on every one of the dashboard's requests, to move an expiry that only
needs moving once a day.

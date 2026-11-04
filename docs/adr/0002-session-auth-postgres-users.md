# 0002 - Session authentication with users in Postgres

Status: accepted
Date: 2026-11-04

## Context

Members must sign in (requirement R7) before the household domain can be scoped
to them (R6). The options were a self-contained credential store, stateless
tokens, or an external identity provider. This is a household app: a handful of
users, no mobile client yet, and no existing identity infrastructure that was
confirmed available.

## Decision

Spring Security with **server-side sessions**, credentials in the `app_user`
table, BCrypt hashes.

- `POST /api/auth/login` accepts JSON rather than a form post, so the SPA uses
  one consistent transport. It calls `AuthenticationManager` directly instead of
  relying on the form-login filter.
- The session id is rotated on login via `ChangeSessionIdAuthenticationStrategy`.
  Calling `HttpServletRequest.changeSessionId()` directly throws when no session
  exists yet, which is the normal case for a first login.
- CSRF protection stays **on**, because the credential is a cookie. The token is
  served from `GET /api/auth/csrf` into a readable `XSRF-TOKEN` cookie and echoed
  in `X-XSRF-TOKEN`.
- Unauthenticated API requests return **401**; the API never redirects to a login
  page.
- `ERROR` dispatches are permitted in the security chain. Without that, a 500
  inside a controller is forwarded to `/error`, blocked by
  `anyRequest().authenticated()`, and surfaces to the client as a misleading 401
  - which is exactly how the `changeSessionId` bug above first presented.

## Consequences

- No token storage, refresh, or revocation logic to get wrong; signing out is
  real server-side invalidation.
- The backend is stateful: more than one instance needs shared session storage.
  That is the most likely reason Redis eventually arrives
  ([0003](0003-defer-redis-and-minio.md)).
- The SPA must prime the CSRF token before its first POST.
- Password reset, email verification, and household invitations are not designed
  yet; the open questions in requirements 0001 cover this.

## Alternatives considered

- **JWT bearer tokens** - stateless and mobile-friendly, but revocation and
  refresh are real work, and storing tokens in the browser invites XSS
  exfiltration that an HttpOnly cookie avoids.
- **External OIDC (Keycloak/Authentik)** - the most robust, and worth revisiting
  if such a provider is already running on the network. Rejected for now because
  it adds infrastructure that was not confirmed to exist, for a handful of users.
- **Stub/dev-only auth** - would have unblocked the skeleton faster, but the e2e
  suite needs a genuine sign-in step, and retrofitting auth under existing
  authorization checks is harder than starting with it.

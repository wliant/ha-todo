# 0016 - The authenticated principal carries the user id

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md)
R19 (every access check needs the caller)

## Context

Nothing in the backend can currently ask "who is calling?" from inside a service.
`AppUserDetailsService` returns Spring's own `User`, which carries a username and
authorities and no id. `AuthController` threads `Principal.getName()` into
`LoginService`, and `AdminUserService` never needs a caller at all, because its one
authorization rule - the admin row is undeletable - is about the *target*, not the
actor.

adr/0010 changes that. Every list and item read and write is checked against the
caller's grants, so every service method in the feature needs the caller's id, and
needs it as a `UUID` because that is what `list_access` is keyed by.

## Decision

**`AppUserPrincipal implements UserDetails`** carries `UUID id`, `username`,
`UserRole role`, `passwordHash` and `enabled`. `AppUserDetailsService` returns it,
so the id travels in the session and no request pays a lookup to discover who is
calling.

**`CurrentUserProvider`** is the service-layer entry point:

```java
UUID requireUserId();      // from the principal, no query
AppUserPrincipal require();
boolean isAdmin();
```

The `SecurityContextHolder`-backed implementation reads the context through
`getContextHolderStrategy()`, matching the strategy `AuthController` already uses,
and **throws** `IllegalStateException` when the principal is absent or not an
`AppUserPrincipal`. It never returns null and never invents an anonymous id: a
caller who reaches a service without an authenticated principal is a routing bug,
and failing loudly is the only safe response when the return value is about to be
used as an authorization subject.

Services inject `CurrentUserProvider`. Controllers go back to translating HTTP and
stop accepting `Principal`.

**adr/0005's fail-closed placeholder is preserved.** `AppUserPrincipal.getPassword()`
returns the unmatchable `{password-not-required}` string when the stored hash is
null, so if `LoginService`'s one explicit `if` were ever deleted, a passwordless
account would fail authentication rather than accept anything.

**`LoginService`'s passwordless branch must build its authenticated token from the
same `AppUserPrincipal`.** This is the sharp edge of the change: the two sign-in
paths construct their `Authentication` differently, and if only the
`DaoAuthenticationProvider` path yields the new principal type, then password
sign-in works and passwordless sign-in puts a `User` in the session that every
later `requireUserId()` rejects. The failure appears only for passwordless
members, only after sign-in, and no existing test covers it - so a unit test
asserting the principal type and id on that branch is mandatory.

## Consequences

- Every access check is one field read from the session. No per-request user query,
  no identity argument in twenty service signatures, and no service re-resolving a
  username to a row.
- Unit tests inject a one-line stub provider, so service tests stay free of
  Spring Security machinery.
- `AppUserPrincipal` holds the password hash, as `UserDetails` requires. It lives in
  the session, which is server-side and in memory - the same place the hash already
  travelled as Spring's `User`. No change in exposure, and it must never be
  serialised into a response; the response records are separate types already.
- Sessions are in-memory with no Redis (adr/0003), so a redeploy ends every session
  and nobody holds a principal of the old type across the change. If session
  storage is ever externalised, this becomes a migration concern and the provider
  would need a fallback - noted so the connection is not lost.
- The provider throws rather than returning an `Optional`, so a service cannot
  accidentally treat "nobody" as a valid subject. Routes that are legitimately
  anonymous - the roster, login, CSRF - never call it.
- `role` on the principal makes `isAdmin()` free, which the password routes need.
  It is not a substitute for the `hasRole("ADMIN")` URL rule; both stay.

## Alternatives considered

- **Thread `Principal.getName()` from the controller into every service**, as
  `AuthController` does today. No new types. Rejected: twenty signatures carrying an
  identity argument, each service turning a string back into a row, and the
  identity being a username where the authorization table is keyed by id. One
  forgotten argument is an unauthenticated write.
- **`@AuthenticationPrincipal` in controller methods**, passing the id down. Removes
  the `SecurityContextHolder` read and keeps the dependency explicit, which is
  genuinely cleaner in isolation. Rejected for the same reason as above: the id
  still becomes a parameter on every service method, and a parameter can be passed
  the wrong value while a provider cannot.
- **`@EnableMethodSecurity` with `@PreAuthorize`.** The conventional Spring answer.
  Rejected because the rule is data-dependent - does this caller hold a grant on
  this list - so it needs a repository lookup inside the transaction that owns the
  write. Expressing that in a SpEL string splits authorization between an annotation
  and the service, and the project deliberately has no method security today.
- **A request-scoped `CurrentUser` bean** resolved once per request. Equivalent in
  effect, with proxying and scope machinery for no benefit over reading the context.
- **Keeping Spring's `User` and looking the id up by username in each service.** No
  new principal type, and a database round trip per authorization check for an id
  that was known at sign-in.

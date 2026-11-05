# 0035 - A feed address is a write-only secret, fetched through an egress guard

Status: accepted
Date: 2026-11-06

## Context

`adr/0034` makes a calendar feed a URL a member pastes. Two properties of that
URL force decisions this product has not had to make before.

**It is a bearer capability.** Google's secret iCal address and Outlook's
published calendar link authenticate nobody: whoever holds the string reads that
calendar in full, indefinitely, from anywhere, with no session and no audit.
It is closer to a password than to an identifier, and unlike a password this
product cannot hash it - it has to send it verbatim on every refresh. Only the
member can invalidate it, by regenerating it at the provider.

**It is a member-supplied URL that our server will fetch.** This is the first
outbound HTTP call the product makes, and the destination is attacker-chosen in
the general case and typo-chosen in the common one. The deployment makes the
general case concrete: the app container can reach a Docker registry on
`192.168.1.248:5000` over plain HTTP with no authentication, its own
`app:8080/actuator`, Postgres and the SeaweedFS store on the same LAN. A feed
pointed at any of those turns "add a calendar" into a request-forgery primitive
whose response the member can read in the preview that `requirements/0007` R65
requires.

Every member is family, and the admin creates the accounts (`adr/0006`). So the
threat is not primarily malice - it is that a confused-deputy hole in a home
server is cheap to close now and embarrassing to discover later, and that the
preview feature hands back the response body by design.

## Decision

**The URL goes in write-only, and comes out never.** `calendar_feed.url` is set
on create and on an explicit replace, and no response record carries it - not
the owner's own, not an administrator's view, not an error message. The screen
shows the label, the provider inferred from the host, and the sync state. A
member who has lost the address regenerates it at the provider and pastes the
new one; the product cannot help them, by construction.

**Every fetch goes through one guard, and the guard runs on the resolved
address.** One method is the only place in the application that opens a
connection to a feed, and before it does:

- the scheme must be `https`;
- the host is resolved, and every address it resolves to must be a global
  unicast address - loopback, link-local, site-local, unique-local,
  carrier-grade NAT and multicast are all refused;
- redirects are followed at most three deep, and the guard runs again on each
  hop, because a public host may redirect to `127.0.0.1`;
- the response is read through a limiting stream that aborts past 5 MB, with a
  10-second connect and 20-second read timeout;
- a refusal reports *that* the address is not allowed, never what was found at
  it.

Local development and the integration tests set
`app.calendar.allow-private-hosts: true`, so a fixture feed can be served from
`localhost`. It is **false** by default, and dev and prod leave it false.

## Consequences

- **A mispaste or a hostile paste cannot read the LAN.** The registry, the
  actuator and the object store are unreachable through this feature, and the
  preview cannot be used to exfiltrate their responses.
- **The preview in 0007 R65 is safe to show, which is the point.** Handing a
  response body back to the member is only acceptable because the guard decides
  where the body came from.
- **A member can never verify which address they stored.** They see a label and
  a last-synced time, and if a feed is wrong their only move is to replace it.
  Accepted: a readable secret is one screenshot away from being a leaked
  calendar, and this is the same rule `adr/0009` applies to the sign-in roster -
  a field is omitted rather than shown carefully.
- **The guard is a behaviour with a test, not a comment.** Refusing
  `http://`, a hostname resolving to a private address, a redirect to loopback,
  and an oversized body are four integration tests; without them this decision
  is an aspiration.
- **DNS is resolved twice** - once by the guard, once by the HTTP client - so a
  determined rebinding attack has a window. Accepted for a household LAN;
  closing it means connecting to the vetted address with the hostname in the TLS
  SNI and `Host` header, which is a custom socket factory this does not justify.
- **The stored address is not encrypted at rest.** Left as `requirements/0007`'s
  open question: the only available key would live in the same environment file
  as the database password, so it would obscure the secret from nobody who could
  not already read it.
- **An administrator with database access holds every member's calendar.** True
  before this decision and unchanged by it; worth stating because members will
  reasonably assume otherwise.

## Alternatives considered

**Return the URL masked** (`https://calendar.google.com/.../basic.ics`, middle
elided). Rejected: the token *is* the middle, so a useful mask shows nothing,
and a mask that shows enough to recognise shows enough to narrow.

**Store a hash and keep the URL out of the database.** Impossible - the refresh
needs the URL verbatim.

**Allowlist only Google's and Microsoft's feed hosts.** Tempting, and it would
close the forgery hole completely. Rejected because it would reject every other
legitimate feed - a school's term calendar, a sports fixture list, a shared
Nextcloud - which is most of the long tail this feature will actually be used
for, and it would make the product's two-provider assumption permanent in code.
The guard is the same work and does not care who publishes.

**Fetch without a guard, on the grounds that every member is family.** Rejected:
the hole is not about trust between members, it is that a single bad paste - or
a link somebody was sent and did not read - gives the server's network position
to whoever wrote it.

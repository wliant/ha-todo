# 0034 - Calendar feeds are read-only iCalendar subscriptions, not OAuth integrations

Status: accepted
Date: 2026-11-06

## Context

`requirements/0007` puts household members' appointments on My day beside their
chores (R62, R67), and forbids this product from authoring an event at all
(R63). The events live in Google Calendar and Outlook. There are two ways to
read somebody else's calendar out of either provider.

**OAuth against the provider's API.** Google Calendar's read scopes are
*sensitive*: a Google Cloud project, a consent screen, a privacy policy and a
verification submission, or else a permanent cap on test users and a warning
screen that looks like a phishing attempt. Microsoft needs an Entra application
registration, and personal Microsoft accounts and work accounts are not the same
registration story. Both mean storing and refreshing per-member tokens - a
credential this product would have to keep valid indefinitely, with a refresh
path, a revocation path and a failure mode per provider. In exchange: a calendar
picker, near-real-time push notifications, and incremental sync.

**An iCalendar feed.** Both providers publish a per-calendar `.ics` URL with an
unguessable token in it - Google's "secret address in iCal format", Outlook's
"Publish a calendar" link - which serves the calendar over plain HTTPS GET with
no authentication of the caller. One format, RFC 5545, for both providers and
for anything else a member might subscribe. In exchange: no picker, no push, and
output that providers regenerate lazily.

The deployment is one household of a handful of people (`adr/0010`), behind two
HTTPS hostnames on a home server, deployed by a script from a laptop. Nothing
here writes to a calendar, now or in the requirement: R63 is a position, not a
staging post.

## Decision

**Subscribe to iCalendar feeds. No OAuth, no provider SDK, no stored tokens.**

A feed is a URL a member pastes, plus a label. The product fetches it on a
schedule, parses it, and keeps the events it needs. There is no Google client
library and no Microsoft one; there is one HTTP GET and one iCalendar parser,
and the product cannot tell a Google feed from an Outlook one except by what the
member typed and what the bytes say.

Since the product never authenticates as the member, it cannot enumerate their
calendars - so "which calendar is synced" is answered by *which address was
pasted*, which is what 0007 R62 says.

## Consequences

- **The whole feature is an HTTP GET and a parser.** No application
  registration in two developer consoles, no verification submission, no consent
  screen, no token table, no refresh job, no per-provider failure modes, and
  nothing that expires on its own. The single dependency is an iCalendar parser.
- **It is testable.** A feed is a text file over HTTP, so integration tests
  serve fixtures from a local HTTP server and assert on what was parsed and
  stored - including the cases that actually break (all-day events, recurrence
  with exceptions, a moved occurrence, a cancelled one). The OAuth version of
  this feature could not be exercised end to end at all without mocking two
  providers' token endpoints.
- **The copy lags, and this is the real cost.** Published `.ics` output is
  regenerated on the provider's schedule, not ours; Google's can be hours
  behind. Polling more often does not fix it. Accepted in 0007's consequence 1:
  the events this feature exists for are known days in advance. If
  same-minute accuracy is ever required, this decision is the one to revisit,
  and it is a rewrite of the fetch layer rather than of the feature.
- **Onboarding is manual and a little miserable** (0007 consequence 2). There is
  no picker to offer, so the product's job is to explain where the address is.
- **The product holds a bearer secret per feed.** That is the subject of
  `adr/0035`, which also covers fetching a member-supplied URL from our server.
- **Nothing blocks a second backend instance on this feature's account.** The
  poller shares the recurrence job's single-instance assumption (`adr/0025`),
  and like it, fails noisily rather than wrongly.

## Alternatives considered

**OAuth against Google Calendar and Microsoft Graph.** Rejected on cost against
value: two application registrations, a sensitive-scope verification and a
token lifecycle, bought for a household of four, where the only thing it buys
that members would notice is freshness measured in minutes instead of hours.
This is the option the original requirement assumed ("add connector"), and it
was discarded once the feature was narrowed to display-only - the connector
model only earns its keep when something needs to be *written* back.

**CalDAV.** Authentication per provider again, and Google's CalDAV endpoint
wants OAuth anyway. All of the cost of OAuth and a more obscure protocol.

**Each member shares their calendar directly with the others at the provider.**
Free, better notifications, and genuinely the right answer for the part of this
that is "see Ben's calendar" - which is exactly why it was considered. Rejected
because it cannot put an appointment and a due chore in the same column of the
same day, which `requirements/0007` identifies as the only thing this product
can do that the providers cannot. Members should still do this *as well*; it is
not in competition.

**A browser-side fetch of the feed.** Would avoid storing the URL server-side
and avoid our server making outbound calls. Rejected: cross-origin, so it
cannot work; the URL would have to be readable by the client, which contradicts
0007 R66; and each member's browser would have to hold every other member's
secret addresses to render a shared feed.

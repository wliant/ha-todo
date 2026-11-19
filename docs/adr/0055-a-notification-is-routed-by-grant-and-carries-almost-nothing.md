# 0055 - A notification is routed by grant and carries almost nothing

Status: accepted
Date: 2026-11-19

## Context

[adr/0054](0054-new-work-reaches-an-open-agenda-over-sse.md) gives the agenda a
stream. That stream carries the description of a chore somebody added, and
[requirements/0021](../requirements/0021-an-agenda-that-keeps-itself-current.md)
R183 says a member may only ever be told about work on a list they hold a grant
on.

Every read in this product already answers that question in SQL, by joining
`list_access` - never by fetching and filtering in Java
([adr/0010](0010-per-list-access-instead-of-household-scoping.md)). A push
channel is the first thing in the product that does not begin with a request
from the person who will see the result, so the question has to be answered
somewhere new.

There is also a question about what a notification should contain. The payload
the dashboard already returns is right there, and `DashboardItemResponse` would
drop into an event with no work.

## Decision

**The server resolves recipients and writes only to their streams.** There is no
list-addressable channel, no subscription parameter, and no filtering on the
client. `GET /api/live/stream` takes no arguments at all; the registry is keyed
by user id; the identity of the subscriber comes from the session, exactly as
`/api/dashboard/*` takes no list id.

**Recipients are the `list_access` rows for the item's list, resolved at
delivery time** - not the owner unioned with the grants, and not a set cached on
the emitter when it was opened.

**A notification carries the item id, the list id and name, the description, who
added it (or nothing, when the job did), and whether the recipient is the member
who added it.** It carries no detail HTML, no assignee, no costs, no due date
and no version.

## Consequences

- **The client's only response to a notification is to refetch** through the
  guarded endpoints. The stream is a doorbell. The day a notification carries
  enough to render a row from is the day it has quietly acquired a second copy
  of the access guard - at which point this decision is to be **reopened**,
  with the grant check written once and shared, rather than extended.
- **`list_access` alone is the same predicate as the read being triggered.** The
  dashboard's guard is `EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = i.list
  AND a.user.id = :userId)`, and `TodoListService.create` writes the owner's own
  grant row in the same transaction. A broader recipient set would tell somebody
  about a chore their agenda then does not show; a narrower one would leave
  somebody stale. A future reader who "fixes" this by unioning the owner has
  broken the first of those, invisibly.
- **A grant revoked mid-stream takes effect on the next event**, because the
  recipient set is read per event. Caching list ids on the emitter at subscribe
  time is the cheaper design and keeps notifying somebody about a list they were
  removed from.
- **`byYou` is computed per recipient, on the server.** The client never compares
  user ids, so R181 lives in one place rather than in every surface that ever
  consumes the stream.
- **A notification from the recurrence job is never `byYou`**, even for the
  member who wrote the rule. `TodoItemService.createFromRule` takes the rule's
  author as its actor - a real person, because a synthetic system user would be
  a lie in the audit trail (adr/0025) - so without a `MEMBER` / `RECURRENCE`
  discriminator on the event, the one member guaranteed to hear nothing at 01:05
  is the one who wrote the standing instruction. That is R182 exactly backwards,
  and nothing would fail.
- **The description is the only piece of list content on the wire.** It only ever
  goes to grant-holders, and it is rendered as text in a snackbar. Detail HTML is
  excluded not because it is large but because putting sanitized HTML on this
  channel invites a client that renders it, and the client sanitizes nothing by
  policy ([adr/0018](0018-detail-html-is-sanitized-on-write.md)).
- **No version on the wire.** A version invites a write against a value that was
  never read through the guarded endpoint.

## Alternatives considered

**Broadcast, and filter on the client.** Much cheaper: one write per event
instead of one per recipient, and no recipient query. Rejected because its
failure mode is the worst available here - it passes every unit test, passes
every integration test written against the UI, and puts the description of every
private list in the household into every connected browser, discoverable only in
a devtools network tab. There is no version of "cheaper" worth that.

**A channel per list, subscribed by the client.** Then the server has to
authorize subscriptions instead of deliveries, which is the same check performed
earlier and cached for longer - so a revoked grant keeps delivering until the
stream is reopened. It also puts list ids in a URL the member chooses, which is
a second surface to get wrong.

**Sending `DashboardItemResponse` on the event.** It already exists and would
save the refetch. Rejected: see the first consequence above. It also could not
answer the question each cached payload needs answered - which window, which
scope, which of three shapes - so the client would have to guess where to put
it, which is the work `useUpdateItemFromAgenda` already declines to do.

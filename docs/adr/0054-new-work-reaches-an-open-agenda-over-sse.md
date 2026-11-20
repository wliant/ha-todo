# 0054 - New work reaches an open agenda over server-sent events

Status: accepted
Date: 2026-11-19

## Context

[requirements/0021](../requirements/0021-an-agenda-that-keeps-itself-current.md)
R179 asks that work added by somebody else appear on an open agenda without the
member doing anything, and R180 that it be named when it does.

Until now nothing in the product pushed, polled or subscribed. The single
freshness mechanism is `refetchOnWindowFocus: true` in `web/src/main.tsx`;
everything else happens because the member wrote something. The dashboard query
hooks carry no `staleTime` and no `refetchInterval`, deliberately.

Two constraints shaped this more than anything about the browser:

- **The front door is not ours.** Both environments terminate TLS on a reverse
  proxy configured outside this repository (dev at `todo-dev.example.org` -> 5987,
  prod at `todo.example.org` -> 5986). Whatever we build has to survive a proxy
  whose behaviour we cannot test from here and cannot change in a pull request.
- **One backend instance.** Sessions are in memory
  ([adr/0003](0003-defer-redis-and-minio.md)) and the recurrence job takes no
  lock ([adr/0025](0025-recurrence-is-materialised-by-a-scheduled-job-on-one-instance.md)).

R180 also rules out the cheapest implementation of R179 on its own. Polling and
diffing the refetched payload would answer "is this current" but not "what
arrived": no item DTO in the product carries a creation timestamp, the dashboard
pages are capped at 500 rows, and the window changes whenever the member
navigates - so an id that was not in the previous payload means "new", "beyond
the cap", "outside the old window" or "I just pressed Next", indistinguishably.

## Decision

New work reaches an open agenda through a **one-way server-sent event stream**
at `GET /api/live/stream`, opened by the agenda alone, and **the stream is a
notification channel rather than a second way to read**.

The client's entire response to an event is to invalidate the query keys it
already owns and raise a message. Everything drawn on screen still comes from
`/api/dashboard/*`, through the same grant checks as before (R187).

Subscribers are held in a `LiveBroker` bean: a map from user id to that member's
emitters, with a single-threaded executor that owns every write to every
emitter, and a 20-second heartbeat comment. The heartbeat and the fan-out share
that one thread because `SseEmitter` is not safe for concurrent sends, and two
threads interleaving mid-frame produce a dropped event with no exception on
either side.

## Consequences

- **One long-lived connection per open agenda.** Not per tab of the application:
  the hook is mounted by `DashboardPage` and nowhere else. On HTTP/1.1 it
  occupies one of the browser's six connections per origin, which is a second
  reason to keep it off every other route.
- **Every proxy in the chain must not buffer it.** `web/nginx.conf` gets an
  exact-match location with `proxy_buffering off`, and the controller sets
  `X-Accel-Buffering: no` on the response - that header is the only instruction
  that reaches the front door we do not control, and it is the most valuable
  line in the change.
- **A buffered stream is not a broken one.** It degrades to latency, which is
  what makes [adr/0056](0056-a-stream-that-cannot-open-degrades-to-periodic-invalidation.md)'s
  fallback a tolerable floor rather than a confession.
- **The emitter carries an explicit 30-minute timeout.** With the no-argument
  constructor Spring falls back to the container's async timeout, which is 30
  seconds on Tomcat: the feature still works, every test passes, and every open
  tab reconnects 120 times an hour. `spring.mvc.async.request-timeout` is
  deliberately *not* set globally, because it would bind every future async
  endpoint to this feature's number.
- **The timeout is also a security bound.** Nothing re-checks authorization on
  an emitter that is already open, so a stream authenticated at subscribe time
  outlives its session by up to half an hour. A logout handler closes the
  streams on an explicit sign-out; the timeout bounds the expiry case, which has
  no event to hook.
- **The heartbeat runs on the broker's own executor, not `@Scheduled`.**
  `@EnableScheduling` sits on `RecurrenceSchedulingConfiguration`, which is
  conditional on `app.recurrence.enabled` - and the integration profile sets
  that to `false`. A `@Scheduled` heartbeat would therefore not exist in
  integration tests, and switching recurrence off in production would silently
  switch liveness off with it.
- **A second backend instance gets worse, in a new way.** Recurrence's
  double-run is noisy and harmless; this would deliver to whichever half of the
  household happened to hold a stream on the instance that did the write, with
  nothing failing anywhere. Sessions still break first.
- **`app.live.enabled: false` makes the endpoint absent**, which the client
  cannot distinguish from a failure to connect - so the kill switch degrades to
  the fallback rather than breaking the page.

## Alternatives considered

**WebSocket.** Rejected on the front door. An upgrade handshake has to be
honoured by `web/nginx.conf` *and* by the external terminator; if the latter
does not proxy upgrades the connection simply fails, with no degraded mode and
no fix available in this repository. A server-sent stream is an ordinary chunked
HTTP/1.1 GET, and the worst a well-behaved proxy does to one is buffer it.
Secondary reasons: the traffic is one-way, so duplex buys nothing; `EventSource`
carries the session cookie same-origin with no handshake of our own, where a
WebSocket subprotocol invites a token in a query string - the shape
[adr/0035](0035-a-feed-address-is-a-write-only-secret-fetched-through-a-guard.md)
exists to refuse; and STOMP or `spring-websocket` would be a second messaging
model, with its own `Origin` and CSRF story, in a product with none.

**Polling on an interval, with no push at all.** The user was offered this and
chose against it after the cost was stated. It also cannot satisfy R180 on its
own, for the reason in the context above. It survives as the *fallback*
([adr/0056](0056-a-stream-that-cannot-open-degrades-to-periodic-invalidation.md)),
where losing the naming is the stated price.

**Long polling.** All of SSE's proxy exposure with none of its browser support
for reconnection, and a reconnect loop we would have to write and get right.

**A `since` parameter on the dashboard endpoints.** `todo_item.created_at` exists
and is unread, so a server-authoritative "what is new" is available without a
migration. Rejected because it makes every dashboard payload carry a timestamp
whose only consumer is a notification, puts an unindexed predicate on three
queries, and still requires the client to poll to ask the question.

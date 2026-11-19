# 0021 - An agenda that keeps itself current

Design for [requirements/0021](../requirements/0021-an-agenda-that-keeps-itself-current.md)
(R179-R187). **Both sides**, one new backend package and four new frontend
modules, with no migration, no change to any existing DTO and no change to any
existing endpoint.

Three ADRs record the decisions:
[0054](../adr/0054-new-work-reaches-an-open-agenda-over-sse.md) (the transport),
[0055](../adr/0055-a-notification-is-routed-by-grant-and-carries-almost-nothing.md)
(the routing and the payload) and
[0056](../adr/0056-a-stream-that-cannot-open-degrades-to-periodic-invalidation.md)
(what happens when it does not work).

## The shape of it, in one paragraph

A member with the agenda open holds one `EventSource` on `GET
/api/live/stream`. `TodoItemService` publishes an `ItemCreatedEvent`;
`ItemCreatedListener` consumes it **after the transaction commits**, resolves
the recipients from `list_access`, and writes one notification to each of those
members' emitters and no others. The client invalidates `['dashboard']` and
`['lists']` and queues a message naming the chore, unless the server marked the
notification as the recipient's own act. If the stream cannot be opened, the
same hook falls back to invalidating `['dashboard']` on an interval.

## Scope

| Piece | Where | New? |
|---|---|---|
| The event | `item/ItemCreatedEvent` | new |
| Publishing it | `item/TodoItemService` | changed |
| Who may hear it | `list/ListAccessRepository.findUserIdsForList` | changed |
| Settings | `live/LiveProperties`, `application.yml`, `.env.example` | new |
| The wire record | `live/LiveNotification` | new |
| The registry | `live/LiveBroker` | new |
| The endpoint | `live/LiveController`, `live/LiveConfiguration` | new |
| Routing | `live/ItemCreatedListener` | new |
| Sign-out | `live/LiveLogoutHandler`, `config/SecurityConfig` | new / changed |
| The proxy | `web/nginx.conf` | changed |
| The transport | `web/src/api/live.ts` | new |
| The queue's rules | `web/src/dashboard/announcements.ts` | new |
| The queue | `web/src/dashboard/useAnnouncer.ts` | new |
| The connection | `web/src/dashboard/useLiveUpdates.ts` | new |
| Wiring | `web/src/dashboard/DashboardPage.tsx` | changed |

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| An application event published by a service and consumed elsewhere | `ListDeletedEvent`, `ListAccessRevokedEvent` | The pattern and the direction of the arrow are established |
| `ApplicationEventPublisher` injected into a service | `TodoListService` | `TodoItemService` copies the constructor parameter |
| A grant query in SQL | `ListAccessRepository` | One more projection, not a new idea |
| Two sibling key trees and the rule about invalidating both | `dashboard/queries.ts`, adr/0039 | The hook follows it |
| A page-level snackbar with five writers | `DashboardPage` | The queue replaces its one state slot, not its markup |
| `SseEmitter` | `spring-boot-starter-webmvc` | No new dependency |

## Step 1 - the event, and the two places it is published

`ItemCreatedEvent` is a record in the **item** package, carrying values:

```java
public record ItemCreatedEvent(UUID itemId, UUID listId, String listName,
                               String description, UUID actorId,
                               String actorDisplayName, Source source) {
    public enum Source { MEMBER, RECURRENCE }
}
```

`TodoItemService` takes an `ApplicationEventPublisher`, as `TodoListService`
already does, and publishes from `create(...)` with `Source.MEMBER` and from
`createFromRule(...)` with `Source.RECURRENCE`. The item package learns nothing
about streams; the arrow points one way, which is the reason
`ListAccessRevokedEvent`'s javadoc gives for existing at all.

**It carries values, never entities, and that is load-bearing.** The listener
runs after the transaction has committed, and `spring.jpa.open-in-view` is
false - so reaching `item.getList().getName()` there throws
`LazyInitializationException`. In the `createFromRule` case that throw lands
*inside the recurrence runner's catch*, is recorded against the rule, and reads
as a recurrence bug.

## Step 2 - who may hear it

```java
@Query("SELECT a.user.id FROM ListAccess a WHERE a.list.id = :listId")
List<UUID> findUserIdsForList(@Param("listId") UUID listId);
```

**`ListAccess` alone, deliberately**, without unioning the owner - see
[adr/0055](../adr/0055-a-notification-is-routed-by-grant-and-carries-almost-nothing.md).
`TodoListService.create` writes the owner's own grant row, and this has to be
the same predicate as the read it is about to trigger.

## Step 3 - the broker

`LiveBroker` holds `ConcurrentHashMap<UUID, Set<SseEmitter>>`, the value a
`ConcurrentHashMap.newKeySet()`.

**Removal goes through `compute`, never `get().remove()` followed by `if
(isEmpty()) map.remove()`.** That pair races: one thread finds the set empty and
drops the key while another is adding an emitter to the set it just orphaned.
The symptom is a member who is connected, is heartbeating, and never receives
anything - with nothing logged.

**Deregistration is wired on all three callbacks** - `onCompletion`,
`onTimeout`, `onError` - and is idempotent. `onTimeout` additionally calls
`complete()`, which Spring does not do for you; a timed-out emitter left
uncompleted logs an `AsyncRequestTimeoutException` and stays in the map.

**One thread writes.** The broker owns a single-threaded daemon
`ScheduledExecutorService`, started in `@PostConstruct` and shut down in
`@PreDestroy`, and **both** the heartbeat tick and every fan-out go through it.
Two reasons, the second load-bearing: the listener runs on the request thread of
the member who just pressed Add, and writing to N sockets there makes their
button wait on somebody else's stalled connection; and `SseEmitter` is not safe
for concurrent sends, so a heartbeat and an event interleaving mid-frame is a
dropped event with no exception on either side. One writer removes that by
construction rather than by remembering to synchronise, which is the same move
as one `isWritableBy`, one `duplicateOf`, one `TrackerPeriod.startOf`.

The heartbeat is an SSE **comment**, so no client listener is needed for it. It
is also the only thing that discovers a peer that vanished - a slept phone, a
closed lid - because the write throws `IOException` and the emitter is forgotten.
Without it every closed tab leaks an emitter until its timeout.

## Step 4 - the endpoint

`GET /api/live/stream`, `produces = text/event-stream`, returning
`broker.subscribe(currentUser.requireUserId())`. **It takes no parameters at
all** (adr/0055). It is `authenticated()` under `SecurityConfig`'s
`anyRequest()`, and being a GET it is outside CSRF.

It sets **`X-Accel-Buffering: no`** and `Cache-Control: no-store` on the
response. That header is the only instruction in this whole change that reaches
the front door we do not control.

`LiveConfiguration` is `@ConditionalOnProperty("app.live.enabled")`, so
switching the feature off makes the endpoint 404 - which the client treats as
any other failure to connect, and falls back from.

### Settings

```yaml
app:
  live:
    enabled: ${LIVE_ENABLED:true}
    heartbeat: ${LIVE_HEARTBEAT:PT20S}
    stream-timeout: ${LIVE_STREAM_TIMEOUT:PT30M}
    max-streams-per-member: ${LIVE_MAX_STREAMS:5}
```

20 seconds is under nginx's 60-second `proxy_read_timeout` default *and* under
the typical idle timeout of a front door we cannot inspect. 30 minutes is a
deliberate recycle: it reaps half-dead connections and bounds how long a stream
authenticated at subscribe time can outlive its session. The cap stops a
reconnect bug in one tab accumulating emitters.

`application-itest.yml` sets `heartbeat: PT1S` and - unlike recurrence and
calendar - **does not** set `enabled: false`: this feature has no cron that can
fire mid-assertion, it only acts when an item is created, which is what the test
is doing on purpose.

## Step 5 - routing

`ItemCreatedListener` is `@TransactionalEventListener(phase = AFTER_COMMIT)`. It
reads the recipients, computes `byYou` per recipient, and hands the broker one
notification each.

```java
public record LiveNotification(String type, UUID itemId, UUID listId,
                               String listName, String description,
                               String actorDisplayName, boolean byYou) {}
```

`byYou` is `source == MEMBER && recipient.equals(actorId)` - **so a
`RECURRENCE` notification is never `byYou`, even for the member who wrote the
rule**. That is R182, and without the discriminator it would be exactly
backwards (adr/0055). `actorDisplayName` is null there, and the client words it
"added automatically".

## Step 6 - signing out

`LiveLogoutHandler` is registered through `.logout(l ->
l.addLogoutHandler(...))` in `SecurityConfig` and closes that member's streams.
Nothing re-checks authorization on an open emitter, so without it a signed-out
tab keeps receiving chore descriptions until the stream times out.

## Step 7 - the proxy

`web/nginx.conf` gets `location = /api/live/stream` immediately above `location
/api/`: `proxy_buffering off`, `proxy_cache off`, `gzip off`,
`proxy_read_timeout`/`proxy_send_timeout 1h` (which must exceed the emitter's
own, or nginx cuts first and the client reconnects on nginx's clock),
`proxy_http_version 1.1`, `proxy_set_header Connection ""`.

**Every `X-Forwarded-*` header is repeated**, and that is the silent one. nginx
inherits `proxy_set_header` from the enclosing level only when the current level
sets **none** of its own - so the moment this block sets `Connection ""`, Host,
`X-Real-IP`, `X-Forwarded-For` and `X-Forwarded-Proto` all stop being sent, and
Spring sees plain http on an https site. That is precisely what the comment at
the top of that file exists to prevent.

Vite needs no change.

## Step 8 - the client

`api/live.ts` holds the DTO and one `openLiveStream()` factory. The factory
existing at all is what lets a test swap the transport without a global. No
barrel, as everywhere in `api/`.

`dashboard/announcements.ts` is pure: the queue, the cap and the collapse.
`dashboard/useAnnouncer.ts` holds it and the precedence rule.
`dashboard/useLiveUpdates.ts` owns the connection.

**On an event:** invalidate `['dashboard']` - debounced, so a burst is one
refetch round - **and `['lists']`**; then announce unless `byYou`. The hook is a
reader rather than one of adr/0039's four writers, so that rule does not
formally bind it, but its reason does: the two trees are siblings, neither
prefix reaches the other, and the chore that just appeared is on a list as well
as on the agenda. A `byYou` notification still invalidates - the member may have
two devices open and only one of them did the create (R181).

**The fallback** is adr/0056: on no `open` within ten seconds, an `error` with
`readyState === CLOSED`, or no `EventSource` in this browser, invalidate
`['dashboard']` every sixty seconds while `document.hidden` is false. It names
the key tree, so no query hook changes and a fourth dashboard query is covered
for free.

**Signing out, and a correction worth stating.** `EventSource` does **not**
retry a non-2xx: per spec it fires `error`, sets `CLOSED`, and stops. Our
anonymous `/api/**` answers 401, so an expired session ends the stream for good
rather than hammering - the hazard is the opposite one, a tab that is silently
no longer live. So a permanent close enters degraded mode *and* invalidates
`currentUserKey` once; if the session really is gone, `ProtectedRoute` redirects
and the hook's cleanup closes everything. We write no retry loop of our own -
the browser owns backoff for network-level failures - and after five consecutive
permanent closures the hook stops constructing an `EventSource` until the next
`visibilitychange`. A bound, not a loop.

**StrictMode:** the cleanup must genuinely `close()` and null the ref, or `pnpm
dev` holds two streams per tab and every message appears twice - a dev-only
symptom that reads as a server bug.

**Agenda-only.** The revisit threshold is a *second* surface that needs telling;
at that point the hook moves to `app/`, takes a predicate for what to
invalidate, and `AppShell` opens the one stream.

## Step 9 - the queue

A local queue in `dashboard/`, **not** the application's first shared snackbar
provider. Four surfaces own a local snackbar today - `DashboardPage`,
`ItemsSection`, `RecurrenceSection`, `TrackerDetailPage` - and exactly one of
them is acquiring a second message source. A provider built for one real user is
the generalisation this repo defers on principle, the way
[adr/0032](../adr/0032-a-detail-pictures-owner-is-a-third-foreign-key.md) sets
the threshold at a fourth owner and
[adr/0051](../adr/0051-a-tracker-is-its-own-aggregate.md) at a fourth kind.
**The threshold for a shared provider is a second surface that needs a queue**,
not a second writer on one surface.

Precedence, which is the actual behaviour being designed: a message from the
member's **own act** - completed, lapsed, saved, refused - **pre-empts
immediately and clears any queued announcements**. Announcements queue behind at
four seconds each, capped at five, the tail collapsing into "and 3 more chores
were added" and recounting as more arrive (R184).

## What fails silently

In roughly descending order of how long it would take to find.

1. **A plain `@EventListener` instead of `AFTER_COMMIT`.** The push leaves
   before the commit; the client's refetch runs in a transaction that cannot see
   the row; the message names a chore the agenda does not show. It **self-heals
   on the next refetch**, so no test fails and the report is "I saw a message
   about a chore that wasn't there". Pinned by a reflective assertion on the
   annotation, because there is nothing else that could fail.
2. **Broadcasting and filtering on the client.** Passes everything; leaks every
   private list's descriptions to every connected browser.
3. **Unioning the owner into the recipient set**, or caching list ids on the
   emitter. The first tells somebody about a chore they cannot see; the second
   keeps telling somebody about a list they were removed from.
4. **Two threads writing one emitter.** Interleaved frames are dropped events
   with no exception on either side.
5. **An emitter with no explicit timeout.** Works perfectly, at 120 reconnects
   per tab per hour.
6. **A map key removed from under a concurrent add.** Connected, heartbeating,
   receives nothing.
7. **Dropping `Source` from the event.** The member who wrote the standing
   instruction is the one person who never hears about its chores.
8. **Forgetting `['lists']`.** A member who switches to the list screen sees a
   cached payload one chore short, with nothing to refetch it. The same shape
   three other places in this codebase have had to get right.
9. **An announcement stomping the completion message.** Both paths individually
   correct, both individually tested, and the member never learns whether their
   completion landed. `DashboardPage.test.tsx` carries the regression.
10. **An exception thrown from the listener.** It runs on the publishing thread
    after the commit, so it turns a successful create into a 500 on a row that
    was saved. Caught and logged; notification is best-effort by definition.
11. **A buffering front door.** Everything works in local development and the
    message never arrives in dev or prod. `X-Accel-Buffering: no` is the ask,
    the fallback is the insurance, and `test_live.py`'s fifteen-second timeout is
    what refuses to call the fallback a success.
12. **A global `EventSource` fake in `setup.ts`.** Every test then holds a fake
    connection and the degraded path is never exercised by anything.

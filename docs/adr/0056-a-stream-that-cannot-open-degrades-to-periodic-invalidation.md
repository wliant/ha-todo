# 0056 - A stream that cannot open degrades to periodic invalidation

Status: accepted
Date: 2026-11-19

## Context

[adr/0054](0054-new-work-reaches-an-open-agenda-over-sse.md) accepts that the
stream passes through a reverse proxy configured outside this repository. If
that proxy buffers the response, or refuses to hold it open, the agenda is back
to where it started - except that it now *appears* to have a live connection,
which is worse than not having one.

[requirements/0021](../requirements/0021-an-agenda-that-keeps-itself-current.md)
R186 therefore requires that the agenda bring itself up to date even when the
connection cannot be made, and says explicitly that the naming (R180) is what
may be lost instead.

The complicating constraint is that the dashboard's read hooks are deliberately
bare - `useScheduled`, `useUndated` and `useOverdue` take a key and a function
and nothing else, and `useSetStateFromAgenda` relies on the `['dashboard']`
prefix reaching every one of them, including any added later.

## Decision

When the stream cannot be opened - no `open` event within ten seconds, an
`error` with `readyState === CLOSED`, or no `EventSource` in this browser at all
- the same hook starts invalidating the **`['dashboard']` key tree** every sixty
seconds while the document is visible, and stops when a stream opens.

The fallback lives in `useLiveUpdates` and names the key tree. **No query hook
changes.**

No message is raised in degraded mode.

## Consequences

- **R179 survives a proxy we do not control; R180 does not.** That is the stated
  price, and it is written into the requirement rather than discovered later.
- **A fourth dashboard query added later is covered for free**, because the
  prefix reaches it - the same property `useSetStateFromAgenda.onSettled` relies
  on, and the reason this is one `invalidateQueries` rather than a
  `refetchInterval` on each of three hooks. Three places to remember is how the
  fourth gets forgotten.
- **The query hooks keep their shape.** Nothing about them says a word about
  liveness, so a reader of `dashboard/queries.ts` is not left wondering which of
  two freshness mechanisms is in charge.
- **The hidden-tab guard matters.** Polling forever in a background tab is the
  cost the user chose against when they chose push over polling, and
  `refetchOnWindowFocus: true` already covers a tab coming back.
- **Degraded mode is reachable in ordinary use**, not only behind a broken
  proxy: `app.live.enabled: false` makes the endpoint 404, which the client
  cannot distinguish from any other failure to connect. That is the feature's
  kill switch and it is meant to be used this way.
- **The e2e test must stay tight enough to tell push from fallback.** A timeout
  comfortably longer than sixty seconds would be satisfied by degraded mode, and
  would certify a build in which the stream never works at all. `test_live.py`
  waits fifteen seconds for exactly this reason, and a failure there is a
  finding about the front door rather than a number to raise.

## Alternatives considered

**No fallback - push or nothing.** Simpler, and honest in the sense that a
broken connection is visible as a stale agenda. Rejected because the stale
agenda is the bug this whole feature exists to fix, and because the thing most
likely to break it is a proxy nobody would think to look at.

**`refetchInterval` on each dashboard hook, enabled by the hook's state.** Three
hooks to change, a fourth to forget, and the hooks acquire a dependency on
whether a stream happens to be open - which is not something a query about
undated work should know.

**Showing the member that the connection is down.** Tempting, and genuinely
informative, but it makes an infrastructure detail a member can do nothing about
into a permanent piece of furniture on the landing page. Left as an open
question in the requirement.

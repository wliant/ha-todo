# 0040 - Overdue is a day boundary, read from the server's clock

Status: accepted
Date: 2026-11-07

## Context

`requirements/0011` puts overdue work on the agenda (R88-R93). Four things about
the existing code decide the shape, and two of them are traps.

- **The agenda's ranged read is parameterised by the client.**
  `GET /api/dashboard/scheduled?from=&to=` takes two instants, and
  `TodoItemRepository.findScheduledFor` is half-open on them. A comment there
  says the bounds come from the client because "only the browser knows where
  this member's midnight is" - which **stopped being true at `adr/0036`**, when
  the household got one timezone, served to the SPA on `/api/me`. The client
  still sends the bounds; it now derives them from a value the server gave it.
- **The undated panel is the only range-independent read**, and it is the shape
  to copy: its own endpoint, its own query key, a capped page plus a separately
  counted total, and a position in the layout owned by `DashboardPage` rather
  than by any one view.
- **"Now" and "the period on screen" are different things since `adr/0037`**,
  and `requirements/0008` records that confusing them is a mistake this product
  can now make. Overdue is defined entirely in terms of the first.
- **`useCompleteFromAgenda.onMutate` rewrites each cached payload by hand**,
  naming `['dashboard', 'scheduled']` and `['dashboard', 'undated']` separately.

## Decision

**Overdue is `due_at < start of today in `app.timezone``, computed on the
server, served by its own range-independent endpoint, and drawn above the
period.**

- **`GET /api/dashboard/overdue?scope=`** → `OverdueResponse(items, total)`.
  Modelled on `/dashboard/undated` exactly: same `EXISTS (list_access)` guard in
  SQL, same `done = FALSE`, same `LIMIT`, same separately counted total, same
  `DashboardItemResponse`.
- **The boundary is not a parameter.** `DashboardService` reads
  `HouseholdProperties.zone()` and `Instant.now()` and computes the start of
  today itself. The client sends `scope` and nothing else.
- **The granularity is the day**, so a chore due at 09:00 is today's work until
  midnight.
- **One surface, above the grid**, beside `StaleFeedsNotice` and
  `OutsideWindowNotice` in `DashboardPage`'s notices: collapsed to a count and
  the oldest few rows, expandable to the capped page. Not in the right-hand
  column, and not inside any range view.
- **It carries `CompleteControl`**, through the same `useCompleteFromAgenda`
  mutation the Day row uses, whose `onMutate` learns the third payload shape.

## Consequences

- **Overdue cannot drift with navigation.** There is no window in the request,
  so there is nothing for an anchor to leak into. R89 holds by construction
  rather than by a reviewer noticing.
- **The item appears in exactly one place per period.** Day granularity is what
  makes R91 true: an instant boundary would move a chore out of today's row
  mid-morning, and for the minutes either side of the cutoff the same chore is
  drawn twice on one screen.
- **The count is stable for a whole day**, so it does not change under a member
  who is reading it, and two members looking at the same agenda in the same day
  see the same number.
- **The server owns the definition of late**, so a device with a wrong clock, or
  a member travelling, cannot produce a different answer from the person beside
  them. This is `adr/0036`'s rule applied to the one piece of the agenda that
  had not needed it yet.
- **`/dashboard/scheduled` is left alone.** It keeps its client-supplied window,
  which is now redundant rather than wrong; changing it is a separate change
  with its own migration of callers and no behaviour to show for it.
- **A third cached payload shape.** `['dashboard', 'overdue']` has to be handled
  in `useCompleteFromAgenda.onMutate` alongside the other two. Forgetting it
  does **not** break the feature - `onSettled` invalidates `['dashboard']`
  wholesale - so the row lingers for one round-trip and no test fails. Same
  class of defect as the `['lists']` invalidation `adr/0039` records, and the
  reason both are written down.
- **The strip is above every view on every range**, including the ones where it
  is irrelevant (a member planning March). Collapsed, it is one line.
- **Three cross-list reads now exist** where `0005` had two. A fourth would be
  the point to ask whether `/dashboard` should serve one payload with named
  sections rather than an endpoint per question.

## Alternatives considered

**`due_at < now()`, to the minute.** The literal reading of "overdue", and
rejected by R91. It makes a row move for a reason nothing on screen explains,
puts the same chore in two places around the boundary, and gives a count that
ticks upward while somebody reads it. A household does not plan to the minute;
it plans to the day, which is also how every other bucket on this screen works.

**Carrying overdue items into today's cell**, as "what is left" rather than as a
separate set. Reads beautifully on the Day view and lies everywhere else: in the
week grid the same chore is drawn on the Tuesday it was due *and* on today, and
the member can no longer see how late it is. It also quietly redefines the day
cell from "due this day" to "due this day or earlier and still open", which the
month grid cannot express at all.

**Widening `/dashboard/scheduled` to take a `from` of `null`, meaning
open-ended.** One endpoint, and one query whose results mean two different
things depending on a parameter - the ranged read would stop being bucketable by
day without inspecting each row. The undated panel already established that a
range-independent question gets its own endpoint, for the plainer reason that
sharing a key would refetch it on every tab change.

**A client-side filter over a widened window.** Fetch from the start of time
and partition in the browser. Rejected on the same grounds the access guard is
in SQL: an unbounded window against `LIMIT 500` silently truncates, and the rows
it truncates are the oldest - exactly the ones this feature exists to surface.

**The right-hand column, beside "No date yet".** The obvious home, and wrong on
a phone: at `xs` that column stacks *below* the outlet, so the highest-priority
category in the product would sit under a six-row month grid. Late work has to
be above the fold on the device the household actually uses.

**A fourth tab.** Refused by `requirements/0011`: the tabs are periods, overdue
has no neighbours and no `?on=`, and putting it behind a tab means a member only
sees it if they already suspected it was there.

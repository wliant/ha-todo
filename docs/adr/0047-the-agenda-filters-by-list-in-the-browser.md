# 0047 - The agenda filters by list in the browser, and never filters an event

Status: accepted
Date: 2026-11-11

## Context

0014 R123 asks for a list filter on the agenda. The agenda is three cross-list
reads - `GET /api/dashboard/scheduled`, `/undated` and `/overdue` - and
`DashboardController`'s own javadoc states the position those were written
under: *no list id anywhere; the caller's grants are the scope, and those come
from the session.*

Three facts about what is already there decided this:

- `DashboardItemResponse` already carries `listId`, `listName` and `listIcon`
  on every row, and all three payloads use it. Nothing new has to cross the
  wire for a client to know which list a chore came from.
- `DashboardService` pages each query at `LIMIT = 500` as a safety bound, not
  as pagination, with the access guard in SQL. A household of four never
  reaches it, so the rows in hand are the rows there are.
- The query keys mention the window and the scope. Adding a selection of lists
  to them would key a separate cache entry per combination, so the member
  exploring - ticking a list, unticking it, ticking two - would be the one
  member who waits.

`adr/0044` had already answered the identical question for a list's own filter
and for the same reasons.

An event has no list at all. It belongs to a `calendar_feed` and its owner
(`0007`), reaches the agenda through `CalendarFeedAccess` rather than
`ListAccess`, and is collapsed per occurrence across feeds. There is no join
between the two dimensions and `adr/0034` refuses to author one.

## Decision

**The agenda filters by list in the browser, over the fetch it was already
making.** `dashboard/listFilter.ts` is pure - `parseLists`, `writeLists`,
`onlyLists`, `known`, `listFilterLabel` - and the three views and two panels
apply `onlyLists` to the rows they already hold. No endpoint, no query
parameter, no query key and no Java changes.

**The selection lives in the address** as `?lists=<id>,<id>`, omitted entirely
when empty, written with `replace` rather than `push`, parsed defensively and
never throwing - `adr/0045`'s rule, applied to a second surface.

**An empty selection is no filter**, including the empty selection that results
from dropping ids for lists the member cannot see. `known` runs once
`GET /api/lists` has answered, in `DashboardPage` and nowhere else, so there is
one owner of what the selection means.

**Events are passed through untouched.** `byDayEntries(onlyLists(items, lists),
events, zone)` is the shape in all three views, and the asymmetry is the
decision rather than an oversight.

**A filtered count never comes from an unfiltered total.** `UndatedResponse` and
`OverdueResponse` carry a server-side `total` beside a capped page. A client
filter cannot narrow it, so while a filter is active the panels count the rows
they are showing and say "and possibly more" instead of a number whenever the
page was capped.

## Consequences

- No migration, no endpoint change, nothing to deploy on the backend. The whole
  feature is four new frontend files and five call sites.
- Ticking a list is instant and costs no request (R130): one cached payload
  serves every selection, and stepping between periods is unaffected.
- A narrowed agenda is a link. The default agenda still serialises to a bare
  `/dashboard/day`.
- **The revisit threshold is inherited from `adr/0044` and is the same one:**
  the day a dashboard payload is genuinely paginated rather than
  safety-capped, this predicate has to move to the server, because a filter
  applied to a page is a filter applied to a sample. The 500-row cap is the
  reason it is not already that day, and the "and possibly more" caption is the
  honest edge of it.
- Two things are now easy to break, and both fail silently. A view that filters
  `events` as well as `items` takes somebody's appointments off their agenda
  and no test outside `DashboardPage.test.tsx` would notice. And a panel that
  subtracts `items.length` from `total` while a filter is active prints a
  confident number about rows it never saw.
- A filter by calendar feed remains unbuilt and is not blocked: it would be a
  second, parallel control over the other dimension.

## Alternatives considered

- **A `lists=` parameter on all three endpoints.** The predicate is additive to
  the existing `EXISTS` access guard, so it leaks nothing, and it is five JPQL
  queries and a service signature. Rejected for the caching cost above, for
  arguing against a position the controller states deliberately, and because it
  would put a second implementation of "which list is this" on the server while
  the client already has every input.
- **Component state in `DashboardPage`.** Cheapest to write, and it loses the
  filter on every navigation, cannot be linked, and reintroduces exactly the
  state `adr/0015`, `adr/0037` and `adr/0045` have kept off this page.
- **Hiding events while a filter is active.** Considered and refused: it makes
  one control narrow two unrelated dimensions, and it hides the half of the
  agenda the member did not ask about.
- **Reusing `GET /api/lists/sidebar` for the menu.** Rejected: its `lists`
  array is capped at the member's `sidebar_list_count` and partitioned against
  the pinned section, so it is deliberately not the full set.

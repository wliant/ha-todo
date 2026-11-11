# 0044 - A list filters and sorts in the browser, over the whole list

Status: accepted
Date: 2026-11-11

## Context

`requirements/0013` asks for filtering by status, assignee, due date and every
cost a list declares, plus a sort key and direction (R107-R114). The question
that decides the shape of the whole feature is where those predicates run.

Three facts about what already exists:

- **`GET /api/lists/{listId}/items` returns the entire list**, unpaginated, and
  each `ItemResponse` already carries everything R107-R114 filters on: the
  assignee, `dueAt`, `completed`, and a `costValues` entry per answered cost.
  The client is not missing a single input.
- **The client already fetches the list three times over**, once per value of
  the `status` parameter, because the parameter is part of the query key. The
  To do / Done / All toggle refetches on every tap.
- **A household is four people.** The largest plausible list is a chore list a
  recurrence rule has filled daily for a year, which is low thousands of rows
  at a few hundred bytes each - and R119's status filter means the common view
  is already asking for a superset of what it draws.

Against that, the cost of the server-side version is not small. A cost filter is
a multi-select over choices *or* a numeric range, per field, with "not
answered" (R111) as a third state - which in SQL is an `EXISTS` per cost field
with a `NOT EXISTS` alternative, assembled dynamically, against the same
`item_cost_value` table the item write path merges by identity. Sorting by a
cost is a `LEFT JOIN` per field with nulls last. Each distinct filter
combination is a new cache key and a new round trip, on a control a member taps
repeatedly while deciding.

## Decision

**The filter and the sort are computed in the browser, over one unfiltered
fetch of the list.**

- The client asks for the list **once** per list, and `fetchItems` no longer
  takes a status. `itemKeys.filtered` is gone; there is one items key per list.
- `items/filters.ts` is pure: it parses and serialises the filter, and exposes
  `visibleItems(items, filter, fields, zone, now)` - a predicate and a
  comparator and no React, no clock of its own, and no fetching.
- **The server's `status` parameter is removed**, along with the branch in
  `TodoItemRepository.findForList`. One implementation of "what counts as
  done", in one place, and it is the browser's.

## Consequences

- **No migration, no endpoint, no response change.** The feature is one design
  step, and the per-list access guard it depends on is the one already enforced
  on the read.
- **Re-filtering is instant and free.** No request, no spinner, no key per
  combination, and the bar can be tapped as fast as a member likes.
- **Completion interacts with the filter in the client.** The optimistic write
  in `useToggleCompletion` sets `completed`, so with a To do filter the row
  leaves immediately rather than after the refetch. That is why R119 exists, and
  why `ItemsSection` raises a snackbar when a completion removes a row - the
  same reasoning as `adr/0039`.
- **The limit is the payload, and it is now the only limit.** A list drawing
  thousands of rows is slow because it is *drawing* thousands of rows, not
  because it filters them. **The threshold for revisiting this is a list that
  needs pagination**: the moment the client stops holding the whole list, every
  predicate here has to move to the server, and it has to move *all at once* -
  a filter applied to a page is a filter applied to a sample. Nothing here
  should be half-moved.
- **`done = TRUE/FALSE` keeps exactly one reader in Java**, the dashboard's,
  which needs it for a reason this does not have: the agenda reads across lists
  and could not fetch them all.
- **An integration test and a recurrence test lose their `?status=` query
  string**, and `ItemApiIntegrationTest`'s status-filter assertion is replaced
  by a frontend test over `visibleItems`. The coverage moves with the logic.

## Alternatives considered

**Server-side filtering through query parameters.** The scalable answer, and it
buys nothing at this size while costing a dynamically assembled statement over
`item_cost_value`, a cache key per combination, and a round trip per tap. It
would also leave status filterable in two places at once, which is how two
implementations of one rule start to disagree.

**Keep the server's `status` parameter as an unused affordance**, in case some
future caller wants it. Rejected: nothing sends it, so nothing would notice it
breaking, and a predicate that exists on both sides of the wire is precisely
the duplication this decision is trying not to have.

**Filter in the browser but keep fetching per status**, which would have been
the smallest diff. Rejected: with status multi-select (R108), "To do and Done"
and "neither" both mean the whole list, so the parameter would have had four
client-side meanings mapping onto three server-side values - and the one
request that returns everything is also the one the other filters need.

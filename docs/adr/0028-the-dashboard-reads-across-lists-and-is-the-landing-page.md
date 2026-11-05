# 0028 - The dashboard reads across lists, and is the landing page

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R44 (a dashboard), R45 (undated items are never hidden)
Amends: [0007](0007-installable-pwa-without-offline-caching.md) - the manifest's
`start_url`. [0015](0015-a-sidebar-replaces-the-dual-navigation-bar.md) - the
sidebar's `MENU` section gains an entry above Lists.

## Context

Every read path in this product so far starts from one list: `GET
/api/lists/{id}/items`, `GET /api/lists/{id}/points`. Authorization is per-list
(`adr/0010`) and is enforced by a `list_access` join in SQL, so "which list" has
always been the first thing a query knew.

R44 asks for a surface that does not start from a list. It reads every list the
caller can see, in three date ranges, and R45 adds a fourth panel of undated items
shown beside all three. That is the first query in the product where a missing
access guard would leak *several* members' private lists at once rather than one -
and the first where the guard cannot be a path parameter the controller checked.

Three further things need deciding. Whether the three ranges are three endpoints or
one. Whether the dashboard shows only what is assigned to the caller or everything
they can see. And whether this becomes the app's landing page, which R44's framing
("at a glance") implies but does not state.

There is no date library in `web/`, no calendar component, and no
`@mui/x-date-pickers`. All date handling today is `Intl` plus native
`<input type="datetime-local">`.

## Decision

**Two endpoints, not three and not one.** `GET /api/dashboard/scheduled?from=&to=`
and `GET /api/dashboard/undated`. The three ranges differ only by `[from, to)`, so
three endpoints would be three copies of the one query, each carrying its own access
guard - and the guard is the thing to have exactly one of. The undated panel is
separate because it is shown beside all three views, and folding it into the ranged
response would refetch it on every tab switch.

**`from` and `to` are instants supplied by the client**, half-open. The client
computes its own local midnights, which is right per device, needs no server
timezone, and means a member who travels sees their own "today". This is why the
dashboard needs none of the `app.household.zone` configuration that recurrence
introduces.

**The access guard is the same `EXISTS` subquery, in SQL.**

```
AND EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId)
```

the one `TodoListRepository.findVisibleById` already uses. Never a filter in Java:
a predicate applied after a full fetch passes every unit test and leaks the
household's private lists. The negative cases therefore belong in the integration
suite against real Postgres, including one asserting the **admin's** dashboard is
empty of lists they were not granted - the test that stops a future
`|| isAdmin()`.

**Everything readable, with a `scope=mine` toggle, defaulting to `all`.** The
argument is about the data rather than taste: `ASSIGNEE` is an optional field whose
`required` defaults to false, so on most lists most items have no assignee, and on a
list that never declared the field *every* item has none. A mine-only dashboard is
therefore empty or near-empty on exactly the lists this product is about, and it
hides the unclaimed chores a household dashboard exists to surface. CLAUDE.md's own
framing settles it: design around several people touching one list, not around a
single-user todo app. `scope` lives in the URL so it is deep-linkable and adds no
state.

**Both queries are capped** at 500 rows by a `Pageable`, and the undated response
carries `total` separately. The cap is not theoretical: after `adr/0022`'s backfill
every list declares a due date and nobody has filled most of them in, so the undated
panel contains very nearly every open item in the household on day one.

**`DashboardItemResponse` is `ItemResponse` plus the list's identity** - `listId`,
`listName`, `listIcon` - because the dashboard crosses lists, so every row must say
where it came from, and the icon is how a list is recognised at a glance (R18's own
argument). It carries the full payload even though the week view shows only a
description, because the popup must open instantly on a phone and the alternative is
a request per tap.

**The dashboard is the landing page**, and it moves in **three** places that must
move together: `web/public/manifest.webmanifest`'s `start_url`, the catch-all
`Navigate` in `web/src/App.tsx`, and the post-login fallback in
`web/src/pages/LoginPage.tsx`. A **Dashboard** entry goes above **Lists** in
`SideNav`'s `MENU` section.

**No date library.** `web/src/dashboard/calendar.ts`, hand-rolled: start-of-day,
start-of-week, add-days, start and end of month, and a 6x7 grid - about forty lines -
with all formatting left to `Intl`, which `ItemRow.formatDue` already uses
correctly. Weeks start Monday, hard-coded, because `Intl` exposes first-day-of-week
only through `Intl.Locale.prototype.getWeekInfo`, which is not universally
available, and this is one household with one locale. Adding a day is
`d.setDate(d.getDate() + 1)` and **never** `+ 86400000`, which is an hour wrong
twice a year and surfaces as a week view whose Monday column is empty.

## Consequences

- One cross-list read path, so there is one place the access guard can be wrong and
  one place to test it. Any future cross-list view extends these two queries rather
  than writing a third guard.
- The dashboard needs two indexes `todo_item` did not have:
  `ix_todo_item_due (list_id, due_at) WHERE done = FALSE` for the ranges and the
  `IS NULL` panel, and `ix_todo_item_assignee_due` for `scope=mine`, which does not
  start from a list. Worth confirming with `EXPLAIN` against seeded dev rather than
  assuming - the planner may well prefer a sequential scan at household row counts,
  which is fine, and saying so here stops somebody "fixing" the plan later.
- **`e2e/conftest.py`'s `ui_sign_in` waits on a `Lists` heading, and all nine test
  modules depend on it.** design/0004 warned about this exact line. It becomes the
  dashboard's `h1` in the same commit, and `AllListsPage` keeps its own `h1` for the
  list-page tests. This is why the dashboard ships last and alone: it is then also
  the cheapest thing to revert.
- An installed PWA that was opening `/lists` will open `/dashboard` after the next
  manifest fetch. `adr/0007`'s service worker caches nothing, so there is no stale
  `start_url` to invalidate - a happy consequence of that decision.
- The undated panel will be long and will stay long, because nothing forces a member
  to date anything. The cap and the count are the whole mitigation; a member who
  wants a short panel has to fill in dates, which is the incentive R40 creates.
- `scope=all` means a member sees items on a shared list that nobody has claimed and
  that may not be theirs to do. That is the intent - unclaimed work is what needs
  surfacing - but it means the dashboard is not a personal to-do list, and calling it
  "My day" slightly oversells the day view.
- Hand-rolled date arithmetic is ours to get wrong, and DST is where it will be: the
  spring-forward test is not optional.

## Alternatives considered

- **Three endpoints, one per range.** Each self-describing, each cacheable on its own
  key. Rejected because each would carry its own copy of the access guard, which is
  the one piece of logic that must not exist three times.
- **One endpoint returning all three ranges plus the undated panel.** One request per
  page load. Rejected because the month range subsumes the others, so the response
  would be mostly redundant, and the undated panel - the largest part - would be
  refetched on every tab change.
- **Server-computed ranges from a `view=day|week|month` parameter.** Simpler client.
  Rejected because the server would then need to know the member's timezone, which
  nothing stores, and "today" would be the server's day rather than theirs.
- **`scope=mine` as the default, or the only behaviour.** Reads as a personal to-do
  list, which is what a dashboard usually is. Rejected on the data: the assignee
  field is optional and mostly unused, so the default view would be empty on the
  lists that matter most.
- **Keep `/lists` as the landing page and make the dashboard a destination.** Purely
  additive, no manifest change, no e2e fixture change. Rejected because the surface
  that answers "what do I need to do today" is worth nothing if it is not the first
  thing a member sees, and the cost is one fixture and three one-line redirects.
- **`@mui/x-date-pickers` with an adapter and date-fns.** A real calendar and a real
  picker, free. Rejected on dependency grounds that CLAUDE.md states directly: it is
  a separate MUI package on its own release train, needs an adapter **plus** one of
  date-fns / dayjs / luxon (two dependencies, not one), weighs well over 100KB, and
  pinning a second MUI train against `@mui/material` 9.5.0 invites exactly the drift
  a shared lockfile punishes. It would also replace
  `<input type="datetime-local">`, which is native, accessible, free and already a
  real picker on a phone.

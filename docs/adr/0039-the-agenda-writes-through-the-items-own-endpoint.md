# 0039 - The agenda completes through the item's own endpoint, and the row leaves

Status: accepted
Date: 2026-11-07

## Context

`requirements/0010` puts the completion gesture on the agenda (R83-R87). Three
things about the existing code decide how:

- **`DashboardItemResponse` already carries `version`** and the whole item
  payload, because the popup has to open without a request. So the client
  already holds everything `PUT /api/items/{id}/completion` wants.
- **That endpoint is per-item and self-guarding.** It resolves the item, checks
  the caller's grant on its list, compares the version before mutating anything,
  and credits the points to whoever asked. Nothing about it knows or cares which
  screen called it.
- **The dashboard's query keys sit outside the per-list hierarchy
  (`['dashboard', ...]`)**, with a comment in `dashboard/queries.ts` saying why
  that is safe: the dashboard is read-only, and anything a member changes they
  change on a list, which remounts the agenda on the way back.

The third one is a premise this change destroys.

## Decision

**No backend change. The agenda calls the item's completion endpoint, and the
completed row leaves the agenda.**

- `useCompleteFromAgenda` in `dashboard/queries.ts` wraps `setItemCompleted`
  and invalidates **`['dashboard']` and `['lists']`** - both, because the write
  crosses the boundary the two key trees were drawn around.
- It is **optimistic in one direction only**: the row is removed from every
  cached agenda payload immediately, and put back if the write fails. There is
  no "completed" state to draw on this surface, so there is nothing else to
  guess.
- **The scheduled query keeps `done = FALSE`.** The agenda remains a view of
  outstanding work (R86).
- **One mutation, one note dialog and one snackbar for the whole page**, owned
  by `DashboardPage` and reached through the existing view context. The Day row
  and the item popup both call it.
- The Day row is **restructured**, not decorated: its description becomes a
  `ButtonBase` with the control as a sibling, because a row-wide button
  containing a button is invalid nesting and a screen reader reads it as
  neither. Same shape, and same reason, as `items/ItemRow`.

## Consequences

- **The feature is frontend-only** - no migration, no endpoint, no response
  change - which is why it is one design step rather than five.
- **The 409 path comes for free.** `conflictingItem` carries the server's item
  and the prose names the other member; the agenda shows that in a snackbar and
  refetches, which is R87 without new server work.
- **`dashboard/queries.ts`'s comment is now false and has been rewritten.** The
  hazard it was protecting against is live: a *second* writer added to the
  agenda that forgets `['lists']` leaves a list stale, and the only symptom is
  a list screen that is wrong until it remounts.
- **Completion is reachable from two places with one implementation.**
  `CompleteControl` and `CompletionNoteDialog` are imported from `items/`
  rather than copied; the hold-vs-note accessibility argument in `adr/0027` is
  satisfied here by construction rather than by being remembered.
- **The row vanishing is the main cost**, accepted in `requirements/0010`'s
  first consequence, and softened by a snackbar that names what was completed.
- **The agenda's completion cannot be undone from the agenda**, because the item
  is no longer there. The undo is on the list, one tap from the popup that is
  still there for every item that is.

## Alternatives considered

**Return completed items in the window and strike them through**, for parity
with a list's "All" filter (0005 R38). It is the more consistent-looking answer
and the worse one: the week and month views are a date grid of one-line chips,
and filling cells with work that is done makes the one screen whose job is "what
is left" answer something else. It also needs a `completed` flag on
`DashboardItemResponse`, a `status` parameter on `/dashboard/scheduled`, and a
decision about whether the undated panel honours it - a backend change and three
new questions to buy a worse screen.

**An optimistic strike-through that then disappears on refetch.** Two states for
one row, the second of which contradicts the first a few hundred milliseconds
later. Worse than either honest answer.

**A `/dashboard` completion endpoint**, so the agenda's write and its reads are
one controller. Rejected: it would be a second place that decides who may
complete what, which is exactly the duplication `adr/0010`'s "enforced in the
service layer" rule exists to prevent.

**Completing without the note accelerator**, leaving the note to the list.
Rejected by R84: a gesture that exists on one surface and not the other teaches
members that the note is unreliable, and the control that implements it is a
single import.

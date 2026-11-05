# 0026 - Claiming is recorded, not inferred

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R46 (a member claims work in bulk), R47 (claimed work is visibly claimed)
Refines: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R24 (assignment)

## Context

R47 asks that an item somebody took for themselves look different from one somebody
else gave them, and that **every member see the same mark**. 0004 stores only
`assignee_id`, so "Ben is the assignee" is all the data says - and that one fact has
to serve two different household meanings. "Ben took this" means the work is spoken
for. "Ana gave this to Ben" means it has been delegated and may not be. Only the
first tells the rest of the household to stop thinking about it.

The cheap reading of R47 is viewer-relative: colour the badge when the assignee is
whoever is looking. That needs no schema at all, and it is a different feature -
"this one is mine" rather than "this one is claimed" - and it fails R47's explicit
requirement that everybody see the same mark.

R46 then adds a second question. Assigning several items at once is one member
intent, but every mutating request in this product carries the version its view was
drawn from (design/0004), and the existing 409 returns exactly **one** item for
reconciliation. A bulk write has to decide what happens when one of eight items was
touched by somebody else in the meantime.

## Decision

**`todo_item.assigned_by` records who put the assignee there**, and self-claim is
the predicate `assignee_id IS NOT NULL AND assignee_id = assigned_by`. One shared
truth: Ana and Ben see Ben's badge marked the same way, because it is a fact about
the item rather than about the reader.

**The API publishes a boolean, not the id.** `ItemResponse` gains
`selfAssigned`, computed server-side. `assigned_by` itself is deliberately *not*
exposed as a member, because the badge needs one bit and the id would publish "Ana
assigned this to Ben" as a separate fact nothing in the product asks for.

**No CHECK pairs `assigned_by` with `assignee_id`.** The obvious
`CHECK (assignee_id IS NOT NULL OR assigned_by IS NULL)` would make deleting an
account fail: `assignee_id` is `ON DELETE SET NULL` and so is `assigned_by`, and
Postgres applies the FK actions as an update that the CHECK then rejects - aborting
the delete of exactly those accounts that were used. The constraint buys nothing
either, since the self-claim test is already false on a null assignee. **Never write
a CHECK spanning a column with an `ON DELETE SET NULL` action and one without.**

**Bulk assignment is one list-scoped endpoint.** `POST
/api/lists/{listId}/items/assignment`, taking `assigneeId` (null unassigns) and a
list of `{id, version}`. Scoped to the list so there is one readable check and one
assignee-audience check (R24); a cross-list version would need N of each and could
silently span lists the caller sees differently. Generalised beyond "me" because it
is the same code path, and `assigned_by` is always the caller - which is what makes
`assigneeId == caller` render as claimed.

**Partial success, and always 200.** Fresh items are assigned; stale ones come back
in a `conflicts` array carrying the server's item. The reasoning: a member who
selected eight items and had one touched by somebody else - possibly a description
edit with nothing to do with assignment - should not have to re-select all eight on
a phone, because their intent for the other seven is unambiguous. 200 rather than
207 because `api/http.ts`'s `isConflict` branches on 409 and a 207 would be a third
branch nothing in the SPA has; the body is the contract here, not the status.

**A non-throwing `isFreshView`.** `requireFreshView` throws, so it cannot be reused
in a loop. The stale-tab case - design/0004's mechanism (1) - is detected and
skipped without mutating anything. The rare *true* race, mechanism (2), cannot be
isolated per item without flushing inside the loop, and a failed flush poisons the
Hibernate session; so a genuine concurrent flush fails the whole batch with a plain
409 carrying no item, which the client's existing "409 without an item means
invalidate and refetch" path already handles.

**An id not on this list is an ordinary conflict**, `item: null`, with the same "no
longer here" prose as a deleted one - never a distinct status, because an id on a
list the caller cannot see must answer as a deleted one does (R19).

## Consequences

- "Claimed" is a durable fact, so it survives a page load, is the same for everybody,
  and is assertable in a test without reasoning about who is signed in.
- `scope=mine` on the dashboard finally has something to filter, and R46 is what
  makes it worth having: before bulk claiming, most items on most lists had no
  assignee at all.
- A member who claims an item and is then reassigned it by somebody else loses the
  claimed mark, because `assigned_by` is overwritten. That is correct - it is no
  longer self-chosen - but it means the mark can disappear without the assignee
  changing, which will look like a bug until this line is read.
- Nothing migrates. Every existing item has `assigned_by` null, so nothing is marked
  as claimed, including items a member did in fact assign to themselves before this
  change. Backfilling is impossible - the information was never recorded - and
  guessing from `created_by` would be fiction.
- Partial success is a second response shape for assignment, and the client must
  handle both arrays. The payoff is that the common failure needs one more tap on
  exactly the right items rather than a full re-selection.
- Bulk assignment cannot span lists. A member selecting across the dashboard would
  need one request per list, which the dashboard does not offer and R46 does not ask
  for.

## Alternatives considered

- **Viewer-relative accent: colour the badge when the assignee is the reader.** Zero
  schema, and "mine" reads at a glance. Rejected because it answers a different
  question and fails R47 outright: Ana would see her own name accented on an item
  Ben assigned her, and nobody could tell a claim from a delegation.
- **Both cues - `assigned_by` for claimed, a border for "yours".** Most informative.
  Rejected as two visual languages on one chip for a household of five, where the
  reader almost always knows which name is theirs.
- **An enum `assignment_source` of `SELF | OTHER`.** Expresses the one bit directly
  and needs no second FK. Rejected because it is derivable from `assigned_by` and
  therefore a second truth that can disagree, and because `assigned_by` answers "who
  delegated this" if that is ever asked - at the same cost.
- **All-or-nothing bulk assignment.** Matches the single-item contract exactly and
  has one response shape. Rejected because one contended item would discard seven
  unambiguous intentions, and a member on a phone would have to re-select all of
  them. R30's "nothing is retried behind the member's back" is about not retrying
  silently, not about refusing work nobody contended.
- **A 207 Multi-Status.** Honest HTTP. Rejected as a third status branch in a client
  whose error helpers know 401, 404 and 409, for no gain the body does not already
  provide.
- **N parallel `PATCH /api/items/{id}` calls from the client.** No new endpoint.
  Rejected because it does the assignee-access check N times, produces N independent
  409s with no coherent story, and makes "assign these four" four chances to
  half-fail.

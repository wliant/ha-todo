# 0020 - Changing a chore where it is seen

Design for [requirements/0020](../requirements/0020-changing-a-chore-where-it-is-seen.md)
(R173-R178). **Frontend only**: no endpoint, no DTO, no migration. An edit from
the agenda is an ordinary `PATCH /items/{id}` with a body the client assembled,
and `DashboardItem` already carries every component `SaveItem` needs - the
detail, the assignee, the due date, the costs and the **version**. That it does
is not luck: the popup already draws all of them.

No new ADR. This widens [adr/0039](../adr/0039-the-agenda-writes-through-the-items-own-endpoint.md)
a third time along the axis 0018 established - the agenda may write whatever
needs no list to be picked - and every decision below is one of 0018's, applied
to a PATCH instead of a POST.

## Scope

Six files, one of them new on each side.

1. **`items/edit.ts`** (new) - `editableOf(item)`, the one definition of what a
   form holding an existing item starts from.
2. **`items/ItemsSection`** - its private `fromItem` deleted in favour of it.
3. **`dashboard/EditItemDialog`** (new) - the agenda's copy of the list's form
   (R173, R175, R178).
4. **`dashboard/DashboardItemDialog`** - an Edit button beside Duplicate.
5. **`dashboard/dashboardView`** / **`DashboardPage`** - the action and the
   state behind it.
6. **`dashboard/queries`** - `useUpdateItemFromAgenda`, which names both key
   trees (R177).

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| A form built from a list's declared fields | `items/ItemForm` | The agenda reuses it rather than growing a second form |
| An update that checks the grant and the version | `PATCH /items/{id}` | R176 is the server's existing behaviour, not a new rule |
| Every value an edit needs, on the agenda's payload | `DashboardItem` | Including `version`, which 0010 added for the completion |
| The list's configuration, cached under `['lists']` | `useList`, `useListFields` | Shared with the list screen's cache entries |
| A page-level snackbar | `DashboardPage`'s `said` | R177's confirmation has somewhere to go |

## Step 1 - one definition of what an edit starts from

`editableOf(item: Item | DashboardItem): SaveItem & { id: string }` returns the
description, the detail, the assignee id, the due date, the costs **and the
version**. It is `duplicateOf`'s mirror and deliberately its opposite: a
duplicate carries two components because the rest are what made the original
*that* occurrence; an edit carries **all** of them because a form that opened
missing one would write the blank back over it on the next save.

The union type is the point, as it is for `duplicateOf`. `ItemsSection` had
this function privately, as `fromItem`; it is moved rather than copied, so
"what an edit holds" stays one answer. A second copy is how one surface comes
to drop a field that nothing fails on.

The `id` present is what makes the save a PATCH, exactly as its absence makes
a duplicate a POST.

## Step 2 - the agenda's dialog

`EditItemDialog` is `DuplicateItemDialog` with three differences, and nothing
else is worth saying about it:

- it seeds from `editableOf` rather than `duplicateOf`;
- it calls `useUpdateItemFromAgenda` rather than `useCreateItemFromAgenda`;
- it has no explanatory line, because there is nothing to explain.

The **three load states** carry over unchanged and are the thing most likely to
be lost in a tidy-up: `pending`, `error` and `ready` are separate, because a
form drawn from `fields ?? []` asks for a description alone while the request
is in flight and is indistinguishable from a failed one. That is `NavGroup`'s
0012 bug, and it has now had to be got right three times.

`expanded` (R178) is the opposite of adding and the same as duplicating, for
the opposite reason: a duplicate is expanded because what is left to answer is
what was not copied, an edit because what is worth seeing is everything that
is already there.

The form's value is held in state, seeded **once**. The dialog is keyed on the
item in `DashboardPage` so a second chore opens a fresh instance, which is the
same rule `CompletionNoteDialog` and `DuplicateItemDialog` follow and exists
because `DetailEditor` takes its `value` as initial content and never re-syncs.

## Step 3 - the popup

`DashboardItemDialog` gains `onEdit` and an Edit button. Both form-openers sit
**after** everything that writes - Complete, Lapse, then Edit, Duplicate - on
0018's rule read the other way: a mis-tap on a control that opens a form costs
a Cancel, so those are the ones that may sit next to each other.

`DashboardPage.startEditing` closes the popup first, as `startDuplicating`
does: two dialogs about one item stacked on each other is one dialog too many
(0005 R38).

The action reaches the popup through `ViewContext`, like every other verb,
taking the **item** rather than an id - the version the save will carry comes
from the payload the row was drawn from.

## Step 4 - the invalidation, which is the part that can be wrong invisibly

`useUpdateItemFromAgenda` invalidates **`['dashboard']` and `['lists']`**. It is
the fourth writer on this page that has to name both trees, and the failure is
the same every time: the prefixes are siblings, neither reaches the other, and
forgetting `['lists']` leaves the list screen showing the old date with nothing
to make it refetch. Nothing fails; a member notices days later.

It is **not optimistic**, for `useCreateItemFromAgenda`'s reason and one
stronger: an edit can change the due date, so *which* cached payload the chore
belongs in is precisely what changed. Guessing would mean re-running every
view's window predicate by hand - work the refetch does correctly for free -
and there is no row moving under a finger to justify it, because the dialog is
still open when the server answers.

A 409 is rendered in the dialog as a warning Alert with the server's prose,
which names the other member. The form stays open holding what was typed.

## Tests

`web/src/dashboard/DashboardPage.test.tsx`, two cases:

- **"opens the list's form holding everything the item already is"** - clicks
  Edit on the popup, asserts the list's form appeared prefilled, changes the
  description and asserts the PATCH carried every component including the
  version and the costs. That assertion is the regression for step 1: a
  `editableOf` that dropped a field passes every other test in the suite.
- **"does not ask for a description alone while the list is still loading"** -
  resolves `fetchListFields` against a promise that never settles, so the query
  cannot rescue the assertion.

The list screen's own edit path is already covered by `ItemsSection.test.tsx`
and did not change behaviour - only where its helper lives.

## What is deliberately not built

- **No agenda-row Edit** (0018 R153's reasoning, unchanged).
- **No delete.** It destroys a record and has no undo; it stays on the list.
- **No list picker**, which is the one question this surface can avoid asking.
- **No second form component.** `ItemForm` is the list's form wherever it is
  drawn.

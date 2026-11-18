# 0018 - Adding another one like this

Design for [requirements/0018](../requirements/0018-adding-another-one-like-this.md)
(R153-R158). **Frontend only**: no endpoint, no DTO, no migration. A duplicate
is an ordinary `POST /lists/{id}/items` with a body the client assembled, and
`SaveItem` already carries every component it needs - which is R155 read as a
signature.

## Scope

Six files, and one new one on each side.

1. **`items/duplicate.ts`** (new) - `duplicateOf(item)`, the one definition of
   what a duplicate copies.
2. **`items/ItemRow`** - a Duplicate icon button beside Edit (R153).
3. **`items/ItemsSection`** - opens the add form, prefilled and expanded
   (R154).
4. **`dashboard/DuplicateItemDialog`** (new) - the agenda's copy of the list's
   form (R156, R157).
5. **`dashboard/DashboardItemDialog`** / **`dashboardView`** /
   **`DashboardPage`** - the Duplicate action and the state behind it.
6. **`dashboard/queries`** - `useCreateItemFromAgenda`, which names both key
   trees (R158).

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| A form built from a list's declared fields | `items/ItemForm` | The agenda reuses it rather than growing a second form |
| A create that is a create because it has no id | `ItemsSection.save` | `editing.id` already decides POST from PATCH; a duplicate simply has none |
| The list's configuration, cached under `['lists']` | `useList`, `useListFields` | The agenda's dialog shares the list screen's cache entries |
| Server-side field discipline | `TodoItemService` | A value for an undeclared field is dropped, so a stale copy cannot smuggle one in |
| A page-level snackbar | `DashboardPage`'s `said` | R158's confirmation has somewhere to go |

## Step 1 - one definition of what a copy is

`duplicateOf(item: Item | DashboardItem): SaveItem` returns the description and
the detail, with `assigneeId`, `dueAt` and `costValues` empty, and **no `id`
and no `version`**. The union type is the point: both surfaces call the same
function, so "what a duplicate carries" (R155) is one answer rather than two
that drift. The absent `id` is what makes the save a create (R154) without any
caller having to remember.

## Step 2 - the list's row

`ItemRow` gains `onDuplicate` and an icon button, grouped with Edit and
**outside** the complete/lapse group. The grouping is the same rule 0015
followed: the completion group is one gesture with one accelerator, and
anything that is not that gesture sits beside it. Edit and Duplicate belong
together because neither writes anything - both open a form, so a mis-tap costs
a Cancel.

It is rendered for every state (R153). `closed` already decides between the
complete controls and Reopen; Duplicate is below that branch and so is
unaffected by it.

`ItemsSection.startDuplicating` sets the form **expanded**. That is deliberately
the opposite of adding: 0017's collapsed form hides what is expensive to
answer, and after a duplicate the only things left to answer are exactly the
date, the assignee and the costs that R155 refused to copy. A collapsed form
would hide the whole remaining task.

The dialog's title needs three answers, so `ItemsSection` grows one boolean,
`duplicating`. Only the title reads it - `editing.id` still decides create from
update - and every path that opens the form sets it, so it cannot survive into
the next one.

## Step 3 - the agenda

The agenda's payload is `DashboardItem`, which carries what a grid cell needs
and therefore carries **no fields and no members**. R157 says the copy goes on
the list it came from, which means the list's own form, which means its
configuration has to be fetched. `DuplicateItemDialog` does that with
`useList(item.listId)` and `useListFields(item.listId)` - the list screen's own
hooks and its own cache keys, so this costs nothing to a member who then opens
that list.

Three states, not two. A form drawn from `fields ?? []` would ask for a
description alone while the request was in flight and then rearrange itself
under somebody already typing; worse, it would be indistinguishable from a
failed request. So the form renders only when both have arrived, a skeleton
stands in, and an error says so. This is the same mistake `NavGroup` shipped
with in 0012 and the reason `PinnedSection` had a `loaded` guard from the
start.

The action lives on `DashboardItemDialog` rather than `AgendaItemRow` (R157's
"explicitly not required"): the row has two write controls already, and a third
icon on a phone row is a mis-tap. One control on the popup also *is* every
view - the week grid, the month grid, the undated panel and the overdue strip
all open into it.

`duplicate` joins the `ViewContext` beside `complete`, `writeNote` and `lapse`,
and `DashboardPage` holds the chore being copied in state. Not in the address,
unlike `?item=`: a half-filled form is not something a link can restore, and
putting it there would promise that it could. The dialog is keyed on the item's
id for the reason `CompletionNoteDialog` is - the form seeds its value once.

## Step 4 - the invalidation, which is the part that can be wrong invisibly

`useCreateItemFromAgenda` invalidates **`['dashboard']` and `['lists']`**. This
is `adr/0039`'s rule applied to a third writer: the two key trees are siblings,
neither prefix reaches the other, and the symptom of forgetting `['lists']` is
a list screen that is one item short with nothing to make it refetch. It
deliberately does *not* reuse `items/queries`' `useCreateItem`, whose
invalidation knows about `['lists']` and nothing about the agenda - calling it
from here is the shape of the bug.

It is **not optimistic**, unlike the agenda's state write. Where a new chore
lands depends on the date just typed and on the window each cached payload
asked for, so a hand-written insert would have to reimplement the server's day
bucketing in three payload shapes; the refetch does it correctly for nothing.
And there is no row moving under a finger to cover - the dialog is still open
when the request returns.

## Tests

- **`ItemsSection.test.tsx`** - duplicating carries the description and the
  detail and nothing else (asserted as the whole `createItem` body, so a
  carried date fails); the dialog is a create, with no Delete; a **completed**
  item offers it too.
- **`DashboardPage.test.tsx`** - Duplicate on the popup opens the list's form
  prefilled, saves against the chore's own list, and says so; the form does not
  ask for a description alone while the configuration is still loading (against
  a promise that never settles, so no refetch can rescue the assertion).

## What is deliberately not built

- No `POST /items/{id}/duplicate`. The server would have to decide what a copy
  carries, which is a product decision that then exists in two places, and the
  client has every value already.
- No lineage column, no "copied from". Nothing reads it (R157's non-goals).
- No duplicate on `AgendaItemRow`, on `RecurrenceSection`, or on a collection
  entry.

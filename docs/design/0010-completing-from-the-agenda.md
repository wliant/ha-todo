# 0010 - Completing a chore from the agenda

Design for [requirements/0010](../requirements/0010-completing-from-the-agenda.md)
(R83-R87). **Frontend only**: no migration, no endpoint, no response change.

Decision this implements rather than argues:
[adr/0039](../adr/0039-the-agenda-writes-through-the-items-own-endpoint.md).

## Scope

One step. The write already exists and the client already holds everything it
needs; what is missing is a control, a mutation that invalidates both key trees,
and somewhere for the conflict to be said.

## What is already there

| Piece | Where | Why it is enough |
| --- | --- | --- |
| The write | `PUT /api/items/{id}/completion` | Per-item, checks the caller's grant, compares the version first, credits the caller |
| The version | `DashboardItem.version` | The agenda payload carries the whole item so the popup opens without a request |
| The gesture | `items/CompleteControl` | Press completes; hold offers the note, with a visible tabbable alternative (`adr/0027`) |
| The note | `items/CompletionNoteDialog` | Keyed on the item, because `DetailEditor` never re-syncs its value |
| The conflict | `conflictingItem(error)` | The server's item rides the 409, and the prose names the other member |

## The pieces

### `dashboard/queries.ts` - `useCompleteFromAgenda`

```ts
mutationFn: ({ item, noteHtml }) =>
  setItemCompleted(item.id, item.version, true, noteHtml)
```

- **`onMutate` removes the row** from every cached `['dashboard']` payload -
  both `Scheduled` and `Undated`, which nest their items inside an object, so
  this is a map over the payload rather than over an array.
- **`onError` restores the snapshot**, and reads `conflictingItem` only for its
  message: there is no completed row to replace on this surface, so the server's
  item has nothing to be written into. The refetch is the reconciliation.
- **`onSettled` invalidates `['dashboard']` *and* `['lists']`.** The second one
  is the part that is easy to leave out and impossible to notice: the agenda
  looks right and the list it came from is stale. The file's old comment said
  this could not happen because the dashboard never writes; that comment is now
  wrong and has been replaced with the rule.

### `dashboard/dashboardView.ts` - two more context members

`complete(item)` and `writeNote(item)`, beside `open` and `openEvent`. The
context exists so the three range views need nothing from their parent but this;
adding the writes here keeps **one** mutation, **one** note dialog and **one**
snackbar on the page rather than one per view.

### `dashboard/DashboardPage.tsx`

Owns the mutation, the `CompletionNoteDialog` (keyed on the item), and the
snackbar. `complete` also drops `?item=` - the popup's subject is about to stop
existing, so leaving it open would leave a dialog describing nothing until the
refetch lands and `OpenItem` finds nothing to render.

### `dashboard/MyDayView.tsx` - the Day row

The row becomes a `ButtonBase` around the description with a `CompleteControl`
as its **sibling**, not a `ButtonBase` containing one. A button inside a button
is invalid nesting and a screen reader announces neither; `items/ItemRow` has
the same shape for the same reason. The disclosure label is unchanged
(`"<description>, on <list>"`), so the popup's existing tests still name it.

An **event row gains nothing** (R63): it keeps its single whole-row button and
its dashed border. The two have to read differently or somebody will try to tick
a dentist appointment off.

### `dashboard/DashboardItemDialog.tsx`

Two actions added beside `Close` and `Open <list>`: `Complete` and the
note-taking variant, via the same `CompleteControl`. This is what makes R85 true
for the week view, the month view and the undated panel at once, rather than
three times.

## Testing

### Unit - `DashboardPage.test.tsx`

- A Day row's control completes the right item with the version the payload
  carried, and the row goes.
- The note route sends the note HTML with the completion, in one request.
- The popup completes and then closes.
- A 409 raises the snackbar carrying the server's prose, and the row comes back.
- An event row still offers nothing to complete - the existing test, which has
  to keep passing now that something on the same screen does.

### E2E - `e2e/tests/test_dashboard.py`

- A member adds a chore due today, opens the agenda, completes it **there**, and
  finds it struck through with its points on the list - the assertion that the
  write is the same write, which no unit test can make.
- Two browser contexts, one chore: the second member is told who got there
  first. The suite's reason to exist, pointed at the new surface.

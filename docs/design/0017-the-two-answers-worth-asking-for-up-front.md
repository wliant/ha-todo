# 0017 - The two answers worth asking for up front

Design for [requirements/0017](../requirements/0017-the-two-answers-worth-asking-for-up-front.md)
(R148-R152). Frontend only, and no new state anywhere: the form's one boolean
stays, the due control's one piece of local state stays, and nothing is added
beside either.

Decisions this implements rather than argues:
[adr/0050](../adr/0050-the-collapsed-form-asks-for-what-costs-a-tap.md).

## Scope

Three components and their tests. No API change, no DTO change, no migration -
`SaveItem` already carries `assigneeId` and `dueAt`, and R152 is the requirement
that says so.

1. **`DueAtField`** gains a `compact` mode: the chip row alone.
2. **`AssigneeField`** is new, and replaces the `select` in `ItemForm`.
3. **`ItemForm`** changes which fields it shows collapsed, and when it offers
   the disclosure.

## What is already there

| Piece | Where | Why it is enough |
|---|---|---|
| One-tap dates, with the chips as their own readout | `items/DueAtField` (0012 R94) | R149 is a render mode of a control that already works this way |
| The household's zone applied in exactly one place | `instantOf` in `DueAtField` | R152's single source of truth needs no second thought |
| A member's face, already decorative | `app/MemberAvatar` (`aria-hidden`) | A chip can carry one without entering its accessible name |
| The collapsed/expanded boolean | `ItemsSection`'s `showAllFields` | R148 narrows what it hides; it does not need a third state |
| Per-list fields, including who may be assigned | `ListField`, `list.members` (R24) | The chip row is drawn from the same array the dropdown was |

## Step 1 - `DueAtField` gains a compact mode

One new prop, `compact`, defaulting to false. When set, the component renders
its existing chip row and returns before the date/time row. The chips, the
emit path, `instantOf`, `DEFAULT_TIME` and the zone handling are all untouched -
this is a render decision, not a second implementation.

Two details matter:

- **`pendingTime` stays.** In compact mode nothing can change it, so it is
  simply `DEFAULT_TIME` - which is exactly what R149's "one tap means end of
  day" needs, and is what the chips already did.
- **A date that is neither today nor tomorrow is rendered as a chip showing
  the date**, non-interactive (no `onClick`, so it is not a button and offers
  nothing that does nothing). The collapsed form must never hold a value it
  does not show; without this, a member who expands, picks next Thursday and
  collapses sees an unselected Today/Tomorrow pair and a date they cannot see.
  The form cannot currently be collapsed again, so this is a guard against the
  next change rather than a path that exists today - which is why it is one
  chip and not a mechanism.

## Step 2 - `AssigneeField`

A new component in `items/`, because `ItemForm` is already long and this is the
piece most likely to be wanted elsewhere (the recurrence form is the obvious
candidate, deliberately not changed here - see R151's exclusion list).

```tsx
<AssigneeField
  members={members}
  required={assignee.required}
  value={value.assigneeId}
  onChange={(assigneeId) => onChange({ ...value, assigneeId })}
/>
```

- A `FormLabel` reading "Assignee", with `required` passed through, exactly as
  `DueAtField` labels its chips. A legend labels no control, which is why the
  tests key off the chips rather than the label.
- One `Chip` per member, filled and `primary` when selected, outlined
  otherwise - the same selected/unselected vocabulary the date chips use, so
  the two rows read as one idea.
- `aria-pressed` on every chip, because a row of toggles whose state lives only
  in colour is unreadable to anything that is not looking at it. The accessible
  name stays the member's name alone, so `getByRole('button', { name: 'Ana' })`
  works and the avatar stays out of it.
- A **Nobody** chip first, only when the field is optional. It mirrors the
  dropdown's "Nobody" option, and - more usefully - it makes *unassigned* a
  visible selection rather than the absence of one. Tapping a selected member
  chip deliberately does nothing: one obvious way to unassign is better than
  two, and a toggle-off gesture on a chip that looks identical to the others is
  not discoverable anyway.

`MenuItem` leaves `ItemForm`'s imports with the select; the cost fields still
use it, so the import stays.

## Step 3 - `ItemForm` decides what collapsed means

`asked` currently answers "declared, and either expanded or required". R148
makes the assignee and the due date exceptions:

```tsx
/** Shown while collapsed only if the list insists on an answer... */
const asked = (field: ListField | undefined) =>
  Boolean(field) && (expanded || field!.required)

/** ...except these two, which cost a tap and which everything else reads. */
const alwaysAsked = (field: ListField | undefined) => Boolean(field)
```

The disclosure's condition changes with it, and this is the part easy to get
wrong. `hidden` counts declared-but-not-required fields, which used to be the
same question as "is there anything behind the toggle". It is not any more:

- the assignee is shown collapsed, so it must stop counting - otherwise a list
  declaring only a description and an optional assignee offers "More fields"
  that reveals nothing;
- the due date is shown collapsed **but only as chips**, so it must *start*
  counting whenever it is declared - its picker, time box and steppers are
  genuinely behind the toggle (R151), including when the field is required.

So the count becomes "fields with something still to show": the non-required
detail and costs as before, plus the due date whenever it exists, and never the
assignee.

## Testing

`ItemsSection.test.tsx` is where the collapsed/expanded rules are already
asserted, and three of its tests assert the old rule by name or by
`combobox`:

- "starts the add form with only what the list insists on" - **replaced**. The
  new one asserts that a declared-but-optional assignee and due date are both
  on screen with the form collapsed, while an optional cost is not, and that
  the date picker arrives only on expanding.
- "asks only for the fields the list declared" - keeps its point (a field never
  declared is never rendered) and loses its `More fields` click, which is now
  unnecessary for the assignee.
- "offers only the people who can see this list as assignees" and "opens the
  whole form when editing" - re-keyed from the `combobox` to the chips.

`DueAtField.test.tsx` gains: compact renders the chips and no date input; a
chip still emits end-of-day in the household's zone from compact mode; and a
date that is neither today nor tomorrow is shown rather than silently held.

A new `AssigneeField.test.tsx` covers the chip row, `aria-pressed`, Nobody's
presence when optional and absence when required, and that selecting emits the
member's id.

E2E: `test_items.py` drives the assignee dropdown in two places
(`combobox`/`option`) and must move to the chips. One of them exists precisely
to prove R24 in the UI, so it keeps that purpose and changes only its selector.

## What this deliberately does not do

- **No API, DTO or schema change.** R152.
- **No third form mode**, and no new state: `compact` is a prop, not a
  remembered choice.
- **No change to the recurrence form.** Its assignee stays a `select`; a
  standing instruction is written once and read for months.
- **No reordering or ranking of members.** The list's own order, as handed over.
- **No change to what the chips produce.** End of day in `app.timezone`, via
  `instantOf`, exactly as 0012 left it.

# 0027 - Complete is a button, and the checkbox selects

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R38 (rows that open), R46 (selection), R48 (completing is deliberate)
Amends: [0014](0014-mui-as-the-design-system.md) - one named palette entry is added
for a semantic state

## Context

The item row has one control today: a `Checkbox` whose accessible name is `Complete
<description>`, which completes the item. R46 needs a checkbox that *selects*, and
R48 wants completion to carry an optional note reachable by holding the control for
a second. Those cannot be the same control, and there are only so many things a row
on a phone can hold.

R48's press-and-hold is the part that needs care. A hold is a duration-based pointer
gesture, and it has **no keyboard equivalent**: holding Enter on a `<button>` fires
repeated clicks in every major browser rather than one long press, so there is
nothing to detect. A route to the note that exists only as a hold fails WCAG 2.1 SC
2.5.1 (Pointer Gestures, which requires a single-pointer alternative to any
path- or duration-based gesture) and arguably 2.1.1 (Keyboard). Neither an
`aria-describedby` hint - which tells but does not enable - nor an invented chord
like Shift+Enter, invisible to everyone, fixes that.

R47 then wants a claimed badge in an accent colour. The theme defines `primary`,
`error`, `background`, `text` and `divider` and nothing else: there is no
`secondary` and no accent to reach for.

And `ItemsSection` holds no per-row state today; `ItemRow` is stateless and pure.

## Decision

**The checkbox selects, and is named `Select <description>`.** Completion moves to
its own control.

**Completion is a split button.** A `Complete` button that completes on an ordinary
click, plus a small `IconButton` named `Complete <description> with a note` that
opens the note dialog on an ordinary click. **The hold is an accelerator to the same
dialog, and nothing depends on it.** The visible note button *is* SC 2.5.1's
single-pointer alternative; it is tabbable, announced, mouse-reachable and
assertable, and no `role` is lied about and no `aria-keyshortcuts` is needed.

**The gesture, where it exists, is defensive.** `onPointerDown` arms a 1000ms timer;
`onPointerUp`, `onPointerLeave` and `onPointerCancel` clear it; a `pointermove`
beyond about 10px clears it too, or starting a scroll on the button completes an
item. `onContextMenu` is prevented and `touch-action`, `user-select` and
`-webkit-touch-callout` are set, because **on iOS a long press on a button raises
the selection callout**, which would land on top of the dialog that just opened.

**Expansion state is one id in `ItemsSection`.** `expandedId: string | null`, so
R38's "at most one row open" is true by construction rather than by cleanup, and
`ItemRow` stays stateless, receiving `expanded` and `onToggleExpand`. `adr/0015`'s
rule is narrowed, not broken: it governs *layout* state and `useMediaQuery`, both of
which stay in `AppShell`. The description becomes a `ButtonBase` with
`aria-expanded` and `aria-controls`, and the checkbox and action buttons are
siblings **outside** it - a row-wide button containing a checkbox is invalid nesting
and a screen reader reads it as neither.

**A named semantic palette entry, `selfClaimed`**, in both colour schemes via module
augmentation. Not `secondary`, MUI's generic second brand colour, which unrelated
things will reach for within a release; and not `primary`, which `ItemRow` already
uses for the completed chip, so reusing it would give one colour two meanings - the
drift `adr/0014` exists to prevent.

**Colour is never the only signal.** The claimed chip carries a person-check icon
and an accessible name of the form `<name> claimed this`, so a colour-blind member,
a screen reader and a Playwright role query all read the same fact. Colour alone
would fail WCAG 1.4.1 and be unassertable.

## Consequences

- The common case stays one tap, the richer case needs no detour, and the note is
  reachable without a pointer. That is all three of R48's clauses.
- **Existing tests break, and the naming is load-bearing.** Four assertions in
  `e2e/tests/test_items.py` and six in `ItemsSection.test.tsx` locate the completion
  control as `role=checkbox, name=Complete …`. They must change in the same phase,
  and the new selection checkbox must be named `Select …`: had it kept "Complete" in
  its name, every one of those queries would **keep matching** and the suite would
  stay green while asserting that ticking the selection box completes the item. A
  passing test that tests nothing is worse than a failing one, so the rename is a
  correctness requirement rather than a tidiness one.
- Because `e2e/` runs against **dev**, that phase's test changes must land with the
  deploy rather than after it, and nothing else may ride along.
- The row holds more controls - a checkbox, a disclosure, Complete, the note button,
  edit, and Reopen when completed. On a narrow phone that is tight, and the mitigation
  is that Reopen lives inside the expanded body rather than on the collapsed row.
- `dangerouslySetInnerHTML` and the note's images leave the collapsed render path
  entirely, so a long list draws no pictures - a real performance win, and a test
  cost: the detail assertion now needs an expand click first.
- The note dialog must mount a **fresh** `DetailEditor` per item, keyed on the item
  id. `DetailEditor`'s `value` is initial-only and never re-synced, so without that
  key an editor opened on a second item shows the first item's note - one member's
  sentence on another item.
- The hold is undocumented in the UI. Members who never discover it lose nothing,
  which is the point of it being an accelerator, but it also means the feature R48
  describes most vividly is the one least likely to be found.
- One more theme entry exists that only one component uses. `adr/0014`'s rule holds -
  no global class, no second token system - but the palette is no longer only brand
  colours.

## Alternatives considered

- **Hold-only, with a hint.** Exactly what R48 describes, and one control instead of
  two. Rejected: it fails SC 2.5.1 with no alternative, and a hint that says "hold
  for a note" informs a keyboard user of something they still cannot do.
- **A long-press-free design: always open the dialog.** One control, fully
  accessible, no timers. Rejected because it makes the common case - tick the bins
  off - a two-step modal, which is the friction R39 and R48 both exist to remove.
- **Keep the checkbox for completion and add a separate selection affordance** (a
  long-press on the row, or an explicit "Select" mode). No test changes. Rejected
  because a selection mode is a second piece of row state and a mode a member has to
  discover and leave, and because a checkbox that means "done" while another gesture
  means "chosen" is the ambiguity R48 is removing.
- **`secondary` in the palette for the claimed badge.** One line, conventional.
  Rejected because `secondary` is a generic slot that the next component will adopt
  for something unrelated, at which point one colour means two things again.
- **A bare `sx` colour literal on the chip.** No theme change at all. Rejected by
  `adr/0014`: the theme is the single source of truth for colour, and a literal does
  not survive the dark scheme.
- **Per-row expansion state inside `ItemRow`.** The component owns its own
  disclosure, which is the ordinary React shape. Rejected because "only one open at
  a time" then needs rows to coordinate - a context, or a callback that closes
  siblings - and one id in the parent makes the invariant structural.

# 0023 - The completion note is part of the completion, not a field

Status: accepted
Date: 2026-11-05
Implements: [requirements/0005](../requirements/0005-completion-detail-recurrence-and-planning-views.md)
R37 (a member records how it went), R48 (one gesture, one request)
Builds on: [0018](0018-detail-html-is-sanitized-on-write.md)

## Context

R37 adds a second body of rich text to an item: a sentence and often a photograph
about how the work actually went, written at the moment it is finished. The product
already has one such field - `detail_html`, declared per list as the `DETAIL` kind
(R21) - with a sanitizer, an image upload path and a claim mechanism behind it
(`adr/0018`, `adr/0013`).

Three things need deciding, and the first will be guessed wrong by anybody reading
`FieldKind` looking for where the note lives.

Whether the note is a configurable field. `FieldKind` is the closed set of things a
list declares, and a reader's instinct is that a second rich-text body belongs
there as `COMPLETION_NOTE`.

Where the note is written. The item already has `PATCH /api/items/{id}` and `PUT
/api/items/{id}/completion`, and the note could plausibly ride either, or take an
endpoint of its own.

How the note's pictures are owned. `claimDetailImages` scans exactly one column and
is called from exactly two places, neither of which is the completion path.

## Decision

**The note is a column, not a field kind.** `todo_item.completion_note_html`,
alongside `detail_html`. Making it a `FieldKind` would mean widening
`ck_list_field_kind`, `isSingleton()`, `ListFieldService.describe`'s exhaustive
switch, the field configuration editor and a migration - all to make configurable
something no list wants to switch off. The note is a property of *the act of
completing*, which every list supports identically, not of an item's shape. Stated
here because `FieldKind` is where the next reader will look for it.

**The note joins the completion columns, and the database says so.**
`ck_todo_item_completion` is extended with `completed_at IS NOT NULL OR
completion_note_html IS NULL`, so a note on an item nobody completed is
unrepresentable - a sentence about work that was never done.

**It is written by the completion request.** `CompletionRequest` becomes `(Long
version, Boolean completed, String noteHtml)`. A separate endpoint would mean two
writes, two versions and a window in which the item is complete with no note; and
R48's press-and-hold produces a single intent, so it should produce a single
request. Reopening *with* a note is a **400** rather than a silent drop: the CHECK
would otherwise turn it into a 500, and a quietly discarded field hides a client bug
for a release.

**Sanitization is unchanged.** The note goes through the same `HtmlSanitizer` and
the same policy as the detail - the same allowed elements, the same
`^/api/images/{uuid}$` restriction on `img src`, the same null-when-nothing-survives
- and the client sanitizes nothing, as `adr/0018` requires. One policy, one
implementation.

**`claimDetailImages` becomes `claimImages` and scans both columns**, and is called
from the completion path as well as from create and update. It scans the
concatenation of the two columns with the same pattern, because `stored_image` has
no per-field discriminator and does not need one: a picture belongs to the *item*.

## Consequences

- One sanitizer, one policy, one claim scan. A second rich-text body costs a column,
  a CHECK clause and one `||` in the scan - which is the payoff for `adr/0018`
  having put sanitization on the write path rather than in a renderer.
- `stored_image` keys claims on `item_id` alone, so **"clear the note but keep the
  detail's pictures" is not expressible.** Both bodies' pictures live and die with
  the item. Acceptable today; the day it is not, the fix is a discriminator column
  and a per-field diff at write time, not a second claim table.
- If the scan is *not* extended, four bugs follow in this order, and the first is the
  one a household reports: the note's images keep `item_id IS NULL`, so
  `ItemImageAccess` is never consulted and **every member but the uploader sees a
  broken picture**; `forgetPicturesOf` never reaches the bytes, so deleting the item
  leaks them; deleting the list leaks them too, since `findForList` joins through
  `item_id`; and they stay readable by their uploader after that member loses access
  to the list. Written out because three of the four are silent.
- Nothing unclaims a picture removed from HTML - already true of the detail, now true
  of the note. Under R49 a reopened item keeps its note, so a reopen orphans nothing.
- The note is unconfigurable. A list that would rather not have one cannot turn it
  off, and the only lever is not writing one.
- `ItemResponse` carries a second `dangerouslySetInnerHTML` payload on every item
  fetch. R38's collapsed row does not render either body, which keeps the cost off
  the long-list render path, but the bytes are still on the wire.

## Alternatives considered

- **A `COMPLETION_NOTE` field kind.** Consistent with `adr/0011`, and a list could
  switch it off. Rejected: it widens four closed things and a migration to configure
  something nobody wants configured, and it misfiles the note as a property of an
  item's shape rather than of the act of completing it.
- **A separate `item_completion` table holding the note, the time and the member.**
  The cleanest long-term shape, and it would survive a reopen as history rather than
  as a nulled column. Rejected for this change as a much larger one: it moves the
  leaderboard's `SUM(points_earned)` off `todo_item`, contradicting `adr/0012`'s
  derived-not-counted prose, and either duplicates the completion columns or migrates
  every reader of them. Named here as the designed-for successor if per-item
  completion *history* is ever wanted.
- **`PATCH /api/items/{id}` carries the note.** No new request shape at all. Rejected
  because the note would then be settable on an open item, which the CHECK forbids,
  so the 400 would move from a deliberate branch to a constraint violation.
- **A dedicated `PUT /api/items/{id}/completion/note`.** Lets a member add a note
  after completing. Rejected because R37 is explicit that the note records a moment,
  the second write reintroduces a version and a 409 on a path the member already won,
  and it creates a state - complete, note pending - that the UI would have to
  represent.
- **Sanitize the note on read instead.** Rejected by `adr/0018` already; repeating it
  here only because a second field is exactly when somebody re-proposes it.

# 0061 - The detail field is deliberately not round-tripped

Status: accepted
Date: 2026-11-20

## Context

An item carries a **detail**: rich text a member wrote on a real screen, with
formatting and embedded pictures. It is sanitized server-side on write and its
`<img src>` is restricted to our own `/api/images/{uuid}` paths
([0018](0018-detail-html-is-sanitized-on-write.md),
[0032](0032-a-detail-pictures-owner-is-a-third-foreign-key.md)), and it is
edited through a ProseMirror editor the SPA loads lazily because it is heavy.

Home Assistant's to-do item carries a `description`: a single **plain-text**
field, offered to the member in the to-do card and settable through
`todo.update_item` under the `SET_DESCRIPTION_ON_ITEM` feature flag.

The two look like the same field. Mapping one onto the other is the obvious
thing to do, and there is a real pull towards it - a member looking at a chore
in Home Assistant would reasonably like to see the note attached to it.

The hazard is in how Home Assistant writes. `async_update_todo_item` hands the
integration a **whole item**, not a patch. An automation or a member changing
the due date submits the description along with it, whatever the integration
last reported it to be.

## Decision

The detail is **not exposed and not writable**. `SET_DESCRIPTION_ON_ITEM` is not
declared, `detailHtml` is never read into a Home Assistant item, and no path in
the integration ever sends `detailHtml` - not on create, not on update.

A member creating an item from Home Assistant creates one with a description
and perhaps a due date. The detail stays something the list's own screens own.

## Consequences

- **Nothing the integration does can destroy a member's writing.** That is the
  whole decision. The alternative's failure is silent, unattributable and
  permanent: read the HTML back as text, let an automation touch a due date, and
  a member's formatted note with two photographs in it is replaced by flattened
  text. No exception, no 400, no failing test, and the only person who finds out
  is the one who wrote it, later.
- **The pictures would have gone too.** An `<img src="/api/images/...">`
  stripped to plain text leaves nothing to restore it from, and the stored
  image is then claimed by an item whose detail no longer refers to it.
- **A list that marks `DETAIL` required cannot have items created on it from
  Home Assistant.** The server refuses with "This list needs some detail on
  every item." and
  [requirements/0022](../requirements/0022-home-assistant-acts-as-a-household-member.md)
  R193 is that the refusal is surfaced in those words. The entity still exists
  and completion still works.
- **A member reading a chore in Home Assistant sees less than the product
  knows.** They see the description and the due date, and that is all. Accepted:
  Home Assistant is where the household's automation reaches in, not a second
  place to read a list.
- **The safe half was not kept either.** Exposing the detail as a *read-only*
  plain-text rendering would be harmless on its own, and is deliberately not
  done - because the field would then exist, and the next person to wonder why
  it is read-only has a one-line change available that makes it writable and
  looks like a fix. The open question is recorded in `requirements/0022`; the
  field is absent so that the change cannot be one line.

## Alternatives considered

- **Map `detailHtml` to `description`, both directions.** The obvious
  implementation and the one this ADR exists to refuse, for the reasons above.
- **Expose it read-only, by stripping tags.** Safe in itself, and rejected for
  the "safe half" reason above: it builds the thing that invites the unsafe
  change, and `0059`'s platform has no concept of a read-only field, so the
  only thing stopping a write would be our own flag.
- **Convert the plain text to HTML on write, escaped into a paragraph.** Would
  let an automation attach a note, and the server's sanitizer would accept it.
  Rejected because it does not fix the round trip at all: the next update still
  overwrites whatever a member had written there, now with a paragraph.
- **Accept a description on create only, never on update.** Tempting, and
  genuinely safe - a create has nothing to overwrite. Rejected because
  `SET_DESCRIPTION_ON_ITEM` is one flag covering both, so Home Assistant would offer
  the field in the edit card and the integration would silently discard what
  was typed into it. A field that accepts input and drops it is worse than no
  field.

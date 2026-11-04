# 0020 - A shared link becomes an item on a chosen list

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md)
R31
Amends: [0008](0008-share-target-for-links.md) - the GET share target and the
parameter normalisation stand; parking does not.

## Context

adr/0008 built the share path end to end and stopped one step short, because items
did not exist: a shared link is normalised and then **parked** in `localStorage`
per user, shown on `/lists` as waiting to be filed. That ADR said what should
happen next - "when R1-R5 land, the `park()` call becomes the create-item mutation
and that module is deleted" - and requirements/0003 left the remaining question
open: "Where a shared link should land once lists exist: always ask which list, or
default to a designated 'Inbox' list?"

Items now exist, so the question is live. It is a real fork, because the whole point
of a share target is that sharing never asks the member to retype or paste anything
(0003 R14), and a list picker is, strictly, asking them to do something.

## Decision

**The share screen asks which list, with the member's most recently interacted list
preselected.** One tap confirms; changing the destination is a tap on a different
row. Pinned and recent lists are offered first, drawn from the same
`GET /api/lists/sidebar` query the shell already makes.

**No "Inbox" list is created.** The destination is always a list the member made.

The captured link becomes a real item: `captureSharedLink()`'s normalisation is
unchanged - the URL comes from `url`, or is recovered from `text` where Chrome
usually puts it, with the remainder kept as a note - and the result populates the
new item's description, with the note appended. If the destination list declares a
detail field, the note goes there instead.

**`web/src/share/parkedLinks.ts` and its test are deleted**, and `/lists` loses its
"Shared links waiting" panel, exactly as adr/0008 specified. A link is no longer
held on the device, so requirements/0003's second open question - whether a parked
link survives reinstalling the app - is closed rather than answered: nothing is
parked.

The share route stays behind `ProtectedRoute`, so a share arriving while signed out
still resumes after sign-in via the `state.from` mechanism already in place. R14
remains Android-only; that is a platform limit recorded in 0008 and unchanged here.

## Consequences

- Sharing costs one tap in the common case, because the preselected list is almost
  always the one the member means - they were just looking at it. It is not zero
  taps, which is the honest cost of not inventing a destination.
- The member chooses knowingly, so a link cannot quietly accumulate somewhere they
  never look. That is the failure mode an Inbox has.
- A member with no lists yet reaches the share screen with nothing to pick. The
  screen offers list creation inline rather than refusing the share, and the link
  survives into the newly created list.
- Nothing is stored on the device any more, so a share is either an item or an
  explicit failure the member sees. No silent loss, which was 0008's own standard.
- Recency now has a second consumer beyond the sidebar, which makes the
  `POST /touch` stamping load-bearing for a user-visible default rather than only
  for ordering a list. Worth knowing if the throttle window is ever tuned.
- The item is created with the destination list's required fields unfilled, so a
  list whose assignee or cost field is required will reject the quick create. The
  share screen therefore shows the full item form when the chosen list has required
  fields beyond description, and the one-tap path applies only when it does not.
  This is the detail most likely to be missed in implementation.

## Alternatives considered

- **A designated "Inbox" list per member**, auto-created, that shared links land in
  silently. Genuinely zero taps, and the fastest capture. Rejected: it invents a
  list nobody asked for, splits a member's items across "where I put things" and
  "where things land", and turns filing into a second chore that happens later or
  never. requirements/0004 explicitly does not want a list the product created.
- **Always ask, with nothing preselected.** Safest and most explicit. Rejected as
  slower for no gain - the member still has to choose, and the most recent list is
  right often enough that preselecting it is a better default than none.
- **Remember the last list a share went to**, rather than the last list touched.
  Plausibly a better predictor, and it needs its own stored field. Rejected for now
  as a refinement with no evidence behind it; recency is already tracked and is a
  good enough proxy. Revisit if sharing turns out to concentrate on one list.
- **Keep parking as a fallback** when the member dismisses the picker. Keeps the
  "no link is ever dropped" promise with belt and braces. Rejected because it keeps
  `parkedLinks.ts` alive forever as a second home for items, which is the thing
  0008 wanted gone; a dismissed share is a decision, not a loss.

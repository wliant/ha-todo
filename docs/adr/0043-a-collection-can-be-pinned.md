# 0043 - A collection can be pinned, in a table of its own

Status: accepted
Date: 2026-11-08

## Context

0012 first replaced the sidebar's Pinned section with pins sorting first inside
one ordered group, and accepted in R101 that collections would have no pin at
all. Both halves of that were wrong, and the household said so:

- **A pinned thing is a section, not a sort order.** A member pins the handful
  of things they are working from this week. Folded into an ordering, they are
  indistinguishable from the things that merely happen to be used a lot, and
  they compete with the group's cap: pin twelve lists with a preference of ten
  and two of them vanish, which is the sidebar overruling an explicit
  instruction.
- **Collections are pinned for the same reasons lists are.** A recipe
  collection somebody is cooking from this week is exactly the case pinning
  exists for. The asymmetry was never a product judgement; it fell out of
  `adr/0029` keeping data lists in their own aggregate, and `list_user_state`'s
  `list_id` being a foreign key to `todo_list`.

`adr/0029` is still right, and loosening that foreign key is the one thing not
on the table.

## Decision

**Collections get pins, in `data_list_user_state`** - their own table, keyed
`(data_list_id, user_id)` with a nullable `pinned_at`. `pinned_at IS NULL` *is*
"not pinned", as on `list_user_state`: no companion boolean to disagree with it.

Deliberately **thinner** than `list_user_state`: no `last_interacted_at` twin. A
collection's recency comes from `data_list_interaction_day` (`adr/0041`), and a
second column holding it is a second thing to keep true.

**The sidebar has a Pinned section again**, holding both kinds together, and the
Lists and Collections groups hold everything *not* pinned. The two partition
what the member can see, so nothing is drawn twice. The pinned section is
**uncapped and scrolls**; the groups stay capped at the member's preference.

The merged section is ordered **by name**, not by when each thing was pinned.
The two halves arrive from two separate requests (`adr/0042`) and there is no
shared clock to interleave them on. Exposing a pin timestamp on
`ListSummaryResponse` and `DataListSummaryResponse` - DTOs used by the index
pages, the share picker and the dashboard - to answer a question nobody asked is
the worse trade, and a hand-picked set of a dozen things is scanned by name
anyway.

## Consequences

**This overrides three statements in accepted documents**, which are left as
written because they record what was decided when it was decided:

- `adr/0029`'s consequence that a collection has no per-member state. It now has
  exactly one row's worth, in its own table - which is the *form* `0029`
  prescribes, not a departure from it.
- `adr/0041`'s "a collection has no pin and no recency row". It has a pin. It
  still has no recency row.
- `0012` R101, "pinned lists come first … collections have no pins", replaced by
  R104-R106 in the same document.

Other consequences:

- `SidebarResponse` and `DataListSidebarResponse` each carry two arrays. One
  request per aggregate, not one per section: a split that could disagree while
  one half was in flight would show a list in both sections or in neither.
- `PinButton` now takes an `onToggle` instead of reaching for `usePinList`.
  Picking between two mutation hooks inside the button would mean calling both
  on every render to keep hook order stable, and the caller already knows which
  kind it is drawing.
- The optimistic update flips the star but **does not move the row**. Where the
  row belongs afterwards depends on an ordering only the server can compute -
  name order above, frequency order below - so the star responds under the
  finger and `onSettled`'s refetch relocates the row. A member who pins
  something sees it starred instantly and jump a moment later.
- The pinned section's height cap (`15rem`) is the one number holding the menu
  below it on screen. It is a height rather than a row count because what
  matters is how much of the drawer the section may eat.
- Pinning checks **readable**, not **managed**, exactly as a list's pin does: a
  pin is the caller's own note about something they can see, and pinning
  something shared with you changes nothing for whoever shared it.

## Alternatives considered

- **Add `pinned_at` to `data_list_interaction_day`.** One fewer table. It would
  put an explicit, permanent instruction inside rows that are *pruned on a
  rolling thirty-day window* - the pin would silently expire. Rejected outright.
- **Loosen `list_user_state.list_id`** to a nullable pair of owner keys, or drop
  the FK. One table for both kinds, and it reopens exactly what `adr/0029`
  closed: every existing reader of `list_user_state` would have to remember a
  kind it has never heard of.
- **Keep pins as a sort order and leave collections out**, i.e. what 0012
  shipped. Fails both halves of what was actually asked for.
- **Interleave the merged section by pin time**, adding `pinnedAt` to both
  summary DTOs. More faithful to "most recently pinned first", at the cost of a
  per-member timestamp on the DTOs the whole app reads. Reconsider if the
  section ever needs "recently pinned" as an affordance of its own.

# 0045 - The filter and sort live in the address, and parse defensively

Status: accepted
Date: 2026-11-11

## Context

`requirements/0013` R115 wants a filtered list to survive a reload, a navigation
away and back, and being sent to somebody else. Three places could hold it:
component state, the URL, or a per-member row on the server beside the theme and
`sidebar_list_count`.

`adr/0037` already decided this shape once, for the agenda's period: it is in
the address as `?on=YYYY-MM-DD`, omitted when it is today. `adr/0015` and its two
narrowings (`adr/0042`) say the same thing from the other direction - the app
keeps almost nothing in React state, and what it does keep is about something
the member did to the chrome rather than about what they are looking at.

The list route is already bound to the pathname rather than to state: which tab
is showing is derived from `useLocation().pathname`, deliberately, so the back
button works and there is nothing to keep in sync.

## Decision

**The filter and the sort are query parameters on the list route, and the
default view writes nothing.**

```
?status=done            status, comma-separated; omitted when it is the default `open`
&who=<uuid>,none        assignee ids, with `none` for unassigned
&due=overdue            one of overdue|today|week|none, or `YYYY-MM-DD:YYYY-MM-DD`
&cost.<fieldId>=<choiceId>,none     a FIXED cost: choice ids, `none` for unanswered
&cost.<fieldId>=2:8,none            a NUMERIC cost: min:max, either side blank
&sort=due&dir=desc      sort key, and direction when it is not the key's natural one
```

- **Field and choice ids, not positions.** A position is reordered by the list's
  owner and a pasted link would then filter on a different cost; an id is the
  identity the item's answer is stored against (`adr/0012`).
- **Parsing never throws and never blocks.** An unknown key, a malformed range,
  an id the list no longer declares, a date that is not a date: each one is
  dropped and the rest of the filter stands. The worst case is the unfiltered
  list, which is R116 - this route is reachable from the sidebar, a share and a
  bookmark, and a mangled address must not be a member who cannot get in.
- **Writing is `replace`, not `push`.** Tapping four filter chips must not put
  four entries in the history; leaving the list is one back press.
- **Round-tripping is tested as a property**: serialising a parsed filter
  returns the same parameters, and the default filter serialises to nothing.

## Consequences

- **A filtered list is a link.** "Here is what is still unassigned on the
  grocery list" needs no feature of its own, which is why saved views are out of
  scope.
- **Nothing new is stateful.** `ItemsSection` reads `useSearchParams`; there is
  no filter state to disagree with the address, and the back button is correct
  by construction.
- **The URL is long and ugly when several costs are filtered**, because the ids
  are UUIDs. Accepted: it is read by the app and pasted by a member, never
  typed, and the alternative trades a cosmetic cost for a correctness one.
- **A link outlives the thing it filters on.** A cost deleted from the list, a
  member whose grant was revoked: the parameter names an id nothing matches.
  Dropped silently rather than reported, and the chip simply is not offered -
  the member sees the list, which is the right failure.
- **The filter is not remembered between visits**, deliberately. Opening a list
  fresh shows the list.
- **`?on=` and these are now two independent conventions** for the same idea on
  two surfaces. The shared rule, which both state, is: defaults are absent from
  the address, and parsing falls back rather than failing.

## Alternatives considered

**Component state.** Simplest, and loses the filter on every navigation - which
on a phone includes opening the settings screen and coming back. No link, no
reload survival.

**A per-member stored filter**, like the theme and the sidebar size. More work -
a table or a column per filterable thing, an endpoint, an invalidation - and a
worse product: a filter that persists silently across sessions is a list that is
missing items for reasons the member has forgotten. The agenda considered and
rejected the same thing for its period.

**`localStorage`.** All of the stored version's confusion, per device, plus a
divergence between two devices nobody can inspect. It would also be the first
thing in the app to keep server state's shape outside the query cache.

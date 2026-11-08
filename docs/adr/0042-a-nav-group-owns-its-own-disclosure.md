# 0042 - A nav group owns its own disclosure, and the sidebar asks twice

Status: accepted
Date: 2026-11-08

## Context

0012 R99 makes Lists and Collections expandable groups in the sidebar. Two rules
stand in the way, and both were written deliberately.

`adr/0015` narrowed design/0003's CSS-only rule to let `AppShell` own the
drawer's open state, and closed with: **nothing else may acquire layout state,
and everything else responsive stays CSS.** An expandable group is open or
closed, which no stylesheet can express from the outside.

`adr/0029` keeps data lists in their own aggregate, package and controller,
precisely so that no reader of todo lists has to remember they exist. The
existing sidebar is one request - `GET /api/lists/sidebar` - whose docstring
says both sections come together so they "cannot disagree while one is in
flight". Adding collections to it would put a `DataList` read inside
`ListController`.

## Decision

**Disclosure is not placement.** `SideNav` owns which groups are open, in its
own `useState`, defaulting to both open.

The distinction `adr/0015` drew is between *where a thing sits* - which belongs
to the stylesheet and the breakpoint - and *what a member did* - which is state.
A drawer the member opened is the latter. A group the member collapsed is the
same kind of fact, held by the component the member clicked. The rule stands as
narrowed: no component reads the viewport, nobody else calls `useMediaQuery`,
and nothing here is responsive. It is a disclosure widget, and the alternative
is lifting it into `AppShell`, which would make the shell know what sections the
navigation has.

The default is **open**, because these groups replace the Pinned and Recent
sections wholesale: a member whose sidebar starts collapsed has strictly less
than they had before the change.

The state is **not persisted**. `SideNav` mounts once inside `AppShell` and the
mobile drawer is `keepMounted`, so a collapse survives every navigation and
every drawer open within a session - which is the whole span over which anybody
would notice. A `localStorage` key would be a second source of truth for a
preference nobody would miss, and `adr/0008` already removed one of those.

**The sidebar asks twice**: `GET /api/lists/sidebar` and
`GET /api/data-lists/sidebar`, each from its own controller. The two groups are
independent and nothing is drawn from both, so there is no pair of values that
can disagree - which is what the one-request argument was actually protecting.
Crossing the aggregates to save a round trip would buy consistency that is not
at stake.

## Consequences

- Two requests on shell mount instead of one, both small, both cached by
  TanStack Query under their own key hierarchy.
- The collapsed state is per session and per tab. A member who collapses
  Collections and reloads gets it back. Accepted: it is a glance, not a setting.
- `SidebarResponse` loses its `pinned`/`recent` split and becomes one ordered
  group (`adr/0041` orders it). Anything that reads the old shape breaks at
  compile time on both sides, which is the point of changing the record rather
  than adding a field.
- `adr/0015`'s rule now has an exception with a reason rather than a bare
  exception, so the next component that wants state has a test to meet: is this
  about placement, or about something the member did? Placement is still CSS.
- Collections acquire a `POST /api/data-lists/{id}/touch` - the first write on a
  collection that is not an edit. It records a visit, which is what
  `adr/0041` orders by.

## Alternatives considered

- **Derive the open group from the pathname**, as `AppShell` derives the
  drawer's open state. Elegant, precedented, and useless: the main use is
  jumping to a list *from the agenda*, where neither group would be open.
- **Both groups always open, no disclosure at all.** No new state, and it fails
  R99 and R102 together - a member with twenty lists and a preference of twenty
  gets a sidebar that is only the sidebar.
- **Lift the state into `AppShell`** to keep `adr/0015` literally intact. Makes
  the shell enumerate the navigation's sections, so adding a group would mean
  editing two files. The rule would be preserved and the design would be worse.
- **One `GET /api/sidebar` in a third package** reading both aggregates. Honest
  about being a cross-aggregate read, and it creates a third place that must
  remember collections exist - which is the cost `adr/0029` was avoiding.

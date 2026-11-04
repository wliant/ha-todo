# 0015 - A sidebar replaces the dual navigation bar

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R25
Amends: [0003's](../requirements/0003-pwa-and-ui-shell.md) R15 - navigation is still
one surface carrying identity and sign-out; its placement rules change.

## Context

`NavBar.tsx` renders two navigation surfaces at once - a top `header.appbar` and a
bottom `nav` - and CSS hides one at `48rem`. The component's own comment states the
rule: "Placement is CSS, not JavaScript: the same markup is a top bar from 48rem up
and a bottom bar below it, so nothing re-renders on resize and the two layouts
cannot drift apart."

That rule was sound for what it governed. The navigation was two stateless links,
so duplicating them cost nothing and the only difference between the layouts was
position.

R25 asks for something else: a sidebar with pinned lists, recent lists, a create
button, a menu, and the member's profile. That content is stateful - a selected
row, a variable number of list entries - and on a phone it cannot be permanently on
screen, so it has to open and close. "Opens and closes" is behaviour, not
placement, and the existing technique cannot express it.

## Decision

**One sidebar, one component tree.** A single MUI `Drawer` whose `variant` is
`permanent` from the breakpoint up and `temporary` below it, opened below the
breakpoint by a hamburger in a slim app bar.

**The no-JS-layout-state rule is narrowed, not abandoned.** The shell owns exactly
one piece of layout state, `mobileNavOpen: boolean`, and `useMediaQuery` is called
in exactly one place, to choose the drawer variant. Nothing else in the application
may acquire layout state; a later screen wanting a different layout per viewport
does it in CSS, as before.

**The bottom bar is deleted rather than kept alongside.** Three sections of lists do
not fit three tabs, so a bottom bar would have to carry a reduced version of the
sidebar - and two navigation surfaces with different content is precisely the drift
the original rule existed to prevent. Keeping the sidebar as the only surface keeps
one definition of what navigation contains.

Rendering both a permanent and a temporary drawer and hiding one in CSS - the
existing technique - is specifically rejected here: it would put two copies of a
focus-trappable navigation region in the accessibility tree, and duplicate the
state inside them. That is worse than the hook.

The temporary drawer closes on navigation, so it can never be left open behind a
route change.

## Consequences

- Something re-renders on resize now, at one breakpoint crossing, in one component.
  That is the admitted cost, and it is bounded by being the only place allowed to do
  it.
- `web/src/app/NavBar.tsx` and `AppLayout.tsx` are deleted and replaced by
  `AppShell.tsx` and `SideNav.tsx`. The comments in `NavBar.tsx` and at the top of
  `index.css` that state the old rule are removed with them, and CLAUDE.md's "one
  shell with one breakpoint" paragraph is rewritten in the same change.
- Phone users lose thumb-reach navigation: the hamburger is at the top of the
  screen, where a bottom bar used to be at the bottom. Accepted, because the
  sidebar's contents - pinned and recent lists - are reached more often than the
  two menu entries a bottom bar could hold, and a list opened from the drawer is
  one tap either way.
- `useMediaQuery` needs a `window.matchMedia` stub in the vitest setup, and the
  drawer's open/close behaviour below the breakpoint becomes something unit tests
  can and should assert - which the CSS-only approach made untestable.
- E2E gains a phone-viewport test for the drawer, since this is the one place the
  design accepts layout state.
- Nav links are no longer duplicated in the DOM, so tests can stop using
  `getAllByRole` to work around two copies.

## Alternatives considered

- **Keep the bottom bar on phones and add a drawer for the lists.** Preserves thumb
  reach and keeps R15's phone-first layout literally. Rejected: two navigation
  surfaces whose contents must be kept consistent by hand, which is the failure the
  original rule was written to avoid, and it is more to build.
- **A collapsible icon rail on desktop, drawer everywhere.** Maximises content
  width. Rejected as an extra click to reach lists on the screens that have room
  for a sidebar, for a gain nobody asked for.
- **Both drawers rendered, one hidden in CSS**, extending the current technique
  unchanged. Keeps the rule intact on paper. Rejected for the duplicated
  accessibility region and duplicated internal state described above - the rule
  would be satisfied in letter while causing the harm it exists to prevent.
- **A resize listener setting a breakpoint boolean by hand**, instead of
  `useMediaQuery`. Same state, more code, and it re-implements a hook the library
  already ships.

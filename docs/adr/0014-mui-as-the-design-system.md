# 0014 - MUI as the design system, replacing the CSS tokens

Status: accepted
Date: 2026-11-05
Implements: [requirements/0004](../requirements/0004-lists-items-and-personal-settings.md) R35
Amends: [0007](0007-installable-pwa-without-offline-caching.md) - the no-caching
service worker is unaffected; only the styling layer changes.

## Context

Styling today is one 518-line `web/src/index.css`: a `:root` block of hand-written
tokens, a `prefers-color-scheme` override, and a global class per component. That
was right for five screens with a handful of controls. R35 asks for Material
Design, and the feature it serves needs components the current approach does not
have - a navigation drawer, steppers, autocomplete with chips, dialogs, snackbars,
date inputs, switches, toggle groups, tabs. Hand-rolling those to Material spec,
accessibly, is a project of its own.

The existing stylesheet also has a property worth keeping: components own no
colours. Every colour comes from a token, redefined once for dark. A component
library with its own theming system either respects that or quietly competes with
it, and two sources of truth for one colour is the drift the file's own header
comment warns about.

## Decision

**`@mui/material` is the component library, and its theme is the single source of
truth for colour, spacing, radius and type.** Four dependencies: `@mui/material`,
`@mui/icons-material`, `@emotion/react`, `@emotion/styled`, each **pinned to an
exact version** rather than a caret range.

`web/src/theme/theme.ts` holds one `createTheme` call using MUI's CSS theme
variables (`cssVariables: { colorSchemeSelector: 'data-color-scheme' }`) with both
`colorSchemes.light` and `colorSchemes.dark` defined. It is **seeded with the
palette that exists today** - `primary.main` `#1f5c3f` light and `#2f8f63` dark,
`background.default` `#f6f7f5` / `#121513`, `background.paper` `#ffffff` /
`#1b1f1d`, `error.main` `#a32020` / `#ff8a80`, `shape.borderRadius: 12` - so the
restyle is not also a rebrand and the generated icons and manifest `theme_color`
stay correct.

`breakpoints.values.sm` is set to **768**, so `theme.breakpoints.up('sm')` *is* the
historical `48rem`. Without that line MUI's own 600px default silently becomes the
breakpoint and every layout decision shifts by 168 pixels.

**The hand-written tokens are deleted, not mapped.** `--brand`, `--surface`,
`--text` and the rest go away along with every component class, as each component
is converted. A mapping layer (`--brand: var(--mui-palette-primary-main)`) would
leave two names for one colour, and the old name would quietly survive in a
stylesheet nobody is maintaining. What remains in `index.css` is about forty lines:
`box-sizing`, `body { margin: 0 }`, `-webkit-text-size-adjust`, the safe-area
padding helpers the app bar and drawer need, and the `100dvh` shell height.
`CssBaseline` covers everything else.

**From here on no component introduces a global class.** Styling is `sx`, a styled
component, or a theme override. This is the rule that replaces "components own no
colours", and it is the thing that keeps the stylesheet from growing back.

**Icons are imported one per path** - `import ChecklistRounded from
'@mui/icons-material/ChecklistRounded'` - never from the package root and never
through a namespace import or a lookup by string. `@mui/icons-material` is roughly
11,000 modules, so the barrel is the one import in this project that can put
megabytes into a bundle served to phones. Per-path imports cannot, whatever the
bundler does. When the curated list icons arrive they live in one static
token-to-component map (`web/src/lists/listIcons.tsx`) built from such imports,
so the mapping is explicit and statically analysable.

## Consequences

- The component surface the feature needs arrives accessible, keyboard-navigable
  and Material-conformant, which is most of R35 for the cost of a dependency.
- Four dependencies replace zero. The bundle grows; the phase that lands this
  reports `pnpm build` output size in its PR so later phases have a baseline.
- `pnpm`'s 24h `minimumReleaseAge` applies, and it fails `--frozen-lockfile`
  **inside the Docker build** where a host `pnpm install` succeeds. Exact pins plus
  a local `docker build web/` before the PR is the only reliable check.
- Dark mode stops being a pure media query and becomes a stored preference with a
  `system` option (R34). MUI's `useColorScheme` plus `InitColorSchemeScript` and its
  `localStorage` mirror give the right colours on first paint, before `/api/me`
  resolves; without the mirror every reload flashes light before going dark.
- `useMediaQuery` needs `window.matchMedia`, which jsdom does not provide, so the
  vitest setup file gains a stub. That is a one-line test-infrastructure cost.
- Emotion injects `<style>` elements at runtime. The service worker caches nothing
  (adr/0007), so there is no stale-asset interaction to worry about.
- Converting every existing screen must happen in one change. A half-converted
  stylesheet is both systems at once, which is worse than either.

## Alternatives considered

- **Keep the hand-written CSS and build Material-looking components.** Zero
  dependencies, total control, and the current approach extended. Rejected on
  volume: a correct navigation drawer, autocomplete and dialog are each days of
  work plus the accessibility details that are easy to get subtly wrong, and R35
  asks for Material Design rather than something that resembles it.
- **Material Web (`@material/web`).** Google's own components, closest to Material 3
  and genuinely token-driven, which would have fitted the existing CSS philosophy
  best. Rejected because they are web components: React needs wrappers for
  properties and events, and `@testing-library` queries against shadow DOM are
  awkward - which would degrade the role-and-label test convention the project
  relies on.
- **Tailwind plus a headless library** (Radix, Headless UI). Smaller runtime and
  excellent primitives, but Material Design would still be hand-built on top, so it
  answers the styling question while leaving R35's actual request open.
- **Mapping the existing tokens onto MUI's variables and keeping both.** Looks like
  a cheap migration and leaves two names for every colour, with no mechanism to
  retire the old one. The reason the tokens are deleted instead.

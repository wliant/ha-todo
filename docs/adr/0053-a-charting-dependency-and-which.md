# 0053 - The chart is @mui/x-charts, pinned and lazily loaded

Status: accepted
Date: 2026-11-19

## Context

0019 R167 and R168 put a chart on a tracker's screen: a line or bars over the
chosen range, several members overlaid, gaps drawn as gaps, and an optional
target line. The app had **no charting dependency** - `web/package.json` held
MUI, Emotion, TanStack Query, axios, React Router and TipTap, and nothing else
drew anything.

Three constraints were real:

- **adr/0014**: "styling is the MUI theme, and nothing else", and no component
  introduces a global class. The old `:root` token block was deleted rather
  than mapped, because two names for one colour is how the old one survives.
- **The app runs on phones**, and CLAUDE.md requires new dependencies to be
  pinned to an exact, aged version and verified with a real `docker build web/`
  - a lockfile that resolves on the host and fails under `--frozen-lockfile`
  inside the image is found at the worst possible moment.
- **Most sessions never open a tracker.** The same was true of the detail
  editor, which is why ProseMirror is behind `React.lazy`.

## Decision

**`@mui/x-charts`, pinned exactly at `9.17.0`.**

Published 2026-10-30, so three weeks old at adoption - deliberately not
`9.18.1`, which was three days old. Its peer ranges accept what we have:
`react ^17 || ^18 || ^19` against React 19, and
`@mui/material ^7.3.0 || ^9.0.0` against `@mui/material@9.5.0`. The community
package is MIT; `@mui/x-charts-pro` is the licensed one and nothing here
touches it.

It wins on adr/0014. x-charts draws through **our** theme - the same palette,
typography and light/dark scheme the rest of the app already uses - so a chart
needs no colours of its own and cannot drift from the product when the theme
changes.

**Two rules come with it, and both exist because breaking them fails
silently.**

1. **One import site.** `web/src/trackers/TrackerChart.tsx` is the only module
   that may import `@mui/x-charts`, and it imports by deep path
   (`@mui/x-charts/LineChart`), never the package root - the icon-barrel rule
   generalised.
2. **Reached only through `React.lazy`**, behind a `Skeleton` of the chart's
   exact height so the table below it does not jump.

A second import anywhere drags the library and its bundled d3 into the main
chunk. Nothing fails, no test goes red, and every member who never opens a
tracker pays for it on every cold load.

Chart form is **derived from the tracker, not configured**: `BarChart` when the
rollup is SUM, `LineChart` otherwise. Monthly spend is bars; a weight trend is
a line.

## Consequences

The tracker screen gets axes, ticks, tooltips, touch handling and a reference
line for free, themed, in both colour schemes, and accessible by default - all
of which a hand-rolled SVG would have had to earn one at a time.

It is now the **heaviest dependency in the app** - the package plus its trimmed
d3 vendor and gesture helper, on the order of 120-160 KB gzipped, comparable to
ProseMirror. Lazy loading is what keeps that off everybody else's first paint,
and `pnpm build` must show it as its **own chunk**: if `index-*.js` grows by
~140 KB, rule 1 has been broken somewhere.

**Tests must not assert on the rendered SVG.** x-charts changes its internal
markup between minor versions, so an assertion on paths or `<g>` structure is a
time bomb. The assertions live on `trackers/series.ts` - the pure module that
turns a server series into chart props - and on the chart's accessible label.

## Alternatives considered

- **Recharts** (3.10.1 was the aged pick, React 19 peer is fine). Rejected on
  adr/0014: it brings its own colour and typography story, so the theme would
  need an adapter, and a second place where colour is decided is exactly what
  deleting the token block was meant to prevent. It also adds `react-is`.
- **Hand-rolled inline SVG.** No dependency, full theme control, and the
  initial recommendation. Rejected by the user in favour of a library, and the
  trade is real: axes, ticks, tooltips, touch targets, gap handling and
  accessibility are each a small thing and together are the library.
- **`@mui/x-charts` imported eagerly** - simpler, and silently charges every
  member for a screen most of them never open.

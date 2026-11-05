# 0037 - The period on screen lives in the address

Status: accepted
Date: 2026-11-06

## Context

`requirements/0008` makes the agenda navigable: the three ranges become a day, a
week and a month rather than today, this week and this month (R73), with
controls either side (R74) and one back to the present (R75). Something has to
remember which period is being looked at.

The product has a rule about this. `adr/0015` gives `AppShell` the **only**
piece of layout state in the application and the only `useMediaQuery` call, and
narrows rather than drops the older CSS-only rule: nothing else acquires layout
state, and everything else responsive stays CSS. `DashboardPage` already follows
the spirit of it - the range is a *route* (`/dashboard/day`, `/week`, `/month`)
and the scope is a *search param* (`?scope=mine`), with a comment saying why:
scope filters each view rather than being a different view, so it is
deep-linkable and adds no state.

The three views each compute `now(zone)` once per mount, inside a `useMemo`,
with a comment explaining that a date computed during render makes the query key
depend on when React happened to re-render. That stops being sufficient the
moment the period can change without a remount.

## Decision

**The period is a search param: `?on=YYYY-MM-DD`, absent when it is today.**

- `DashboardPage` reads it, defaults it to the household's today, and passes an
  **anchor** date down through the existing view context beside `scope` and
  `zone`. No component acquires state of its own.
- Each view derives its own range from the anchor - the day containing it, the
  week containing it, the month containing it - so switching range keeps the
  period (R76) with no extra work.
- Stepping rewrites the param. The browser's back button therefore walks the
  periods, which is what a member expects of something that changed the address.
- The param is **omitted when it is today**, exactly as `scope` is omitted when
  it is `all`: a clean address for the common case, and one canonical form.
- `now(zone)` stays, and is still the only impure thing in `calendar.ts` - but
  it now answers "what is the present", not "what am I looking at". The two are
  different questions and the views must keep asking the right one (0008's
  first consequence).

## Consequences

- **Reload, back button and a shared link all work** without a line of code
  each, because the address is the state.
- **Nothing new is stateful.** The rule `adr/0015` narrowed stays narrowed: one
  piece of layout state, in `AppShell`, and nowhere else.
- **The query key follows the period**, so TanStack Query caches each period
  separately and walking back and forth is free after the first visit. Also the
  hazard the views already warn about: the anchor must come from the param
  rather than from a clock read during render, or two renders disagree about
  which period they asked for.
- **An invalid or absurd `?on=` has to degrade**, not throw: it is in a URL that
  anybody can edit or truncate. An unparseable value falls back to today.
- **A link outlives its day** (0008's fourth consequence): an address sent in
  March still opens March in June.
- **Two sources of "now" now exist in the UI** - the anchor and the real clock -
  and conflating them is the mistake this feature introduces. Today's highlight
  in the week and month grids, and whether the return control is available, read
  the clock; everything about layout reads the anchor.

## Alternatives considered

**Component state in `DashboardPage` (`useState`)**. Simplest to write, and
loses reload, the back button and shareable links - all three of which R76 asks
for explicitly. It would also be the second piece of non-layout state in the
shell, which `adr/0015` exists to prevent.

**The period in the path** (`/dashboard/month/2026-11`). Reads well, and makes
the route table carry a date format per range, with three places to validate and
three to default. The range genuinely is a different view and the period
genuinely is a parameter of it; the existing split between route and search
param already says so.

**Three independent periods, one per range.** Would let a member keep a month in
view while checking a day. Rejected: switching range would jump somewhere
unrelated, and R76 asks for the opposite - the week containing the day you were
looking at.

**Storing the last viewed period per member**, so the agenda reopens where it was
left. Rejected outright: the landing page (0005 R44, `adr/0028`) must answer
"what do I need to do *now*", and a dashboard that opens on a week in February
because that is where somebody left it is a dashboard nobody trusts.

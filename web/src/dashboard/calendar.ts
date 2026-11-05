/**
 * The date arithmetic the dashboard needs, and nothing more.
 *
 * Hand-rolled rather than brought in: what is wanted is start-of-day,
 * start-of-week, add-days, the bounds of a month and a 6x7 grid, which is about
 * forty lines - while all the *formatting* is already `Intl`. A date-picker
 * library would mean a second MUI release train plus one of date-fns / dayjs /
 * luxon, well over 100KB, and it would replace a native `datetime-local` input
 * that is already accessible and already a real picker on a phone. See
 * adr/0028.
 *
 * Every function here uses setDate/setHours and **never** arithmetic on
 * milliseconds. Adding 86_400_000 is an hour wrong twice a year, and the bug
 * surfaces as a week view whose Monday column is mysteriously empty.
 *
 * **No function here reads the device's zone.** Days belong to the household,
 * not to the phone looking at them (adr/0036), so every entry point takes a
 * zone and `civilIn` is the only place a real instant becomes one. A helper
 * added later that leaves the zone out will work perfectly in Singapore and be
 * wrong for exactly one member, once, on a trip.
 */

/**
 * An IANA zone id, as the server reports it on /api/me.
 *
 * <p>A named type rather than a bare string so a forgotten argument reads as a
 * type error about a zone rather than about a string.
 */
export type Zone = string

/**
 * The household's civil date and time, carried in a `Date` whose *local* fields
 * hold it.
 *
 * <p>This is the trick the rest of the module rests on: once the household's
 * wall clock is in a `Date`'s local fields, `getDate()`, `getDay()` and
 * `setDate()` all mean what this module wants them to mean, and every function
 * below stays the arithmetic it already was. The instant such a value points at
 * is **meaningless** - use `instantOf` to get a real one, and never
 * `.toISOString()` or `.getTime()` on a civil date.
 */
export type Civil = Date

/** Weeks start Monday, hard-coded: one household, one locale. */
const WEEK_STARTS_ON = 1

/**
 * Now, as the household's wall clock.
 *
 * The only impure thing in this module, so a component can take a range inside
 * a `useMemo` without reaching for the clock itself - which is both the lint
 * rule's point and a real hazard: a date computed during render makes the query
 * key depend on when React re-rendered.
 */
export function now(zone: Zone): Civil {
  return civilIn(new Date(), zone)
}

/**
 * A real instant, read as the household's wall clock.
 *
 * <p>`Intl` rather than any arithmetic of our own, because it is the only thing
 * in the browser that knows the zone database - and `formatToParts` with
 * `hour12: false` is the one shape of it that reports a 24-hour clock
 * reliably, including the midnight that some locales render as 24.
 */
export function civilIn(instant: Date, zone: Zone): Civil {
  const parts = PARTS(zone).formatToParts(instant)
  const field = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? '0')
  return new Date(
    field('year'),
    field('month') - 1,
    field('day'),
    field('hour') % 24,
    field('minute'),
  )
}

/**
 * The real instant at which the household's clock reads this civil time.
 *
 * <p>Solved rather than looked up: the offset of a zone depends on the instant,
 * and the instant is what is being computed. One correction pass settles it -
 * the first guess is only wrong when the offset changed between the two, and
 * the second guess is taken at the corrected instant. Singapore has had no
 * daylight saving since 1935, so the pass is insurance rather than daily
 * arithmetic.
 */
export function instantOf(civil: Civil, zone: Zone): Date {
  const asUtc = Date.UTC(
    civil.getFullYear(),
    civil.getMonth(),
    civil.getDate(),
    civil.getHours(),
    civil.getMinutes(),
  )
  const firstGuess = asUtc - offsetAt(new Date(asUtc), zone)
  return new Date(asUtc - offsetAt(new Date(firstGuess), zone))
}

/** How far ahead of UTC the zone is at this instant, in milliseconds. */
function offsetAt(instant: Date, zone: Zone): number {
  const civil = civilIn(instant, zone)
  const asUtc = Date.UTC(
    civil.getFullYear(),
    civil.getMonth(),
    civil.getDate(),
    civil.getHours(),
    civil.getMinutes(),
  )
  // The seconds and milliseconds of the instant are not in `civil`, so they
  // would otherwise show up as a sub-minute offset.
  const truncated = Math.floor(instant.getTime() / 60_000) * 60_000
  return asUtc - truncated
}

/**
 * Formatters are expensive to construct and are constructed per render
 * otherwise; one per zone is all this ever needs.
 */
const FORMATTERS = new Map<Zone, Intl.DateTimeFormat>()

function PARTS(zone: Zone): Intl.DateTimeFormat {
  const existing = FORMATTERS.get(zone)
  if (existing) {
    return existing
  }
  const created = new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  FORMATTERS.set(zone, created)
  return created
}

export function startOfDay(date: Date): Date {
  const start = new Date(date)
  start.setHours(0, 0, 0, 0)
  return start
}

export function addDays(date: Date, days: number): Date {
  const moved = new Date(date)
  moved.setDate(moved.getDate() + days)
  return moved
}

export function addMonths(date: Date, months: number): Date {
  const moved = new Date(date)
  // Anchored to the 1st first, so stepping from the 31st does not skid into
  // the month after next.
  moved.setDate(1)
  moved.setMonth(moved.getMonth() + months)
  return moved
}

/**
 * The Monday of this date's week, at midnight.
 *
 * `Intl` exposes first-day-of-week only through
 * `Intl.Locale.prototype.getWeekInfo`, which is not universally available - so
 * this is one constant rather than a feature detection.
 */
export function startOfWeek(date: Date): Date {
  const start = startOfDay(date)
  const shift = (start.getDay() - WEEK_STARTS_ON + 7) % 7
  return addDays(start, -shift)
}

export function startOfMonth(date: Date): Date {
  const start = startOfDay(date)
  start.setDate(1)
  return start
}

/** The seven days of this date's week, Monday first. */
export function weekDays(date: Date): Date[] {
  const monday = startOfWeek(date)
  return Array.from({ length: 7 }, (_unused, offset) => addDays(monday, offset))
}

/**
 * A 6x7 grid covering this date's month.
 *
 * Always 42 cells, so the grid never changes height between months - which
 * would otherwise make the whole page jump as somebody steps through them.
 */
export function monthGrid(date: Date): Date[] {
  const first = startOfWeek(startOfMonth(date))
  return Array.from({ length: 42 }, (_unused, offset) => addDays(first, offset))
}

/**
 * Half-open [from, to) in the household's civil time.
 *
 * <p>Civil, not instants: these bound the grid. `windowOf` turns one into the
 * pair of real instants the API is asked for.
 */
export interface Range {
  from: Civil
  to: Civil
}

/** The half-open instant window this civil range covers, as the API expects. */
export function windowOf(range: Range, zone: Zone): { from: string; to: string } {
  return {
    from: instantOf(range.from, zone).toISOString(),
    to: instantOf(range.to, zone).toISOString(),
  }
}

export function dayRange(date: Date): Range {
  const from = startOfDay(date)
  return { from, to: addDays(from, 1) }
}

export function weekRange(date: Date): Range {
  const from = startOfWeek(date)
  return { from, to: addDays(from, 7) }
}

/** The month, widened to whole weeks so the grid's edges are covered too. */
export function monthRange(date: Date): Range {
  const from = startOfWeek(startOfMonth(date))
  return { from, to: addDays(from, 42) }
}

export function isSameDay(left: Date, right: Date): boolean {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  )
}

export function isSameMonth(left: Date, right: Date): boolean {
  return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth()
}

/** Groups items by the household day their due date falls on. */
export function byDay<T>(
  items: T[],
  dueAt: (item: T) => string | null,
  zone: Zone,
): Map<string, T[]> {
  const days = new Map<string, T[]>()
  for (const item of items) {
    const iso = dueAt(item)
    if (!iso) {
      continue
    }
    const key = dayKey(civilIn(new Date(iso), zone))
    const existing = days.get(key)
    if (existing) {
      existing.push(item)
    } else {
      days.set(key, [item])
    }
  }
  return days
}

/**
 * A civil-date key, never `toISOString().slice(0, 10)` - that is UTC's day, and
 * this module's dates are the household's.
 */
export function dayKey(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

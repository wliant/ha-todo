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
 */

/** Weeks start Monday, hard-coded: one household, one locale. */
const WEEK_STARTS_ON = 1

/**
 * Now, in one place.
 *
 * The only impure thing in this module, so a component can take a range inside
 * a `useMemo` without reaching for the clock itself - which is both the lint
 * rule's point and a real hazard: a date computed during render makes the query
 * key depend on when React re-rendered.
 */
export function now(): Date {
  return new Date()
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

/** Half-open [from, to), as the API expects. */
export interface Range {
  from: Date
  to: Date
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

/** Groups items by the local day their due date falls on. */
export function byDay<T>(items: T[], dueAt: (item: T) => string | null): Map<string, T[]> {
  const days = new Map<string, T[]>()
  for (const item of items) {
    const iso = dueAt(item)
    if (!iso) {
      continue
    }
    const key = dayKey(new Date(iso))
    const existing = days.get(key)
    if (existing) {
      existing.push(item)
    } else {
      days.set(key, [item])
    }
  }
  return days
}

/** A local-date key, never `toISOString().slice(0, 10)` - that is UTC's day. */
export function dayKey(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

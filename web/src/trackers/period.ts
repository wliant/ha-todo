import { addDays, addMonths, dayKey, startOfMonth, startOfWeek, type Civil } from '../dashboard/calendar'
import type { TrackerPeriod } from '../api/trackers'

/**
 * Bucket maths in the browser, mirroring `TrackerPeriod` on the server
 * (requirements/0019 R166).
 *
 * <p>The server is still the authority - it canonicalises whatever arrives -
 * so this exists only so the form can *show* the right bucket and step through
 * them. Everything here works on `Civil` values from `dashboard/calendar.ts`,
 * which are the household's dates, and emits `YYYY-MM-DD` strings. No `Date`
 * is held in component state and nothing calls `new Date(localString)`, which
 * is the one bug adr/0036 exists to prevent.
 */

/** The first day of the bucket a household date falls in. */
export function startOfPeriod(day: Civil, period: TrackerPeriod): Civil {
  switch (period) {
    case 'DAY':
      return day
    case 'WEEK':
      return startOfWeek(day)
    case 'MONTH':
      return startOfMonth(day)
  }
}

/** The bucket `steps` buckets away, which is what the form's steppers walk. */
export function stepPeriod(day: Civil, period: TrackerPeriod, steps: number): Civil {
  switch (period) {
    case 'DAY':
      return addDays(day, steps)
    case 'WEEK':
      return addDays(day, steps * 7)
    case 'MONTH':
      return startOfMonth(addMonths(startOfMonth(day), steps))
  }
}

/** The canonical bucket key for a household date: what the wire carries. */
export function periodKey(day: Civil, period: TrackerPeriod): string {
  return dayKey(startOfPeriod(day, period))
}

/**
 * How a bucket reads to a member.
 *
 * <p>A week is named by its span rather than by its Monday, because "16 Nov"
 * for a week that includes the 22nd is a date somebody will mistake for a day.
 */
export function describePeriod(bucket: Civil, period: TrackerPeriod): string {
  switch (period) {
    case 'DAY':
      return bucket.toLocaleDateString(undefined, {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
      })
    case 'WEEK': {
      const start = startOfWeek(bucket)
      const end = addDays(start, 6)
      const sameMonth = start.getMonth() === end.getMonth()
      return `${start.toLocaleDateString(undefined, {
        day: 'numeric',
        ...(sameMonth ? {} : { month: 'short' }),
      })}-${end.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`
    }
    case 'MONTH':
      return bucket.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
  }
}

/** The word a form uses for the thing it is asking for. */
export function periodNoun(period: TrackerPeriod): string {
  switch (period) {
    case 'DAY':
      return 'day'
    case 'WEEK':
      return 'week'
    case 'MONTH':
      return 'month'
  }
}

/**
 * Parses a bucket key back into a `Civil`.
 *
 * <p>Split and constructed rather than `new Date('2026-11-19')`, which is
 * parsed as **UTC midnight** and so is the previous day everywhere west of
 * Greenwich - the device-zone trap in its other form.
 */
export function civilFromKey(key: string): Civil {
  const [year, month, day] = key.split('-').map(Number)
  return new Date(year, month - 1, day)
}

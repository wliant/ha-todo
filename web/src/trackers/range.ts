import type { TrackerPeriod } from '../api/trackers'
import { addDays, addMonths, dayKey, now, type Zone } from '../dashboard/calendar'
import { startOfPeriod } from './period'

/** The ranges a member can ask for (requirements/0019 R169). */
export type RangeKey = '30d' | '3m' | '1y' | 'all'

export const DEFAULT_RANGE: RangeKey = '3m'

/** Exported so the chips render exactly the ranges `windowFor` can answer. */
export const RANGES: { key: RangeKey; label: string }[] = [
  { key: '30d', label: '30 days' },
  { key: '3m', label: '3 months' },
  { key: '1y', label: 'Year' },
  { key: 'all', label: 'All' },
]

/** Anything unrecognised falls back, because this comes out of the address. */
export function parseRange(value: string | null): RangeKey {
  return RANGES.some((range) => range.key === value) ? (value as RangeKey) : DEFAULT_RANGE
}

/**
 * The window a range asks the server for, and the grain to draw it at.
 *
 * <p>The grain is what keeps a year of a daily tracker readable: 365 points on
 * a phone is a smear, so a long window is folded by the tracker's own rollup
 * into weeks or months - the same rule, one level up (R163). It can only ever
 * be coarser than the tracker records at; the server refuses finer.
 */
export function windowFor(
  range: RangeKey,
  period: TrackerPeriod,
  zone: Zone,
): { from?: string; to?: string; by: TrackerPeriod } {
  const today = now(zone)
  const to = dayKey(today)
  const by = grainFor(range, period)
  switch (range) {
    case '30d':
      return { from: dayKey(startOfPeriod(addDays(today, -30), period)), to, by }
    case '3m':
      return { from: dayKey(startOfPeriod(addMonths(today, -3), period)), to, by }
    case '1y':
      return { from: dayKey(startOfPeriod(addMonths(today, -12), period)), to, by }
    case 'all':
      // No `from`: the server's own default window is the widest this asks
      // for, and "all" on a tracker with five years of daily readings is
      // exactly where the 2000-row cap exists.
      return { to, by }
  }
}

function grainFor(range: RangeKey, period: TrackerPeriod): TrackerPeriod {
  if (period !== 'DAY') {
    // A weekly or monthly tracker is already coarse enough to draw whole.
    return period
  }
  switch (range) {
    case '30d':
      return 'DAY'
    case '3m':
      return 'WEEK'
    default:
      return 'MONTH'
  }
}

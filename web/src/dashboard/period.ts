import {
  addDays,
  addMonths,
  dayKey,
  isSameMonth,
  startOfWeek,
  type Civil,
} from './calendar'

/** Which of the agenda's three ranges is on screen. */
export type Range = 'day' | 'week' | 'month'

/** One day, seven days, or one month - the range's own notion of "next". */
export function step(range: Range, from: Civil, direction: 1 | -1): Civil {
  if (range === 'day') {
    return addDays(from, direction)
  }
  if (range === 'week') {
    return addDays(from, 7 * direction)
  }
  return addMonths(from, direction)
}

export function isCurrentPeriod(range: Range, anchor: Civil, today: Civil): boolean {
  if (range === 'day') {
    return dayKey(anchor) === dayKey(today)
  }
  if (range === 'week') {
    return dayKey(startOfWeek(anchor)) === dayKey(startOfWeek(today))
  }
  return isSameMonth(anchor, today)
}

export function returnLabel(range: Range): string {
  if (range === 'day') {
    return 'Today'
  }
  return range === 'week' ? 'This week' : 'This month'
}

/**
 * What the member is looking at, in words.
 *
 * <p>The day view says "Today" when it is today, because that is what a person
 * calls it - and says the date otherwise, because a heading reading "Today"
 * over another day is the lie the old tab label was.
 */
export function periodName(range: Range, anchor: Civil, today: Civil): string {
  if (range === 'day') {
    return dayKey(anchor) === dayKey(today)
      ? 'Today'
      : anchor.toLocaleDateString(undefined, {
          weekday: 'long',
          day: 'numeric',
          month: 'long',
        })
  }
  if (range === 'week') {
    const monday = startOfWeek(anchor)
    const sunday = addDays(monday, 6)
    // formatRange rather than two dates and a dash: it elides what the two ends
    // share and keeps the locale's own order, so a week inside one month reads
    // "23-29 Nov" and one that crosses reads "30 Nov - 6 Dec". Building that by
    // hand produced "23 - Nov 29".
    const format = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' })
    return typeof format.formatRange === 'function'
      ? format.formatRange(monday, sunday)
      : `${format.format(monday)} – ${format.format(sunday)}`
  }
  return anchor.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

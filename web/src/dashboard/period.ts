import {
  addDays,
  addMonths,
  dayKey,
  isSameMonth,
  startOfDay,
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

/**
 * How late a chore is, in words (0011 R90).
 *
 * <p>Whole days apart, in the household's civil time: both arguments are
 * `Civil`, so this is calendar arithmetic and not an elapsed-hours count. A
 * chore due at 23:00 yesterday and read at 07:00 today is "yesterday", which is
 * what a person would say about it, and what an hours-based answer would call
 * "8 hours ago".
 *
 * <p>Takes `today` rather than reading the clock, like everything else in these
 * two modules: a date computed during render is a value that moves under its
 * own refetch.
 */
export function latenessLabel(due: Civil, today: Civil): string {
  // `Math.round`, and that is the whole reason this is allowed to touch
  // milliseconds at all when `calendar.ts` says not to: both ends are already
  // snapped to midnight, so a daylight-saving boundary between them makes the
  // difference 23 or 25 hours rather than a whole number of days, and rounding
  // is what turns that back into the count of days a person would give.
  const days = Math.round(
    (startOfDay(today).getTime() - startOfDay(due).getTime()) / 86_400_000,
  )
  if (days <= 0) {
    // Not reachable from the overdue strip, whose rows the server has already
    // decided are late - but a label is a pure function and must answer for
    // every input it is given rather than printing "0 days ago".
    return 'due today'
  }
  if (days === 1) {
    return 'due yesterday'
  }
  if (days < 7) {
    return `due ${days} days ago`
  }
  // Beyond a week, "due 43 days ago" is arithmetic nobody asked for; the date
  // is what tells a member whether this is worth keeping.
  return `due ${due.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`
}

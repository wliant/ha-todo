import type { CalendarEvent } from '../api/calendars'
import type { DashboardItem } from '../api/dashboard'
import { civilIn, dayKey, type Zone } from './calendar'

/**
 * One thing on a day: a chore to do, or something that is happening.
 *
 * <p>The two are peers rather than two sections (requirements/0007 R67),
 * because the question a household asks in the morning is "what is Thursday
 * like" and not "what are my chores and separately what are my appointments".
 */
export type DayEntry =
  | { kind: 'item'; key: string; sortMinute: number; item: DashboardItem }
  | { kind: 'event'; key: string; sortMinute: number; event: CalendarEvent }

/**
 * Items and events, grouped by the household's day and in clock order.
 *
 * <p>An event already knows which day it is on - the server placed it there in
 * the household's zone, and an all-day event has no instant to place it by. An
 * item has a due instant, so it is read in the same zone. Anything that mixed
 * the two definitions would put a late-evening chore and a late-evening
 * appointment in different columns of the same view (adr/0036).
 */
export function byDayEntries(
  items: DashboardItem[],
  events: CalendarEvent[],
  zone: Zone,
): Map<string, DayEntry[]> {
  const days = new Map<string, DayEntry[]>()

  const push = (day: string, entry: DayEntry) => {
    const existing = days.get(day)
    if (existing) {
      existing.push(entry)
    } else {
      days.set(day, [entry])
    }
  }

  for (const item of items) {
    if (!item.dueAt) {
      continue
    }
    const civil = civilIn(new Date(item.dueAt), zone)
    push(dayKey(civil), {
      kind: 'item',
      key: `item-${item.id}`,
      sortMinute: civil.getHours() * 60 + civil.getMinutes(),
      item,
    })
  }

  for (const event of events) {
    push(event.occursOn, {
      kind: 'event',
      // An event from a preview has no id, and a collapsed one is identified by
      // where it sits rather than by which copy won.
      key: `event-${event.id ?? `${event.occursOn}-${event.sortMinute}-${event.summary}`}`,
      sortMinute: event.sortMinute,
      event,
    })
  }

  for (const entries of days.values()) {
    // All-day events carry -1, so they sort above the day's timed rows -
    // which is where somebody scanning a day expects "Ben away" to be.
    entries.sort((left, right) => left.sortMinute - right.sortMinute)
  }
  return days
}

/** What an event reads as before its title: a time, or that it has none. */
export function eventTime(event: CalendarEvent, zone: Zone): string {
  if (event.allDay) {
    return 'All day'
  }
  if (!event.startsAt) {
    return ''
  }
  return civilIn(new Date(event.startsAt), zone).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** "Ana's Family", or "Ana and Ben" when two feeds carried it (R69). */
export function eventSources(event: CalendarEvent): string {
  if (event.sources.length === 0) {
    return ''
  }
  return event.sources.map((source) => source.memberDisplayName).join(', ')
}

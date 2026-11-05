import { api } from './http'

/**
 * Subscribed calendar feeds (requirements/0007 R62-R66).
 *
 * <p>Its own module with its own DTOs, and no barrel re-exporting it: tests spy
 * by module namespace, and a re-exported live binding is exactly what
 * `vi.spyOn` cannot intercept.
 *
 * <p>**There is no `url` on `CalendarFeed`, and there must never be one.** The
 * address is a bearer capability for somebody's whole calendar and the server
 * does not return it - not to the household, not to an administrator, and not
 * to the member who typed it (adr/0035). It goes out in a request and is never
 * read back.
 */
export type CalendarProvider = 'GOOGLE' | 'OUTLOOK' | 'OTHER'

export interface CalendarFeed {
  id: string
  label: string
  provider: CalendarProvider
  /** Who else may see it. Never contains the owner. */
  memberIds: string[]
  lastSuccessAt: string | null
  lastError: string | null
  /** The server's judgement, not the client's arithmetic. */
  stale: boolean
  eventCount: number
}

/**
 * One event on the agenda.
 *
 * <p>Branch on `allDay` rather than formatting `startsAt` and hoping: an
 * all-day event carries dates and no instants, because a bare date has no
 * zone to be converted from (adr/0036).
 */
export interface CalendarEvent {
  id: string | null
  summary: string
  description: string | null
  location: string | null
  allDay: boolean
  startsAt: string | null
  endsAt: string | null
  startDate: string | null
  endDate: string | null
  /** The household-zone day it is placed on, as `YYYY-MM-DD`. */
  occursOn: string
  /** Minutes into the day, or -1 for an all-day event. */
  sortMinute: number
  sources: CalendarEventSource[]
}

export interface CalendarEventSource {
  feedId: string
  label: string
  memberId: string
  memberDisplayName: string
  avatarImageId: string | null
}

/** A calendar the caller can see that has stopped refreshing (R71). */
export interface CalendarFeedWarning {
  feedId: string
  label: string
  memberDisplayName: string
  lastSuccessAt: string | null
  lastError: string | null
}

export interface CalendarPreview {
  provider: CalendarProvider
  totalInWindow: number
  next: CalendarEvent[]
}

export interface SaveCalendarFeed {
  label: string
  /** Omitted on an edit means "leave the address alone" - see the module note. */
  url?: string
  /** Omitted on an edit means "leave the audience alone"; `[]` means nobody. */
  memberIds?: string[]
}

export async function fetchMyCalendars(): Promise<CalendarFeed[]> {
  const { data } = await api.get<CalendarFeed[]>('/me/calendars')
  return data
}

/** Fetches and shows; saves nothing (R65). */
export async function previewCalendar(url: string): Promise<CalendarPreview> {
  const { data } = await api.post<CalendarPreview>('/me/calendars/preview', { url })
  return data
}

export async function subscribeCalendar(request: SaveCalendarFeed): Promise<CalendarFeed> {
  const { data } = await api.post<CalendarFeed>('/me/calendars', request)
  return data
}

export async function updateCalendar(
  id: string,
  request: SaveCalendarFeed,
): Promise<CalendarFeed> {
  const { data } = await api.patch<CalendarFeed>(`/me/calendars/${id}`, request)
  return data
}

/** Worth a button: a published .ics lags its calendar, Google's by hours. */
export async function refreshCalendar(id: string): Promise<CalendarFeed> {
  const { data } = await api.post<CalendarFeed>(`/me/calendars/${id}/refresh`, {})
  return data
}

export async function unsubscribeCalendar(id: string): Promise<void> {
  await api.delete(`/me/calendars/${id}`)
}

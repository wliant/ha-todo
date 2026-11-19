/**
 * The one long-lived connection in the app (requirements/0021).
 *
 * Its own module with its own DTO and no barrel, like every other feature's -
 * tests spy by module namespace, and a re-exported live binding is exactly what
 * `vi.spyOn` cannot intercept.
 *
 * Not axios: `EventSource` is native, carries the session cookie same-origin
 * with no work, and owns its own reconnection. The relative path is the usual
 * rule - nginx proxies `/api`, so there is never an absolute API URL.
 */

/**
 * What the server writes down the stream, and almost nothing is on it.
 *
 * This is a **doorbell, not a second read API** (adr/0055). Nothing on screen is
 * drawn from it: the client's whole response is to invalidate what it has
 * cached and refetch through the endpoints that check the grant. So there is
 * deliberately no detail, no assignee, no costs, no due date and no version.
 */
export interface LiveNotification {
  /** `item.created` is the only one so far. The field exists so a second is additive. */
  type: string
  itemId: string
  listId: string
  listName: string
  description: string
  /** Null when the scheduled job added it and nobody asked. */
  actorDisplayName: string | null
  /**
   * Whether *this* recipient is the member who added it, decided on the server
   * (adr/0055). The client never compares user ids, so R181 lives in one place.
   * Always false for the recurrence job's work, including for the member who
   * wrote the rule.
   */
  byYou: boolean
}

export const LIVE_STREAM_PATH = '/api/live/stream'

/**
 * `EventSource.CLOSED`, as a number.
 *
 * Deliberately not read off the global: this module is loaded in browsers that
 * have no `EventSource` at all, which is the whole reason
 * {@link liveStreamSupported} exists - and a bare `EventSource.CLOSED` in a
 * handler is a ReferenceError waiting for one of them. The value is fixed by
 * the HTML specification.
 */
export const STREAM_CLOSED = 2
export const ITEM_CREATED = 'item.created'

/** Whether this browser can hold a stream at all. */
export function liveStreamSupported(): boolean {
  return typeof EventSource !== 'undefined'
}

/**
 * Opens the stream.
 *
 * A factory rather than a `new EventSource` at the call site, so a test can
 * substitute the transport without reaching for a global - jsdom has no
 * `EventSource`, and installing a fake one globally would mean no test ever
 * exercises the degraded path.
 */
export function openLiveStream(): EventSource {
  return new EventSource(LIVE_STREAM_PATH, { withCredentials: true })
}

export function parseNotification(raw: string): LiveNotification | null {
  try {
    const parsed: unknown = JSON.parse(raw)
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      typeof (parsed as LiveNotification).description === 'string' &&
      typeof (parsed as LiveNotification).listId === 'string'
    ) {
      return parsed as LiveNotification
    }
    return null
  } catch {
    // A frame we cannot read is not a reason to tear the agenda down.
    return null
  }
}

import { api } from './http'
import type { CalendarEvent, CalendarFeedWarning } from './calendars'
import type { ItemCostValue, ItemMember } from './items'
import type { ListIcon } from './lists'

/** An item, plus which list it came from - the dashboard crosses lists. */
export interface DashboardItem {
  id: string
  listId: string
  listName: string
  listIcon: ListIcon
  description: string
  detailHtml: string | null
  assignee: ItemMember | null
  selfAssigned: boolean
  dueAt: string | null
  costValues: ItemCostValue[]
  version: number
}

/**
 * A window's work, and the day the work has to fit into.
 *
 * <p>Events ride this payload rather than a second request: the grid draws both
 * together, and two queries would give one grid two loading states and a
 * visible flash of chores-without-events.
 */
export interface Scheduled {
  from: string
  to: string
  items: DashboardItem[]
  events: CalendarEvent[]
  /** Calendars that have stopped refreshing - an empty day now has two meanings. */
  staleFeeds: CalendarFeedWarning[]
  /**
   * How far either side of today events have been read at all (0008 R77).
   *
   * <p>Not a client-side constant: it is deployment configuration, and a copy
   * here would be wrong in exactly the deployment that changed it. Items have
   * no such bound - they come back for whatever window is asked for.
   */
  eventWindow: EventWindow
}

export interface EventWindow {
  /** Household dates, `YYYY-MM-DD`. */
  from: string
  to: string
}

export interface Undated {
  items: DashboardItem[]
  /** May exceed `items.length`: the page is capped. */
  total: number
}

/**
 * The open work whose day has gone (0011 R88).
 *
 * <p>The same shape as `Undated` and a separate type on purpose: they answer
 * two questions, and the two sets must never overlap.
 *
 * <p>No boundary rides this payload. Where today starts is the server's
 * decision, not a number the client could usefully hold or recompute - see
 * adr/0040.
 */
export interface Overdue {
  items: DashboardItem[]
  /** May exceed `items.length`, and on a first load usually does. */
  total: number
}

/** Everything readable by default; 'mine' narrows to the caller's own. */
export type DashboardScope = 'all' | 'mine'

export async function fetchScheduled(
  from: string,
  to: string,
  scope: DashboardScope,
): Promise<Scheduled> {
  const { data } = await api.get<Scheduled>('/dashboard/scheduled', {
    params: { from, to, scope },
  })
  return data
}

export async function fetchUndated(scope: DashboardScope): Promise<Undated> {
  const { data } = await api.get<Undated>('/dashboard/undated', { params: { scope } })
  return data
}

/**
 * What is late.
 *
 * <p>Takes `scope` and nothing else - no window, no anchor. That is R89 in the
 * signature: there is no bound for the period on screen to leak into, so
 * navigating cannot change what is overdue.
 */
export async function fetchOverdue(scope: DashboardScope): Promise<Overdue> {
  const { data } = await api.get<Overdue>('/dashboard/overdue', { params: { scope } })
  return data
}

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
}

export interface Undated {
  items: DashboardItem[]
  /** May exceed `items.length`: the page is capped. */
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

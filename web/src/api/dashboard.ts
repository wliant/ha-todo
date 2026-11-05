import { api } from './http'
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

export interface Scheduled {
  from: string
  to: string
  items: DashboardItem[]
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

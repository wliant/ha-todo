import { api } from './http'

export type ListIcon =
  | 'CHECKLIST'
  | 'SHOPPING_CART'
  | 'GROCERIES'
  | 'RESTAURANT'
  | 'PAYMENTS'
  | 'SAVINGS'
  | 'RECEIPT'
  | 'HEALTH'
  | 'MEDICATION'
  | 'FITNESS'
  | 'RUNNING'
  | 'SCHOOL'
  | 'READING'
  | 'HOME'
  | 'CLEANING'
  | 'REPAIRS'
  | 'GARDEN'
  | 'PETS'
  | 'CHILDREN'
  | 'TRAVEL'

export interface ListSummary {
  id: string
  name: string
  description: string | null
  icon: ListIcon
  owned: boolean
  pinned: boolean
  memberCount: number
}

export interface ListMember {
  id: string
  displayName: string
  owner: boolean
}

export interface ListDetail extends Omit<ListSummary, 'memberCount'> {
  /** Echoed back on the next write, so a stale editor is told rather than winning. */
  version: number
  members: ListMember[]
  createdAt: string
}

export interface Sidebar {
  pinned: ListSummary[]
  recent: ListSummary[]
}

export type ListScope = 'all' | 'owned' | 'shared'

export interface ListFilters {
  q?: string
  icon?: ListIcon | ''
  scope?: ListScope
  pinned?: boolean
}

export interface NewList {
  name: string
  description?: string
  icon: ListIcon
  memberIds: string[]
}

export async function fetchLists(filters: ListFilters = {}): Promise<ListSummary[]> {
  const { data } = await api.get<ListSummary[]>('/lists', {
    params: {
      q: filters.q || undefined,
      icon: filters.icon || undefined,
      scope: filters.scope && filters.scope !== 'all' ? filters.scope : undefined,
      pinned: filters.pinned ? true : undefined,
    },
  })
  return data
}

export async function fetchSidebar(): Promise<Sidebar> {
  const { data } = await api.get<Sidebar>('/lists/sidebar')
  return data
}

export async function fetchList(id: string): Promise<ListDetail> {
  const { data } = await api.get<ListDetail>(`/lists/${id}`)
  return data
}

export async function createList(list: NewList): Promise<ListDetail> {
  const { data } = await api.post<ListDetail>('/lists', list)
  return data
}

export async function updateList(
  id: string,
  update: { version: number; name: string; description?: string; icon: ListIcon },
): Promise<ListDetail> {
  const { data } = await api.patch<ListDetail>(`/lists/${id}`, update)
  return data
}

export async function deleteList(id: string): Promise<void> {
  await api.delete(`/lists/${id}`)
}

export async function updateListAccess(
  id: string,
  update: { version: number; memberIds: string[] },
): Promise<ListMember[]> {
  const { data } = await api.put<ListMember[]>(`/lists/${id}/access`, update)
  return data
}

export async function setPinned(id: string, pinned: boolean): Promise<void> {
  if (pinned) {
    await api.put(`/lists/${id}/pin`)
  } else {
    await api.delete(`/lists/${id}/pin`)
  }
}

/**
 * "I opened this list." Deliberately a write the client asks for, rather than a
 * side effect of reading - see the backend's note on why recency must not live
 * on a GET.
 */
export async function touchList(id: string): Promise<void> {
  await api.post(`/lists/${id}/touch`)
}

export interface Member {
  id: string
  displayName: string
}

/**
 * The household, for the access picker. Not the sign-in roster: that one is
 * public and must never grow a field (docs/adr/0009).
 */
export async function fetchMembers(): Promise<Member[]> {
  const { data } = await api.get<Member[]>('/users')
  return data
}

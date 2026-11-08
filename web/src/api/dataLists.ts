import { api } from './http'
import type { ListIcon } from './lists'

/**
 * Collections (requirements/0006 R50) - a reference list, not a list of work.
 *
 * <p>Its own module with its own DTOs, and no barrel re-exporting it: tests spy
 * by module namespace, and a re-exported live binding is exactly what
 * `vi.spyOn` cannot intercept.
 *
 * The icon type is shared with lists because it is one closed set of drawings;
 * nothing else here is.
 */
export interface CollectionSummary {
  id: string
  name: string
  description: string | null
  icon: ListIcon
  owned: boolean
  /** This member's own pin (0012 R104, adr/0043), not a property of the collection. */
  pinned: boolean
  memberCount: number
  entryCount: number
}

export interface CollectionMember {
  id: string
  displayName: string
  owner: boolean
  avatarImageId: string | null
}

export interface CollectionDetail {
  id: string
  name: string
  description: string | null
  icon: ListIcon
  owned: boolean
  /** This member's own pin (0012 R104). */
  pinned: boolean
  /** Echoed back on the next write, so a stale editor is told rather than winning. */
  version: number
  members: CollectionMember[]
  createdAt: string
}

export type CollectionScope = 'all' | 'owned' | 'shared'

export interface CollectionFilters {
  q?: string
  icon?: ListIcon | ''
  scope?: CollectionScope
}

export interface NewCollection {
  name: string
  description?: string
  icon: ListIcon
  memberIds: string[]
}

export interface TagSuggestion {
  tag: string
  count: number
}

export async function fetchCollections(
  filters: CollectionFilters = {},
): Promise<CollectionSummary[]> {
  const { data } = await api.get<CollectionSummary[]>('/data-lists', {
    params: {
      q: filters.q || undefined,
      icon: filters.icon || undefined,
      scope: filters.scope && filters.scope !== 'all' ? filters.scope : undefined,
    },
  })
  return data
}

export async function fetchCollection(id: string): Promise<CollectionDetail> {
  const { data } = await api.get<CollectionDetail>(`/data-lists/${id}`)
  return data
}

export async function createCollection(collection: NewCollection): Promise<CollectionDetail> {
  const { data } = await api.post<CollectionDetail>('/data-lists', collection)
  return data
}

export async function updateCollection(
  id: string,
  update: { version: number; name: string; description?: string; icon: ListIcon },
): Promise<CollectionDetail> {
  const { data } = await api.patch<CollectionDetail>(`/data-lists/${id}`, update)
  return data
}

export async function deleteCollection(id: string): Promise<void> {
  await api.delete(`/data-lists/${id}`)
}

export async function updateCollectionAccess(
  id: string,
  update: { version: number; memberIds: string[] },
): Promise<CollectionMember[]> {
  const { data } = await api.put<CollectionMember[]>(`/data-lists/${id}/access`, update)
  return data
}

/** What this collection already uses, commonest first (R52). */
export async function fetchTagSuggestions(id: string, q?: string): Promise<TagSuggestion[]> {
  const { data } = await api.get<TagSuggestion[]>(`/data-lists/${id}/tags`, {
    params: { q: q || undefined },
  })
  return data
}

/**
 * The sidebar's collections group (0012 R98).
 *
 * <p>Its own request rather than a second field on the lists' sidebar: the two
 * groups are independent and nothing is drawn from both, so there is no pair of
 * values that can disagree while one is in flight (adr/0042).
 */
export interface CollectionSidebar {
  pinned: CollectionSummary[]
  collections: CollectionSummary[]
}

export async function fetchCollectionSidebar(): Promise<CollectionSidebar> {
  const { data } = await api.get<CollectionSidebar>('/data-lists/sidebar')
  return data
}

/** The caller's own pin on a collection (0012 R104). */
export async function setCollectionPinned(id: string, pinned: boolean): Promise<void> {
  if (pinned) {
    await api.put(`/data-lists/${id}/pin`)
  } else {
    await api.delete(`/data-lists/${id}/pin`)
  }
}

/**
 * "I opened this collection." A write the client asks for, not a side effect of
 * reading - the same reason a list's touch is a POST.
 */
export async function touchCollection(id: string): Promise<void> {
  await api.post(`/data-lists/${id}/touch`)
}

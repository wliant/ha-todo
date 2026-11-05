import { api } from './http'
import type { ListIcon } from './lists'

/** One entry in a search result or a listing. Never carries the detail (R57). */
export interface EntryHit {
  id: string
  dataListId: string
  /** Which collection this came from - the part R54 exists to answer. */
  listName: string
  listIcon: ListIcon
  name: string
  description: string | null
  tags: string[]
  version: number
  updatedAt: string
}

export interface EntryDetail {
  id: string
  dataListId: string
  name: string
  description: string | null
  detailHtml: string | null
  tags: string[]
  version: number
  createdById: string | null
  createdByName: string | null
  createdAt: string
  updatedAt: string
}

export interface SaveEntry {
  version?: number
  name: string
  description?: string
  detailHtml?: string | null
  tags?: string[]
}

export interface EntryQuery {
  q?: string
  tags?: string[]
  limit?: number
}

function params(query: EntryQuery) {
  return {
    q: query.q || undefined,
    // Repeated `tag` parameters, which is what the controller binds a List to.
    tag: query.tags && query.tags.length > 0 ? query.tags : undefined,
    limit: query.limit || undefined,
  }
}

/** One collection's entries, or a search within it - the same call (R53). */
export async function fetchEntries(listId: string, query: EntryQuery = {}): Promise<EntryHit[]> {
  const { data } = await api.get<EntryHit[]>(`/data-lists/${listId}/entries`, {
    params: params(query),
  })
  return data
}

/** Across every collection the member can see (R54). */
export async function searchEntries(query: EntryQuery = {}): Promise<EntryHit[]> {
  const { data } = await api.get<EntryHit[]>('/data-entries', { params: params(query) })
  return data
}

export async function fetchEntry(id: string): Promise<EntryDetail> {
  const { data } = await api.get<EntryDetail>(`/data-entries/${id}`)
  return data
}

export async function createEntry(listId: string, entry: SaveEntry): Promise<EntryDetail> {
  const { data } = await api.post<EntryDetail>(`/data-lists/${listId}/entries`, entry)
  return data
}

export async function updateEntry(id: string, entry: SaveEntry): Promise<EntryDetail> {
  const { data } = await api.patch<EntryDetail>(`/data-entries/${id}`, entry)
  return data
}

export async function deleteEntry(id: string, version: number): Promise<void> {
  await api.delete(`/data-entries/${id}`, { params: { version } })
}

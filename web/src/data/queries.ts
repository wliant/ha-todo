import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createCollection,
  deleteCollection,
  fetchCollection,
  fetchCollections,
  fetchTagSuggestions,
  updateCollection,
  updateCollectionAccess,
  type CollectionFilters,
} from '../api/dataLists'
import {
  createEntry,
  deleteEntry,
  fetchEntries,
  fetchEntry,
  searchEntries,
  updateEntry,
  type EntryQuery,
  type SaveEntry,
} from '../api/dataEntries'

/**
 * A hierarchy, so prefix invalidation reaches everything collection-shaped at
 * once - the same convention `listKeys` uses, and for the same reason: a
 * hand-maintained invalidation matrix is where a stale screen after a rename
 * comes from.
 */
export const collectionKeys = {
  all: ['collections'] as const,
  search: (filters: CollectionFilters) => ['collections', 'search', filters] as const,
  detail: (id: string) => ['collections', 'detail', id] as const,
  tags: (id: string) => ['collections', 'detail', id, 'tags'] as const,
  entries: (id: string, query: EntryQuery) =>
    ['collections', 'detail', id, 'entries', query] as const,
}

/**
 * Global search sits outside the collection hierarchy on purpose: it spans
 * every collection, so invalidating one collection must not leave it stale and
 * invalidating it must not drop a collection's own caches.
 */
export const entryKeys = {
  all: ['entries'] as const,
  global: (query: EntryQuery) => ['entries', 'global', query] as const,
  detail: (id: string) => ['entries', 'detail', id] as const,
}

export function useCollections(filters: CollectionFilters) {
  return useQuery({
    queryKey: collectionKeys.search(filters),
    queryFn: () => fetchCollections(filters),
  })
}

export function useCollection(id: string | undefined) {
  return useQuery({
    queryKey: collectionKeys.detail(id ?? 'none'),
    queryFn: () => fetchCollection(id!),
    enabled: Boolean(id),
  })
}

export function useEntries(id: string | undefined, query: EntryQuery) {
  return useQuery({
    queryKey: collectionKeys.entries(id ?? 'none', query),
    queryFn: () => fetchEntries(id!, query),
    enabled: Boolean(id),
  })
}

/**
 * Enabled only once there is something to search for. Without that, opening the
 * collections page would fetch every entry the member can see in order to show
 * nothing.
 */
export function useGlobalEntrySearch(query: EntryQuery) {
  const hasQuery = Boolean(query.q && query.q.trim().length > 0)
  return useQuery({
    queryKey: entryKeys.global(query),
    queryFn: () => searchEntries(query),
    enabled: hasQuery,
  })
}

export function useEntry(id: string | undefined) {
  return useQuery({
    queryKey: entryKeys.detail(id ?? 'none'),
    queryFn: () => fetchEntry(id!),
    enabled: Boolean(id),
  })
}

export function useTagSuggestions(id: string | undefined) {
  return useQuery({
    queryKey: collectionKeys.tags(id ?? 'none'),
    queryFn: () => fetchTagSuggestions(id!),
    enabled: Boolean(id),
    staleTime: 30_000,
  })
}

/**
 * Both hierarchies, because an entry write changes a collection's entry count
 * and can change what global search would return.
 */
function useCollectionInvalidation() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: collectionKeys.all })
    void queryClient.invalidateQueries({ queryKey: entryKeys.all })
  }
}

export function useCreateCollection() {
  const invalidate = useCollectionInvalidation()
  // Wrapped rather than passed bare: react-query hands the mutation function a
  // context as its second argument, which the API call would forward as a
  // request config.
  return useMutation({
    mutationFn: (collection: Parameters<typeof createCollection>[0]) =>
      createCollection(collection),
    onSuccess: invalidate,
  })
}

export function useUpdateCollection(id: string) {
  const invalidate = useCollectionInvalidation()
  return useMutation({
    mutationFn: (update: Parameters<typeof updateCollection>[1]) => updateCollection(id, update),
    onSuccess: invalidate,
  })
}

export function useDeleteCollection() {
  const invalidate = useCollectionInvalidation()
  return useMutation({
    mutationFn: (id: string) => deleteCollection(id),
    onSuccess: invalidate,
  })
}

export function useUpdateCollectionAccess(id: string) {
  const invalidate = useCollectionInvalidation()
  return useMutation({
    mutationFn: (update: Parameters<typeof updateCollectionAccess>[1]) =>
      updateCollectionAccess(id, update),
    onSuccess: invalidate,
  })
}

/**
 * Takes the collection id per call rather than binding it, so the share picker
 * can choose a destination after the hook has been created.
 */
export function useCreateEntry() {
  const invalidate = useCollectionInvalidation()
  return useMutation({
    mutationFn: ({ listId, ...entry }: SaveEntry & { listId: string }) =>
      createEntry(listId, entry),
    onSuccess: invalidate,
  })
}

export function useUpdateEntry() {
  const invalidate = useCollectionInvalidation()
  return useMutation({
    mutationFn: ({ id, ...entry }: SaveEntry & { id: string }) => updateEntry(id, entry),
    onSuccess: invalidate,
  })
}

export function useDeleteEntry() {
  const invalidate = useCollectionInvalidation()
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => deleteEntry(id, version),
    onSuccess: invalidate,
  })
}

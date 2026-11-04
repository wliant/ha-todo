import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createList,
  deleteList,
  fetchList,
  fetchLists,
  fetchMembers,
  fetchListFields,
  fetchSidebar,
  setPinned,
  touchList,
  updateList,
  updateListAccess,
  updateListFields,
  type ListField,
  type ListFilters,
} from '../api/lists'

/**
 * A hierarchy, so prefix invalidation reaches everything list-shaped at once.
 * At household scale that is a handful of cheap requests, and the alternative -
 * a hand-maintained invalidation matrix - is where a stale sidebar after a
 * rename comes from.
 */
export const listKeys = {
  all: ['lists'] as const,
  sidebar: ['lists', 'sidebar'] as const,
  search: (filters: ListFilters) => ['lists', 'search', filters] as const,
  detail: (id: string) => ['lists', 'detail', id] as const,
  fields: (id: string) => ['lists', 'detail', id, 'fields'] as const,
}

export const membersKey = ['members'] as const

export function useLists(filters: ListFilters) {
  return useQuery({
    queryKey: listKeys.search(filters),
    queryFn: () => fetchLists(filters),
  })
}

export function useSidebar() {
  return useQuery({ queryKey: listKeys.sidebar, queryFn: fetchSidebar })
}

export function useList(id: string | undefined) {
  return useQuery({
    queryKey: listKeys.detail(id ?? 'none'),
    queryFn: () => fetchList(id!),
    enabled: Boolean(id),
  })
}

export function useListFields(id: string | undefined) {
  return useQuery({
    queryKey: listKeys.fields(id ?? 'none'),
    queryFn: () => fetchListFields(id!),
    enabled: Boolean(id),
  })
}

export function useMembers() {
  return useQuery({ queryKey: membersKey, queryFn: fetchMembers, staleTime: 60_000 })
}

function useListInvalidation() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: listKeys.all })
}

export function useCreateList() {
  const invalidate = useListInvalidation()
  // Wrapped rather than passed bare: react-query hands the mutation function a
  // context as its second argument, which the API call would forward as a
  // request config.
  return useMutation({
    mutationFn: (list: Parameters<typeof createList>[0]) => createList(list),
    onSuccess: () => void invalidate(),
  })
}

export function useUpdateList(id: string) {
  const invalidate = useListInvalidation()
  return useMutation({
    mutationFn: (update: Parameters<typeof updateList>[1]) => updateList(id, update),
    onSuccess: () => void invalidate(),
  })
}

export function useDeleteList() {
  const invalidate = useListInvalidation()
  return useMutation({
    mutationFn: (id: string) => deleteList(id),
    onSuccess: () => void invalidate(),
  })
}

export function useUpdateListAccess(id: string) {
  const invalidate = useListInvalidation()
  return useMutation({
    mutationFn: (update: Parameters<typeof updateListAccess>[1]) => updateListAccess(id, update),
    onSuccess: () => void invalidate(),
  })
}

/**
 * Takes the list id per call rather than binding it. Creating a list saves its
 * configuration in a second write, and the id does not exist until the first
 * one has returned - a hook bound at render time would still be holding the
 * empty string.
 */
export function useSaveListFields() {
  const invalidate = useListInvalidation()
  return useMutation({
    mutationFn: ({
      listId,
      ...update
    }: {
      listId: string
      version: number
      fields: ListField[]
    }) => updateListFields(listId, update),
    onSuccess: () => void invalidate(),
  })
}

/**
 * Optimistic, because a star must respond under the finger. The server's answer
 * still wins: a failure rolls the caches back to exactly what they held.
 */
export function usePinList() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, pinned }: { id: string; pinned: boolean }) => setPinned(id, pinned),
    onMutate: async ({ id, pinned }) => {
      await queryClient.cancelQueries({ queryKey: listKeys.all })
      const previous = queryClient.getQueriesData({ queryKey: listKeys.all })
      queryClient.setQueriesData<unknown>({ queryKey: listKeys.all }, (data: unknown) =>
        repin(data, id, pinned),
      )
      return previous
    },
    onError: (_error, _variables, previous) => {
      previous?.forEach(([key, data]: [readonly unknown[], unknown]) =>
        queryClient.setQueryData(key, data),
      )
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: listKeys.all }),
  })
}

/** Rewrites the pin flag wherever a list appears, whatever shape holds it. */
function repin(data: unknown, id: string, pinned: boolean): unknown {
  if (Array.isArray(data)) {
    return data.map((entry) => repin(entry, id, pinned))
  }
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>
    if ('pinned' in record && record.id === id) {
      return { ...record, pinned }
    }
    if ('pinned' in record && 'recent' in record) {
      return { pinned: repin(record.pinned, id, pinned), recent: repin(record.recent, id, pinned) }
    }
  }
  return data
}

/**
 * Records that the member opened a list, once per list per session.
 *
 * The guard is module-level rather than component state because
 * refetchOnWindowFocus would otherwise make every glance at another tab a
 * write. Cleared on sign-out, where the query cache is cleared too.
 */
const touched = new Set<string>()

export function clearTouchedLists(): void {
  touched.clear()
}

export function useTouchList() {
  const queryClient = useQueryClient()
  return (id: string) => {
    if (touched.has(id)) {
      return
    }
    touched.add(id)
    void touchList(id)
      .then(() => queryClient.invalidateQueries({ queryKey: listKeys.sidebar }))
      .catch(() => touched.delete(id))
  }
}

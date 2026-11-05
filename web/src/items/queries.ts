import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createItem,
  deleteItem,
  fetchItems,
  fetchPoints,
  setItemCompleted,
  updateItem,
  type Item,
  type ItemStatus,
  type SaveItem,
} from '../api/items'
import { conflictingItem } from '../api/items'
import { isConflict } from '../api/http'
import { listKeys } from '../lists/queries'

export const itemKeys = {
  all: (listId: string) => ['lists', 'detail', listId, 'items'] as const,
  filtered: (listId: string, status: ItemStatus) =>
    ['lists', 'detail', listId, 'items', status] as const,
  points: (listId: string) => ['lists', 'detail', listId, 'points'] as const,
}

export function useItems(listId: string | undefined, status: ItemStatus) {
  return useQuery({
    queryKey: itemKeys.filtered(listId ?? 'none', status),
    queryFn: () => fetchItems(listId!, status),
    enabled: Boolean(listId),
  })
}

export function usePoints(listId: string | undefined) {
  return useQuery({
    queryKey: itemKeys.points(listId ?? 'none'),
    queryFn: () => fetchPoints(listId!),
    enabled: Boolean(listId),
  })
}

/**
 * Everything that can change an item also changes the points, and the list
 * summary counts items - so one blunt invalidation rather than a matrix
 * nobody maintains.
 */
function useItemInvalidation(listId: string) {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: itemKeys.all(listId) })
    void queryClient.invalidateQueries({ queryKey: itemKeys.points(listId) })
    void queryClient.invalidateQueries({ queryKey: listKeys.all })
  }
}

export function useCreateItem(listId: string) {
  const invalidate = useItemInvalidation(listId)
  return useMutation({
    mutationFn: (item: SaveItem) => createItem(listId, item),
    onSuccess: invalidate,
  })
}

export function useUpdateItem(listId: string) {
  const invalidate = useItemInvalidation(listId)
  return useMutation({
    mutationFn: ({ id, ...item }: SaveItem & { id: string }) => updateItem(id, item),
    onSuccess: invalidate,
  })
}

export function useDeleteItem(listId: string) {
  const invalidate = useItemInvalidation(listId)
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => deleteItem(id, version),
    onSuccess: invalidate,
  })
}

/**
 * Ticking an item off.
 *
 * <p>Optimistic, because a checkbox has to move under the finger. When the
 * server says somebody got there first, the caches take the server's version
 * of the item - not the snapshot - because that is the truth the member needs
 * to see, and `onConflict` is how the screen tells them whose it was.
 */
export function useToggleCompletion(
  listId: string,
  onConflict: (message: string) => void,
) {
  const queryClient = useQueryClient()
  const invalidate = useItemInvalidation(listId)

  return useMutation({
    mutationFn: ({ item, completed }: { item: Item; completed: boolean }) =>
      setItemCompleted(item.id, item.version, completed),

    onMutate: async ({ item, completed }) => {
      await queryClient.cancelQueries({ queryKey: itemKeys.all(listId) })
      const previous = queryClient.getQueriesData({ queryKey: itemKeys.all(listId) })
      queryClient.setQueriesData<Item[]>({ queryKey: itemKeys.all(listId) }, (items) =>
        items?.map((candidate) =>
          candidate.id === item.id ? { ...candidate, completed } : candidate,
        ),
      )
      return previous
    },

    onError: (error, _variables, previous) => {
      const current = isConflict(error) ? conflictingItem(error) : null
      if (current) {
        // The server's state wins outright; nothing is retried behind the
        // member's back.
        queryClient.setQueriesData<Item[]>({ queryKey: itemKeys.all(listId) }, (items) =>
          items?.map((candidate) => (candidate.id === current.id ? current : candidate)),
        )
      } else {
        previous?.forEach(([key, data]: [readonly unknown[], unknown]) =>
          queryClient.setQueryData(key, data),
        )
      }
      if (isConflict(error)) {
        const body = (error as { response?: { data?: { error?: string } } }).response?.data
        onConflict(body?.error ?? 'That item changed while you were looking at it.')
      }
    },

    onSettled: invalidate,
  })
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  assignItems,
  createItem,
  fetchActivity,
  deleteItem,
  fetchItems,
  fetchPoints,
  setItemCompleted,
  updateItem,
  type AssignmentResult,
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
  activity: (listId: string, from: string, to: string) =>
    ['lists', 'detail', listId, 'activity', from, to] as const,
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
 * The last seven days on one list (R43).
 *
 * <p>Inside the item key hierarchy on purpose, so completing something
 * invalidates the feed along with everything else.
 */
export function useActivity(listId: string | undefined, from: string, to: string) {
  return useQuery({
    queryKey: itemKeys.activity(listId ?? 'none', from, to),
    queryFn: () => fetchActivity(listId!, from, to),
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
    mutationFn: ({
      item,
      completed,
      noteHtml,
    }: {
      item: Item
      completed: boolean
      noteHtml?: string | null
    }) => setItemCompleted(item.id, item.version, completed, noteHtml),

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

/**
 * Claiming several items at once (R46).
 *
 * <p>Not optimistic: a bulk write can half-succeed, and guessing which half
 * then has to be unguessed. Both halves of the server's answer are written into
 * the caches instead - the assigned items and the server's version of each
 * conflicted one - so a second attempt carries fresh versions for exactly the
 * items that need retrying.
 */
export function useAssignItems(listId: string) {
  const queryClient = useQueryClient()
  const invalidate = useItemInvalidation(listId)

  return useMutation({
    mutationFn: ({
      assigneeId,
      items,
    }: {
      assigneeId: string | null
      items: { id: string; version: number }[]
    }) => assignItems(listId, assigneeId, items),

    onSuccess: (result: AssignmentResult) => {
      const fresh = new Map<string, Item>()
      result.assigned.forEach((item) => fresh.set(item.id, item))
      result.conflicts.forEach((conflict) => {
        if (conflict.item) {
          fresh.set(conflict.item.id, conflict.item)
        }
      })
      queryClient.setQueriesData<Item[]>({ queryKey: itemKeys.all(listId) }, (items) =>
        items?.map((candidate) => fresh.get(candidate.id) ?? candidate),
      )
      invalidate()
    },
  })
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchOverdue,
  fetchScheduled,
  fetchUndated,
  type DashboardItem,
  type DashboardScope,
  type Overdue,
  type Scheduled,
  type Undated,
} from '../api/dashboard'
import {
  conflictingItem,
  createItem,
  setItemState,
  updateItem,
  type ItemState,
  type SaveItem,
} from '../api/items'
import { errorMessage, isConflict } from '../api/http'
import { listKeys } from '../lists/queries'

/**
 * Outside the per-list key hierarchy, because this reads across lists.
 *
 * <p>**It is no longer a read-only surface** (0010 R83): a completion can be
 * written from here, so a write on the agenda has to invalidate `['lists']` as
 * well as `['dashboard']`. Item writes invalidate `['lists']` wholesale and
 * that prefix does not reach these keys, and this prefix does not reach those -
 * the two trees are siblings, so anything that changes an item from here must
 * name both. The symptom of forgetting is a list screen that stays wrong until
 * it remounts. See adr/0039.
 */
export const dashboardKeys = {
  all: ['dashboard'] as const,
  scheduled: (from: string, to: string, scope: DashboardScope) =>
    ['dashboard', 'scheduled', from, to, scope] as const,
  undated: (scope: DashboardScope) => ['dashboard', 'undated', scope] as const,
  overdue: (scope: DashboardScope) => ['dashboard', 'overdue', scope] as const,
}

export function useScheduled(from: string, to: string, scope: DashboardScope) {
  return useQuery({
    queryKey: dashboardKeys.scheduled(from, to, scope),
    queryFn: () => fetchScheduled(from, to, scope),
  })
}

export function useUndated(scope: DashboardScope) {
  return useQuery({
    queryKey: dashboardKeys.undated(scope),
    queryFn: () => fetchUndated(scope),
  })
}

/**
 * What is late (0011 R88).
 *
 * <p>Keyed on the scope alone. Not on a window, not on the anchor: overdue is
 * reckoned from the clock, so every period shares one cache entry and
 * navigating cannot refetch it into a different answer (R89, adr/0040).
 */
export function useOverdue(scope: DashboardScope) {
  return useQuery({
    queryKey: dashboardKeys.overdue(scope),
    queryFn: () => fetchOverdue(scope),
  })
}

/**
 * Finishing a chore, or giving up on one, without leaving the agenda
 * (0010 R83-R87, 0015 R138).
 *
 * <p>The item's own endpoint, which checks the caller's grant and the version
 * it was drawn from; the agenda adds no authorization of its own and could not
 * be trusted with any (adr/0039).
 *
 * <p>Optimistic in one direction only, and that is what lets one mutation
 * serve both verbs: the agenda shows **outstanding** work, so a row that
 * reaches either terminal state simply leaves (R86, R136) and there is no
 * second state to guess. The three payload edits below are therefore identical
 * for a completion and for a lapse. A failure puts the snapshot back -
 * including the conflict, where the server's item has nowhere to be written to
 * here, so the refetch is what reconciles and the 409's prose is what the
 * member reads.
 *
 * <p>Reopening is deliberately absent: a chore in a terminal state is not on
 * the agenda to be reopened, and that record lives on the list.
 */
export function useSetStateFromAgenda(onProblem: (message: string) => void) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      item,
      state,
      noteHtml,
    }: {
      item: DashboardItem
      state: Extract<ItemState, 'COMPLETED' | 'LAPSED'>
      noteHtml?: string | null
    }) => setItemState(item.id, item.version, state, noteHtml),

    onMutate: async ({ item }) => {
      await queryClient.cancelQueries({ queryKey: dashboardKeys.all })
      const previous = queryClient.getQueriesData({ queryKey: dashboardKeys.all })
      queryClient.setQueriesData<Scheduled>(
        { queryKey: ['dashboard', 'scheduled'] },
        (data) =>
          data && { ...data, items: data.items.filter((row) => row.id !== item.id) },
      )
      // Both of the capped-page-plus-total payloads, which share a shape. The
      // overdue strip is the third payload this has to know about, and the one
      // whose absence would be invisible: `onSettled` invalidates
      // `['dashboard']` wholesale, so forgetting it leaves the row sitting
      // there for one round-trip and fails no test (adr/0040).
      queryClient.setQueriesData<Undated>(
        { queryKey: ['dashboard', 'undated'] },
        (data) => withoutItem(data, item.id),
      )
      queryClient.setQueriesData<Overdue>(
        { queryKey: ['dashboard', 'overdue'] },
        (data) => withoutItem(data, item.id),
      )
      return previous
    },

    onError: (error, _variables, previous) => {
      previous?.forEach(([key, data]: [readonly unknown[], unknown]) =>
        queryClient.setQueryData(key, data),
      )
      if (isConflict(error)) {
        const stale = conflictingItem(error)
        onProblem(
          errorMessage(
            error,
            stale
              ? 'That item changed while you were looking at it.'
              : 'That item is no longer there.',
          ),
        )
      } else {
        onProblem(errorMessage(error, 'Could not update that.'))
      }
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      // The other key tree. Forgetting this leaves the list the chore came from
      // showing it as open, with nothing to make it refetch.
      void queryClient.invalidateQueries({ queryKey: listKeys.all })
    },
  })
}

/**
 * One row out of a capped page, with the total corrected.
 *
 * <p>The total counts what the server holds rather than what is shown, so the
 * row that just left has to come off it too - otherwise "and 37 more" is
 * counting a chore that is done.
 */
function withoutItem<T extends { items: DashboardItem[]; total: number }>(
  data: T | undefined,
  itemId: string,
): T | undefined {
  if (!data) {
    return data
  }
  const kept = data.items.filter((row) => row.id !== itemId)
  const removed = data.items.length - kept.length
  return { ...data, items: kept, total: Math.max(kept.length, data.total - removed) }
}

/**
 * Adding an item to one of its lists, from the agenda (0018 R156).
 *
 * <p>Not optimistic: where a new chore lands depends on the date just typed
 * and on the window each cached payload asked for, so guessing which of them
 * it belongs in is work the refetch does correctly for free. A create has no
 * row to move under a finger either - the dialog is still open when it
 * returns.
 *
 * <p>**Both key trees**, as every write from here must: `['dashboard']` so the
 * grid shows the new chore, and `['lists']` so the list it was added to is not
 * left one item short with nothing to make it refetch. The two prefixes are
 * siblings and neither reaches the other (adr/0039).
 */
export function useCreateItemFromAgenda(listId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (item: SaveItem) => createItem(listId, item),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      void queryClient.invalidateQueries({ queryKey: listKeys.all })
    },
  })
}

/**
 * Changing an item from the agenda (0020 R173).
 *
 * <p>The item's own endpoint, which checks the caller's grant and the version
 * the form was drawn from; the agenda adds no authorization of its own and
 * could not be trusted with any (adr/0039).
 *
 * <p>Not optimistic, for `useCreateItemFromAgenda`'s reason and one more: an
 * edit can move the chore's date, so which cached payload it belongs in is
 * exactly what changed. Guessing is work the refetch does correctly for free,
 * and the dialog is still open when the server answers.
 *
 * <p>**Both key trees**, as every write from here must (adr/0039).
 */
export function useUpdateItemFromAgenda() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, ...item }: SaveItem & { id: string }) => updateItem(id, item),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      void queryClient.invalidateQueries({ queryKey: listKeys.all })
    },
  })
}

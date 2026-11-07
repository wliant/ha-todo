import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchScheduled,
  fetchUndated,
  type DashboardItem,
  type DashboardScope,
  type Scheduled,
  type Undated,
} from '../api/dashboard'
import { conflictingItem, setItemCompleted } from '../api/items'
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
 * Ticking a chore off without leaving the agenda (0010 R83-R87).
 *
 * <p>The item's own endpoint, which checks the caller's grant and the version
 * it was drawn from; the agenda adds no authorization of its own and could not
 * be trusted with any (adr/0039).
 *
 * <p>Optimistic in one direction only: the agenda shows outstanding work, so a
 * completed row simply leaves (R86) and there is no second state to guess. A
 * failure puts the snapshot back - including the conflict, where the server's
 * item has nowhere to be written to here, so the refetch is what reconciles and
 * the 409's prose is what the member reads.
 */
export function useCompleteFromAgenda(onProblem: (message: string) => void) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      item,
      noteHtml,
    }: {
      item: DashboardItem
      noteHtml?: string | null
    }) => setItemCompleted(item.id, item.version, true, noteHtml),

    onMutate: async ({ item }) => {
      await queryClient.cancelQueries({ queryKey: dashboardKeys.all })
      const previous = queryClient.getQueriesData({ queryKey: dashboardKeys.all })
      queryClient.setQueriesData<Scheduled>(
        { queryKey: ['dashboard', 'scheduled'] },
        (data) =>
          data && { ...data, items: data.items.filter((row) => row.id !== item.id) },
      )
      queryClient.setQueriesData<Undated>({ queryKey: ['dashboard', 'undated'] }, (data) => {
        if (!data) {
          return data
        }
        const kept = data.items.filter((row) => row.id !== item.id)
        // The total counts what the server holds rather than what is shown, so
        // the row that just left has to come off it too - otherwise "and 37
        // more" is counting a chore that is done.
        return { items: kept, total: Math.max(kept.length, data.total - (data.items.length - kept.length)) }
      })
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
        onProblem(errorMessage(error, 'Could not complete that.'))
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

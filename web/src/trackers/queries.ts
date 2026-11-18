import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { repin } from '../app/repin'
import {
  createTracker,
  deleteTracker,
  fetchPendingTrackers,
  fetchSeries,
  fetchTracker,
  fetchTrackerSidebar,
  fetchTrackers,
  setTrackerPinned,
  touchTracker,
  updateTracker,
  updateTrackerAccess,
  type SeriesWindow,
  type TrackerFilters,
} from '../api/trackers'
import {
  deleteReading,
  fetchReadings,
  recordReading,
  updateReading,
  type SaveReading,
} from '../api/trackerReadings'

/**
 * One hierarchy, so prefix invalidation reaches everything tracker-shaped at
 * once - the same convention `listKeys` and `collectionKeys` use.
 *
 * <p>There is deliberately no second tree: unlike entries, which are searched
 * across collections, nothing in this feature reads across trackers. `pending`
 * is the one cross-tracker read and it sits *inside* this hierarchy, because a
 * reading changes it and so does deleting a tracker.
 *
 * <p>**A tracker write must not invalidate `['lists']` or `['dashboard']`.**
 * `useCreateItemFromAgenda` trains exactly the opposite reflex, and it is
 * right for items: a chore belongs to a list and appears on the agenda. A
 * reading is not work and reaches neither.
 */
export const trackerKeys = {
  all: ['trackers'] as const,
  search: (filters: TrackerFilters) => ['trackers', 'search', filters] as const,
  detail: (id: string) => ['trackers', 'detail', id] as const,
  sidebar: ['trackers', 'sidebar'] as const,
  pending: ['trackers', 'pending'] as const,
  readings: (id: string) => ['trackers', 'detail', id, 'readings'] as const,
  series: (id: string, window: SeriesWindow) =>
    ['trackers', 'detail', id, 'series', window] as const,
}

export function useTrackers(filters: TrackerFilters) {
  return useQuery({
    queryKey: trackerKeys.search(filters),
    queryFn: () => fetchTrackers(filters),
  })
}

export function useTracker(id: string | undefined) {
  return useQuery({
    queryKey: trackerKeys.detail(id ?? 'none'),
    queryFn: () => fetchTracker(id!),
    enabled: Boolean(id),
  })
}

export function useReadings(id: string | undefined) {
  return useQuery({
    queryKey: trackerKeys.readings(id ?? 'none'),
    queryFn: () => fetchReadings(id!),
    enabled: Boolean(id),
  })
}

export function useSeries(id: string | undefined, window: SeriesWindow) {
  return useQuery({
    queryKey: trackerKeys.series(id ?? 'none', window),
    queryFn: () => fetchSeries(id!, window),
    enabled: Boolean(id),
  })
}

/** The agenda's nudge (R171). Its own key, so recording clears it at once. */
export function usePendingTrackers() {
  return useQuery({ queryKey: trackerKeys.pending, queryFn: fetchPendingTrackers })
}

/**
 * One blunt invalidation rather than a matrix nobody maintains: a reading
 * changes the series, the headline, the readings table, the card's count and
 * the nudge, and they are all under this prefix.
 */
function useTrackerInvalidation() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: trackerKeys.all })
}

export function useCreateTracker() {
  const invalidate = useTrackerInvalidation()
  // Wrapped rather than passed bare: react-query hands the mutation function a
  // context as its second argument, which the API call would forward as a
  // request config.
  return useMutation({
    mutationFn: (tracker: Parameters<typeof createTracker>[0]) => createTracker(tracker),
    onSuccess: () => void invalidate(),
  })
}

export function useUpdateTracker(id: string) {
  const invalidate = useTrackerInvalidation()
  return useMutation({
    mutationFn: (update: Parameters<typeof updateTracker>[1]) => updateTracker(id, update),
    onSuccess: () => void invalidate(),
  })
}

export function useDeleteTracker() {
  const invalidate = useTrackerInvalidation()
  return useMutation({
    mutationFn: (id: string) => deleteTracker(id),
    onSuccess: () => void invalidate(),
  })
}

export function useUpdateTrackerAccess(id: string) {
  const invalidate = useTrackerInvalidation()
  return useMutation({
    mutationFn: ({ version, memberIds }: { version: number; memberIds: string[] }) =>
      updateTrackerAccess(id, version, memberIds),
    onSuccess: () => void invalidate(),
  })
}

/**
 * Recording a number (R162).
 *
 * <p>Takes the tracker id per call rather than binding it, so the agenda's
 * nudge can record into whichever tracker the member picked without a hook per
 * row.
 *
 * <p>Not optimistic: where a number lands depends on the bucket the server
 * canonicalises it into and on how the rollup folds it with whatever is already
 * there, so guessing the resulting series is reimplementing the fold in the
 * browser. The dialog is still open when the answer arrives.
 */
export function useRecordReading() {
  const invalidate = useTrackerInvalidation()
  return useMutation({
    mutationFn: ({ trackerId, ...reading }: SaveReading & { trackerId: string }) =>
      recordReading(trackerId, reading),
    onSuccess: () => void invalidate(),
  })
}

export function useUpdateReading() {
  const invalidate = useTrackerInvalidation()
  return useMutation({
    mutationFn: ({ id, ...reading }: SaveReading & { id: string }) => updateReading(id, reading),
    onSuccess: () => void invalidate(),
  })
}

export function useDeleteReading() {
  const invalidate = useTrackerInvalidation()
  return useMutation({
    mutationFn: (id: string) => deleteReading(id),
    onSuccess: () => void invalidate(),
  })
}

/**
 * Optimistic, because a star must respond under the finger - the same shape
 * `usePinList` and `usePinCollection` have, against this key hierarchy. The
 * server's answer still wins: a failure rolls the caches back to exactly what
 * they held, and `onSettled` is what actually moves the row between the pinned
 * section and the group.
 */
export function usePinTracker() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, pinned }: { id: string; pinned: boolean }) =>
      setTrackerPinned(id, pinned),
    onMutate: async ({ id, pinned }) => {
      await queryClient.cancelQueries({ queryKey: trackerKeys.all })
      const previous = queryClient.getQueriesData({ queryKey: trackerKeys.all })
      queryClient.setQueriesData<unknown>({ queryKey: trackerKeys.all }, (data: unknown) =>
        repin(data, id, pinned),
      )
      return previous
    },
    onError: (_error, _variables, previous) => {
      previous?.forEach(([key, data]: [readonly unknown[], unknown]) =>
        queryClient.setQueryData(key, data),
      )
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: trackerKeys.all }),
  })
}

/** The sidebar's pinned trackers and the group below them (R170). */
export function useTrackerSidebar() {
  return useQuery({ queryKey: trackerKeys.sidebar, queryFn: fetchTrackerSidebar })
}

/**
 * Records that the member opened a tracker, once per tracker per session.
 *
 * <p>The guard is module-level rather than component state for the reason the
 * other two kinds' is: refetchOnWindowFocus would otherwise turn every glance
 * at another tab into a write. Cleared on sign-out, where the query cache is
 * cleared too.
 */
const touched = new Set<string>()

export function clearTouchedTrackers(): void {
  touched.clear()
}

export function useTouchTracker() {
  const queryClient = useQueryClient()
  return (id: string) => {
    if (touched.has(id)) {
      return
    }
    touched.add(id)
    void touchTracker(id)
      .then(() => queryClient.invalidateQueries({ queryKey: trackerKeys.sidebar }))
      .catch(() => touched.delete(id))
  }
}

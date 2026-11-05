import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchMyCalendars,
  previewCalendar,
  refreshCalendar,
  subscribeCalendar,
  unsubscribeCalendar,
  updateCalendar,
  type SaveCalendarFeed,
} from '../api/calendars'

export const calendarKeys = {
  mine: ['calendars', 'mine'] as const,
}

export function useMyCalendars() {
  return useQuery({ queryKey: calendarKeys.mine, queryFn: fetchMyCalendars })
}

/**
 * Every write invalidates the dashboard as well as the feed list: subscribing a
 * calendar, sharing one or dropping one changes what My day shows, and the
 * member is often looking at both in one session.
 */
function useCalendarInvalidation() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: calendarKeys.mine })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

/** Fetches and shows; saves nothing (R65). */
export function usePreviewCalendar() {
  return useMutation({ mutationFn: (url: string) => previewCalendar(url) })
}

export function useSubscribeCalendar() {
  const invalidate = useCalendarInvalidation()
  return useMutation({
    mutationFn: (request: SaveCalendarFeed) => subscribeCalendar(request),
    onSuccess: invalidate,
  })
}

export function useUpdateCalendar() {
  const invalidate = useCalendarInvalidation()
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: SaveCalendarFeed }) =>
      updateCalendar(id, request),
    onSuccess: invalidate,
  })
}

export function useRefreshCalendar() {
  const invalidate = useCalendarInvalidation()
  // Wrapped rather than passed bare: react-query hands the mutation function a
  // context as its second argument, which the API call would forward as a
  // request config.
  return useMutation({ mutationFn: (id: string) => refreshCalendar(id), onSuccess: invalidate })
}

export function useUnsubscribeCalendar() {
  const invalidate = useCalendarInvalidation()
  return useMutation({
    mutationFn: (id: string) => unsubscribeCalendar(id),
    onSuccess: invalidate,
  })
}

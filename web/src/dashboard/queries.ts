import { useQuery } from '@tanstack/react-query'
import {
  fetchScheduled,
  fetchUndated,
  type DashboardScope,
} from '../api/dashboard'

/**
 * Outside the per-list key hierarchy, because this reads across lists.
 *
 * <p>Item writes invalidate `['lists']` wholesale, which does not reach these -
 * but the dashboard is a read-only surface, and anything a member changes here
 * they change on the list itself, which remounts this on the way back. Refetch
 * on focus covers the rest.
 */
export const dashboardKeys = {
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

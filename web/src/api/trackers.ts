import { api } from './http'
import type { ListIcon } from './lists'

/**
 * Trackers (requirements/0019) - a number a household keeps, which is neither
 * work nor reference.
 *
 * <p>Its own module with its own DTOs, and no barrel re-exporting it: tests spy
 * by module namespace, and a re-exported live binding is exactly what
 * `vi.spyOn` cannot intercept.
 *
 * The icon type is shared with the other two kinds because it is one closed set
 * of drawings; nothing else here is.
 */

/** Which bucket a reading falls in, and which control the form shows. */
export type TrackerPeriod = 'DAY' | 'WEEK' | 'MONTH'

/** Whose numbers a tracker holds (R160). */
export type TrackerScope = 'PER_MEMBER' | 'SHARED'

/** How several readings in one bucket become one (R163). */
export type Rollup = 'SUM' | 'AVERAGE' | 'LATEST'

export type TargetDirection = 'AT_MOST' | 'AT_LEAST'

export interface TrackerMember {
  id: string
  displayName: string
  owner: boolean
  avatarImageId: string | null
}

export interface TrackerSummary {
  id: string
  name: string
  description: string | null
  icon: ListIcon
  unit: string
  period: TrackerPeriod
  scope: TrackerScope
  rollup: Rollup
  decimals: number
  target: number | null
  targetDirection: TargetDirection | null
  owned: boolean
  /** This member's own pin, not a property of the tracker. */
  pinned: boolean
  memberCount: number
  readingCount: number
}

export interface TrackerDetail extends Omit<TrackerSummary, 'memberCount' | 'readingCount'> {
  /**
   * Whether the period and the scope may still be changed (R161).
   *
   * <p>Computed by the server from whether any reading exists, so the settings
   * screen renders those two read-only rather than offering a control that
   * 409s. The client never recomputes it from a reading count.
   */
  reconfigurable: boolean
  /** Echoed back on the next write, so a stale editor is told rather than winning. */
  version: number
  members: TrackerMember[]
  createdAt: string
}

export type TrackerScopeFilter = 'all' | 'owned' | 'shared'

export interface TrackerFilters {
  q?: string
  icon?: ListIcon | ''
  scope?: TrackerScopeFilter
}

export interface NewTracker {
  name: string
  description?: string
  icon: ListIcon
  unit: string
  period: TrackerPeriod
  scope: TrackerScope
  rollup: Rollup
  decimals?: number
  target?: number | null
  targetDirection?: TargetDirection | null
  memberIds: string[]
}

export interface SaveTracker extends Omit<NewTracker, 'memberIds'> {
  version: number
}

/**
 * The sidebar's trackers.
 *
 * <p>The two arrays **partition** what the member can see - the group excludes
 * everything pinned - so nothing is drawn twice.
 */
export interface TrackerSidebar {
  pinned: TrackerSummary[]
  trackers: TrackerSummary[]
}

/** One line on the chart; `memberId` is null for a shared tracker's one series. */
export interface TrackerSeriesLine {
  memberId: string | null
  displayName: string
  avatarImageId: string | null
  /** One entry per bucket, aligned with `buckets`. Null is a gap, never zero. */
  values: (number | null)[]
}

/**
 * What the chart reads (R167).
 *
 * <p>`buckets` is a dense axis the server computed, so nothing here iterates
 * dates looking for holes - and a bucket nobody recorded is `null` in every
 * line rather than 0, because a missed weigh-in is not 0 kg.
 */
export interface TrackerSeries {
  trackerId: string
  unit: string
  period: TrackerPeriod
  rollup: Rollup
  scope: TrackerScope
  decimals: number
  target: number | null
  targetDirection: TargetDirection | null
  /** The grain actually drawn, which may be coarser than `period`. */
  by: TrackerPeriod
  from: string
  to: string
  /** Household dates, `YYYY-MM-DD`. Never instants (adr/0052). */
  buckets: string[]
  series: TrackerSeriesLine[]
}

/** A daily tracker with nothing recorded today (R171). */
export interface PendingTracker {
  id: string
  name: string
  icon: ListIcon
  unit: string
  decimals: number
  /** The bucket the nudge is asking about, so the form never asks the device. */
  periodStart: string
}

export interface SeriesWindow {
  from?: string
  to?: string
  by?: TrackerPeriod
}

export async function fetchTrackers(filters: TrackerFilters = {}): Promise<TrackerSummary[]> {
  const { data } = await api.get<TrackerSummary[]>('/trackers', {
    params: {
      q: filters.q || undefined,
      icon: filters.icon || undefined,
      scope: filters.scope && filters.scope !== 'all' ? filters.scope : undefined,
    },
  })
  return data
}

export async function fetchTrackerSidebar(): Promise<TrackerSidebar> {
  const { data } = await api.get<TrackerSidebar>('/trackers/sidebar')
  return data
}

export async function fetchTracker(id: string): Promise<TrackerDetail> {
  const { data } = await api.get<TrackerDetail>(`/trackers/${id}`)
  return data
}

export async function createTracker(tracker: NewTracker): Promise<TrackerDetail> {
  const { data } = await api.post<TrackerDetail>('/trackers', tracker)
  return data
}

export async function updateTracker(id: string, tracker: SaveTracker): Promise<TrackerDetail> {
  const { data } = await api.patch<TrackerDetail>(`/trackers/${id}`, tracker)
  return data
}

export async function deleteTracker(id: string): Promise<void> {
  await api.delete(`/trackers/${id}`)
}

export async function fetchTrackerMembers(id: string): Promise<TrackerMember[]> {
  const { data } = await api.get<TrackerMember[]>(`/trackers/${id}/access`)
  return data
}

export async function updateTrackerAccess(
  id: string,
  version: number,
  memberIds: string[],
): Promise<TrackerMember[]> {
  const { data } = await api.put<TrackerMember[]>(`/trackers/${id}/access`, {
    version,
    memberIds,
  })
  return data
}

export async function fetchSeries(id: string, window: SeriesWindow): Promise<TrackerSeries> {
  const { data } = await api.get<TrackerSeries>(`/trackers/${id}/series`, { params: window })
  return data
}

export async function fetchPendingTrackers(): Promise<PendingTracker[]> {
  const { data } = await api.get<PendingTracker[]>('/trackers/pending')
  return data
}

export async function setTrackerPinned(id: string, pinned: boolean): Promise<void> {
  if (pinned) {
    await api.put(`/trackers/${id}/pin`)
  } else {
    await api.delete(`/trackers/${id}/pin`)
  }
}

export async function touchTracker(id: string): Promise<void> {
  await api.post(`/trackers/${id}/touch`)
}

import { api } from './http'
import type { ItemCostValue, ItemMember } from './items'

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY'

/** As the API sends them: the java.time.DayOfWeek names. */
export type Weekday =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export const WEEKDAYS: { value: Weekday; short: string }[] = [
  { value: 'MONDAY', short: 'Mon' },
  { value: 'TUESDAY', short: 'Tue' },
  { value: 'WEDNESDAY', short: 'Wed' },
  { value: 'THURSDAY', short: 'Thu' },
  { value: 'FRIDAY', short: 'Fri' },
  { value: 'SATURDAY', short: 'Sat' },
  { value: 'SUNDAY', short: 'Sun' },
]

export interface RecurrenceRule {
  id: string
  listId: string
  description: string
  detailHtml: string | null
  assignee: ItemMember | null
  selfAssigned: boolean
  frequency: Frequency
  intervalCount: number
  daysOfWeek: Weekday[]
  dayOfMonth: number | null
  /** A wall clock in the household's zone, "HH:mm[:ss]", or null. */
  dueTime: string | null
  leadDays: number
  startsOn: string
  endsOn: string | null
  paused: boolean
  lastMaterialisedOn: string | null
  lastRunAt: string | null
  /** Why the last run produced nothing, so a broken rule can say so. */
  lastError: string | null
  costValues: ItemCostValue[]
  version: number
}

export interface SaveRecurrenceRule {
  version?: number
  description: string
  detailHtml?: string | null
  assigneeId?: string | null
  frequency: Frequency
  intervalCount: number
  daysOfWeek: Weekday[]
  dayOfMonth?: number | null
  dueTime?: string | null
  leadDays: number
  startsOn?: string | null
  endsOn?: string | null
  paused: boolean
  costValues: ItemCostValue[]
}

export interface RecurrenceRun {
  rulesScanned: number
  generated: number
  skipped: number
  failed: number
}

export async function fetchRules(listId: string): Promise<RecurrenceRule[]> {
  const { data } = await api.get<RecurrenceRule[]>(`/lists/${listId}/recurrence`)
  return data
}

export async function createRule(
  listId: string,
  rule: SaveRecurrenceRule,
): Promise<RecurrenceRule> {
  const { data } = await api.post<RecurrenceRule>(`/lists/${listId}/recurrence`, rule)
  return data
}

export async function updateRule(
  id: string,
  rule: SaveRecurrenceRule,
): Promise<RecurrenceRule> {
  const { data } = await api.patch<RecurrenceRule>(`/recurrence/${id}`, rule)
  return data
}

export async function deleteRule(id: string, version: number): Promise<void> {
  await api.delete(`/recurrence/${id}`, { params: { version } })
}

/**
 * Running this list's rules now.
 *
 * <p>The answer to "it didn't appear" that a member can reach themselves,
 * rather than waiting for tonight.
 */
export async function runRules(listId: string): Promise<RecurrenceRun> {
  const { data } = await api.post<RecurrenceRun>(`/lists/${listId}/recurrence/runs`)
  return data
}

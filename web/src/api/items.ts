import { api } from './http'

export interface ItemMember {
  id: string
  displayName: string
  avatarImageId: string | null
}

export interface ItemCostValue {
  fieldId: string
  /** Set for a FIXED cost; null for a NUMERIC one. */
  choiceId: string | null
  amount: number | null
}

/**
 * What an item currently is (0015 R131).
 *
 * <p>Replaced `completed: boolean` rather than joining it, so every reader had
 * to be revisited: a lapsed item is not completed and is not open either, and
 * a boolean has nowhere to put the third answer. See adr/0048.
 */
export type ItemState = 'OPEN' | 'COMPLETED' | 'LAPSED'

export interface Item {
  id: string
  listId: string
  description: string
  detailHtml: string | null
  assignee: ItemMember | null
  /** The assignee put themselves there, so everyone sees it as claimed. */
  selfAssigned: boolean
  dueAt: string | null
  state: ItemState
  completedBy: ItemMember | null
  completedAt: string | null
  /** How the work went. Only returned while the completion stands. */
  completionNoteHtml: string | null
  /** Who gave up on it. Only returned while the lapse stands. */
  lapsedBy: ItemMember | null
  lapsedAt: string | null
  /** Set once an item has been reopened; both records are kept. */
  reopenedAt: string | null
  /** Null while open; snapshotted at completion. */
  pointsEarned: number | null
  costValues: ItemCostValue[]
  /** Echoed back on the next write, so a stale editor is told. */
  version: number
}

export interface SaveItem {
  version?: number
  description: string
  detailHtml?: string | null
  assigneeId?: string | null
  dueAt?: string | null
  costValues: ItemCostValue[]
}

export interface MemberPoints {
  userId: string
  displayName: string
  points: number
}

/**
 * One list, whole.
 *
 * <p>No status argument, and deliberately no `ItemStatus` type to pass one:
 * narrowing a list by status, assignee, due date or cost happens in the
 * browser over this payload (0013, adr/0044), so there is one implementation
 * of each predicate and the cache holds one array per list.
 */
export async function fetchItems(listId: string): Promise<Item[]> {
  const { data } = await api.get<Item[]>(`/lists/${listId}/items`)
  return data
}

export async function createItem(listId: string, item: SaveItem): Promise<Item> {
  const { data } = await api.post<Item>(`/lists/${listId}/items`, item)
  return data
}

export async function updateItem(id: string, item: SaveItem): Promise<Item> {
  const { data } = await api.patch<Item>(`/items/${id}`, item)
  return data
}

export async function deleteItem(id: string, version: number): Promise<void> {
  await api.delete(`/items/${id}`, { params: { version } })
}

/**
 * Completing, lapsing or reopening - one endpoint, because they are one state
 * machine (0015 R131).
 *
 * <p>The note rides the completion rather than taking a write of its own, so
 * one gesture is one request; sending a note with any other state is a 400,
 * not a silent drop, which is why it is dropped here rather than sent and
 * refused.
 */
export async function setItemState(
  id: string,
  version: number,
  state: ItemState,
  noteHtml?: string | null,
): Promise<Item> {
  const { data } = await api.put<Item>(`/items/${id}/state`, {
    version,
    state,
    noteHtml: state === 'COMPLETED' ? (noteHtml ?? null) : null,
  })
  return data
}

export async function fetchPoints(listId: string): Promise<MemberPoints[]> {
  const { data } = await api.get<MemberPoints[]>(`/lists/${listId}/points`)
  return data
}

/**
 * The item as the server says it is, carried on a 409 so a stale client can
 * reconcile without a second request. Absent when it has since been deleted.
 */
export function conflictingItem(error: unknown): Item | null {
  const body = (error as { response?: { data?: { item?: Item } } })?.response?.data
  return body?.item ?? null
}

export interface AssignmentConflict {
  id: string
  error: string
  /** Absent when the item is no longer there at all. */
  item: Item | null
}

export interface AssignmentResult {
  assigned: Item[]
  conflicts: AssignmentConflict[]
}

/**
 * Assigning several of one list's items at once (R46).
 *
 * <p>Partial success, always 200: one item touched by somebody else must not
 * discard the member's unambiguous intent for the rest. The conflicts carry the
 * server's version of each item, so a second attempt retries exactly those.
 */
export async function assignItems(
  listId: string,
  assigneeId: string | null,
  items: { id: string; version: number }[],
): Promise<AssignmentResult> {
  const { data } = await api.post<AssignmentResult>(`/lists/${listId}/items/assignment`, {
    assigneeId,
    items,
  })
  return data
}

export interface ActivityMember {
  userId: string
  displayName: string
  avatarImageId: string | null
  completed: number
  /** May be lower than `completed`: a reopened completion gave its points back. */
  points: number
}

export interface ActivityEvent {
  itemId: string
  description: string
  completedAt: string
  completedBy: ItemMember | null
  pointsEarned: number | null
  /** The work was later undone, which is worth seeing rather than dropping. */
  reopened: boolean
}

export interface Activity {
  from: string
  to: string
  members: ActivityMember[]
  events: ActivityEvent[]
}

/**
 * Who did what on this list, lately (R43).
 *
 * <p>The window is sent as instants the client computed, because only the
 * browser knows where this member's midnight is.
 */
export async function fetchActivity(
  listId: string,
  from: string,
  to: string,
): Promise<Activity> {
  const { data } = await api.get<Activity>(`/lists/${listId}/activity`, {
    params: { from, to },
  })
  return data
}

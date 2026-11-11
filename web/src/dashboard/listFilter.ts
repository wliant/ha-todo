import type { ListSummary } from '../api/lists'
import type { DashboardItem } from '../api/dashboard'

/** The one param this feature owns (adr/0047, following adr/0045). */
const PARAM = 'lists'

/**
 * Which lists the agenda is narrowed to (0014 R123).
 *
 * <p>Pure, and deliberately not a hook: everything here is a function of the
 * address and the payload already in hand. The agenda filters in the browser
 * over the fetch it was already making, so nothing in this file costs a
 * request (adr/0047).
 *
 * <p>**Empty means every list.** An empty set is an absent filter, the same
 * rule the list's bar follows everywhere (adr/0046) - an agenda that silently
 * shows nothing because a selection emptied itself is a bug nobody can
 * diagnose.
 */
export function parseLists(params: URLSearchParams): string[] {
  const raw = params.get(PARAM)
  if (!raw) {
    return []
  }
  // Never throws and never blocks (R128, adr/0045). Ids are not checked
  // against the member's lists here: those have not loaded yet, and rewriting
  // the address while a query is in flight is the worse bug. `known` does that
  // once there is something to check against.
  const seen = new Set<string>()
  for (const token of raw.split(',')) {
    const id = token.trim()
    if (id) {
      seen.add(id)
    }
  }
  return [...seen]
}

/**
 * The address with this selection on it, default omitted.
 *
 * <p>Clones rather than building a fresh `URLSearchParams` the way
 * `items/filters.ts#writeFilter` does: the agenda's address also carries `on`,
 * `scope` and `item`, and a fresh one would quietly drop the period somebody
 * had navigated to.
 */
export function writeLists(params: URLSearchParams, ids: string[]): URLSearchParams {
  const next = new URLSearchParams(params)
  if (ids.length === 0) {
    next.delete(PARAM)
  } else {
    next.set(PARAM, ids.join(','))
  }
  return next
}

/** The rows from the chosen lists, or every row when nothing is chosen. */
export function onlyLists(items: DashboardItem[], ids: string[]): DashboardItem[] {
  if (ids.length === 0) {
    return items
  }
  const wanted = new Set(ids)
  return items.filter((item) => wanted.has(item.listId))
}

/**
 * The selection, less the lists the member cannot see (R128).
 *
 * <p>A link outliving a deleted list, or one whose grant was revoked, degrades
 * to the **unfiltered** agenda rather than to an empty one: dropping the
 * unknown ids can empty the selection, and an empty selection is no filter.
 *
 * <p>Until the lists load there is nothing to check against, so the raw
 * selection stands - it came from this member's own session a moment ago.
 */
export function known(ids: string[], lists: ListSummary[] | undefined): string[] {
  if (!lists) {
    return ids
  }
  const visible = new Set(lists.map((list) => list.id))
  return ids.filter((id) => visible.has(id))
}

/**
 * What the chip says.
 *
 * <p>The list's own name at one selection rather than "Lists: 1", because the
 * label is what tells a member *which* list they are looking at - the colour
 * is never the only cue (R129).
 */
export function listFilterLabel(ids: string[], lists: ListSummary[] | undefined): string {
  if (ids.length === 0) {
    return 'Lists'
  }
  if (ids.length === 1) {
    const only = lists?.find((list) => list.id === ids[0])
    if (only) {
      return `Lists: ${only.name}`
    }
  }
  return `Lists: ${ids.length}`
}

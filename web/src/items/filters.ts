/**
 * Narrowing and ordering one list (0013 R107-R122).
 *
 * <p>Pure: the filter's type, its URL codec, and the predicate and comparator
 * that apply it. No React, no fetching, and no clock of its own - today is
 * always an argument, and a {@link Civil} one, so the only place a real instant
 * becomes a household day is still `calendar.ts`'s `now`. Same rule that module
 * states for itself, and for the same reason: a date read during render is a
 * value that moves under its own refetch.
 *
 * <p>This is where the whole feature lives, because adr/0044 decided it runs
 * in the browser over one unfiltered fetch of the list rather than in SQL.
 * Days are the household's days throughout (adr/0036): every function that
 * compares a date takes a {@link Zone} and none of them reads the device.
 */
import type { Item } from '../api/items'
import type { ListField } from '../api/lists'
import {
  addDays,
  civilIn,
  dayKey,
  startOfDay,
  type Civil,
  type Zone,
} from '../dashboard/calendar'

/**
 * Work nobody has taken (R109).
 *
 * <p>One constant used both as the URL token and as the selected id, so the
 * two spellings cannot drift apart. No member id can collide with it: those
 * are UUIDs.
 */
export const UNASSIGNED = 'none'

export type StatusValue = 'open' | 'done'

/** Canonical order, so a serialised filter round-trips to itself. */
const STATUS_ORDER: StatusValue[] = ['open', 'done']

export type DuePreset = 'overdue' | 'today' | 'week' | 'none'

const DUE_PRESETS: DuePreset[] = ['overdue', 'today', 'week', 'none']

export type DueFilter = { kind: DuePreset } | { kind: 'range'; from: string; to: string }

/**
 * One cost field's filter. A FIXED cost uses `choiceIds`, a NUMERIC one
 * `min`/`max`, and both may ask for the items that answered neither (R111).
 */
export interface CostFilter {
  choiceIds: string[]
  min: number | null
  max: number | null
  unanswered: boolean
}

export type SortKey =
  | 'manual'
  | 'due'
  | 'description'
  | 'status'
  | 'assignee'
  | `cost:${string}`

export interface ItemFilter {
  /**
   * Which statuses to show. Never empty in anything this module produces -
   * unticking the last chip means "show everything", which is spelled as both
   * values rather than as none (adr/0046). A parsed empty set still matches
   * everything, because a hand-edited address must not be a blank list.
   */
  status: StatusValue[]
  /** Member ids, possibly including {@link UNASSIGNED}. Empty means no filter. */
  assigneeIds: string[]
  due: DueFilter | null
  /** Keyed by list field id. A field with no entry here is not being filtered. */
  costs: Record<string, CostFilter>
  sort: SortKey
  descending: boolean
}

/** To do, manual order, nothing else - what a list shows today (R108, R113). */
export const DEFAULT_FILTER: ItemFilter = {
  status: ['open'],
  assigneeIds: [],
  due: null,
  costs: {},
  sort: 'manual',
  descending: false,
}

/** What the bar needs to validate a filter against: this list, as configured. */
export interface ListShape {
  fields: ListField[]
  memberIds: string[]
}

export function emptyCostFilter(): CostFilter {
  return { choiceIds: [], min: null, max: null, unanswered: false }
}

export function isCostFilterActive(filter: CostFilter): boolean {
  return (
    filter.choiceIds.length > 0 ||
    filter.min !== null ||
    filter.max !== null ||
    filter.unanswered
  )
}

/**
 * How many filters are narrowing the list (R107).
 *
 * <p>Measured against the default rather than against "nothing", so the view a
 * member has not touched reports none - and the sort is not counted, because
 * ordering a list does not hide anything from it. One function, so the bar's
 * count and R117's empty state cannot disagree about whether a filter is on.
 */
export function countActive(filter: ItemFilter): number {
  let count = 0
  if (isStatusActive(filter)) {
    count += 1
  }
  if (filter.assigneeIds.length > 0) {
    count += 1
  }
  if (filter.due) {
    count += 1
  }
  count += Object.values(filter.costs).filter(isCostFilterActive).length
  return count
}

/**
 * Whether the status chip is narrowing the list.
 *
 * <p>Measured against the default, not against "one value selected": showing
 * only Done is as much a filter as showing both, and the chip's fill has to
 * agree with the count beside it (R107, R108).
 */
export function isStatusActive(filter: ItemFilter): boolean {
  return !sameStatus(filter.status, DEFAULT_FILTER.status)
}

export function isFiltered(filter: ItemFilter): boolean {
  return countActive(filter) > 0
}

function sameStatus(left: StatusValue[], right: StatusValue[]): boolean {
  return left.length === right.length && left.every((value) => right.includes(value))
}

/**
 * Turning a status chip on or off.
 *
 * <p>Deselecting the last one yields both rather than none: an empty status
 * filter would show an empty list for a reason the member cannot see, and
 * "everything" is what they meant (adr/0046).
 */
export function toggleStatus(current: StatusValue[], value: StatusValue): StatusValue[] {
  const next = current.includes(value)
    ? current.filter((entry) => entry !== value)
    : [...current, value]
  return next.length === 0 ? [...STATUS_ORDER] : canonicalStatus(next)
}

function canonicalStatus(values: StatusValue[]): StatusValue[] {
  return STATUS_ORDER.filter((value) => values.includes(value))
}

/** Adding or removing one value from a multi-select, order preserved. */
export function toggleIn(current: string[], value: string): string[] {
  return current.includes(value)
    ? current.filter((entry) => entry !== value)
    : [...current, value]
}

// --- the address ------------------------------------------------------------

/**
 * Reading a filter out of the address (R115, adr/0045).
 *
 * <p>Never throws, and every value is validated **independently**: a malformed
 * `due` must not take the status filter with it, and a `cost.<id>` naming a
 * field this list no longer declares is dropped here rather than becoming a
 * control the bar cannot draw. The worst case is the default view, which is
 * R116 - this route is reached from a bookmark, a share and the sidebar, and a
 * mangled address must never be a member who cannot get in.
 */
export function parseFilter(params: URLSearchParams, shape: ListShape): ItemFilter {
  return {
    status: parseStatus(params.get('status')),
    assigneeIds: parseAssignees(params.get('who'), shape),
    due: parseDue(params.get('due')),
    costs: parseCosts(params, shape),
    sort: parseSort(params.get('sort'), shape),
    descending: params.get('dir') === 'desc',
  }
}

function parseStatus(raw: string | null): StatusValue[] {
  if (raw === null) {
    return DEFAULT_FILTER.status
  }
  const values = tokens(raw).filter((token): token is StatusValue =>
    STATUS_ORDER.includes(token as StatusValue),
  )
  return values.length === 0 ? DEFAULT_FILTER.status : canonicalStatus(values)
}

function parseAssignees(raw: string | null, shape: ListShape): string[] {
  if (raw === null || !declares(shape.fields, 'ASSIGNEE')) {
    return []
  }
  // An id nothing matches is dropped rather than left to filter everything
  // out: a revoked grant, or a member deleted by the admin, should open the
  // list rather than an empty one.
  return tokens(raw).filter(
    (token) => token === UNASSIGNED || shape.memberIds.includes(token),
  )
}

function parseDue(raw: string | null): DueFilter | null {
  if (raw === null) {
    return null
  }
  if (DUE_PRESETS.includes(raw as DuePreset)) {
    return { kind: raw as DuePreset }
  }
  const [from, to] = raw.split(':')
  return isDay(from) && isDay(to) ? { kind: 'range', from, to } : null
}

function parseCosts(params: URLSearchParams, shape: ListShape): Record<string, CostFilter> {
  const costs: Record<string, CostFilter> = {}
  for (const field of shape.fields) {
    if (field.kind !== 'COST' || !field.id) {
      continue
    }
    const raw = params.get(costParam(field.id))
    if (raw === null) {
      continue
    }
    const filter = emptyCostFilter()
    for (const token of tokens(raw)) {
      if (token === UNASSIGNED) {
        filter.unanswered = true
      } else if (field.costMode === 'NUMERIC' && token.includes(':')) {
        const [min, max] = token.split(':')
        filter.min = asNumber(min)
        filter.max = asNumber(max)
      } else if (field.costMode === 'FIXED') {
        // Validated against the field's own choices, so a choice the owner has
        // since deleted cannot hide every item on the list.
        if (field.choices.some((choice) => choice.id === token)) {
          filter.choiceIds.push(token)
        }
      }
    }
    if (isCostFilterActive(filter)) {
      costs[field.id] = filter
    }
  }
  return costs
}

function parseSort(raw: string | null, shape: ListShape): SortKey {
  if (raw === null) {
    return DEFAULT_FILTER.sort
  }
  if (raw === 'manual' || raw === 'due' || raw === 'description' || raw === 'status') {
    return raw
  }
  if (raw === 'assignee') {
    return declares(shape.fields, 'ASSIGNEE') ? 'assignee' : DEFAULT_FILTER.sort
  }
  if (raw.startsWith('cost:')) {
    const id = raw.slice('cost:'.length)
    const known = shape.fields.some((field) => field.kind === 'COST' && field.id === id)
    return known ? (`cost:${id}` as SortKey) : DEFAULT_FILTER.sort
  }
  return DEFAULT_FILTER.sort
}

/**
 * Writing a filter back into the address.
 *
 * <p>The default view writes **nothing** (R115), so an untouched list has a
 * clean URL - the same rule `?on=` follows for the agenda's period
 * (adr/0037).
 */
export function writeFilter(filter: ItemFilter): URLSearchParams {
  const params = new URLSearchParams()
  if (!sameStatus(filter.status, DEFAULT_FILTER.status)) {
    params.set('status', canonicalStatus(filter.status).join(','))
  }
  if (filter.assigneeIds.length > 0) {
    params.set('who', filter.assigneeIds.join(','))
  }
  if (filter.due) {
    params.set(
      'due',
      filter.due.kind === 'range' ? `${filter.due.from}:${filter.due.to}` : filter.due.kind,
    )
  }
  for (const [fieldId, cost] of Object.entries(filter.costs)) {
    if (!isCostFilterActive(cost)) {
      continue
    }
    const parts = [...cost.choiceIds]
    if (cost.min !== null || cost.max !== null) {
      parts.push(`${cost.min ?? ''}:${cost.max ?? ''}`)
    }
    if (cost.unanswered) {
      parts.push(UNASSIGNED)
    }
    params.set(costParam(fieldId), parts.join(','))
  }
  if (filter.sort !== DEFAULT_FILTER.sort) {
    params.set('sort', filter.sort)
  }
  if (filter.descending) {
    params.set('dir', 'desc')
  }
  return params
}

function costParam(fieldId: string): string {
  return `cost.${fieldId}`
}

function tokens(raw: string): string[] {
  return raw
    .split(',')
    .map((token) => token.trim())
    .filter((token) => token.length > 0)
}

function asNumber(raw: string): number | null {
  if (raw.trim() === '') {
    return null
  }
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

function isDay(raw: string | undefined): raw is string {
  return Boolean(raw) && /^\d{4}-\d{2}-\d{2}$/.test(raw!) && !Number.isNaN(Date.parse(raw!))
}

function declares(fields: ListField[], kind: ListField['kind']): boolean {
  return fields.some((field) => field.kind === kind)
}

// --- applying it ------------------------------------------------------------

/**
 * Whether one item survives the filter.
 *
 * <p>Exported because `ItemsSection` asks it of an item it is about to
 * complete: when the answer changes to false the row is about to vanish, and
 * R119 says that cannot be the only feedback.
 */
export function matchesFilter(
  item: Item,
  filter: ItemFilter,
  fields: ListField[],
  zone: Zone,
  today: Civil,
): boolean {
  if (filter.status.length > 0 && filter.status.length < STATUS_ORDER.length) {
    const wanted = filter.status[0] === 'done'
    if (item.completed !== wanted) {
      return false
    }
  }
  if (filter.assigneeIds.length > 0) {
    const id = item.assignee?.id ?? UNASSIGNED
    if (!filter.assigneeIds.includes(id)) {
      return false
    }
  }
  if (filter.due && !matchesDue(item, filter.due, zone, today)) {
    return false
  }
  for (const [fieldId, cost] of Object.entries(filter.costs)) {
    if (!matchesCost(item, fieldId, cost, fields)) {
      return false
    }
  }
  return true
}

/**
 * Day-granular, in the household's zone (R112).
 *
 * <p>Days rather than instants for the reason adr/0040 gives for the overdue
 * strip: a chore due at 09:00 belongs to today until midnight, not until
 * 09:01, and a member filtering "today" at lunchtime must still see it.
 */
function matchesDue(item: Item, due: DueFilter, zone: Zone, today: Civil): boolean {
  if (due.kind === 'none') {
    return item.dueAt === null
  }
  if (item.dueAt === null) {
    return false
  }
  const day = dayKey(startOfDay(civilIn(new Date(item.dueAt), zone)))
  const start = startOfDay(today)
  if (due.kind === 'overdue') {
    return day < dayKey(start)
  }
  if (due.kind === 'today') {
    return day === dayKey(start)
  }
  if (due.kind === 'range') {
    // Both ends inclusive: a member picking the same day twice means that day.
    return day >= due.from && day <= due.to
  }
  return day >= dayKey(start) && day <= dayKey(addDays(start, 6))
}

function matchesCost(
  item: Item,
  fieldId: string,
  cost: CostFilter,
  fields: ListField[],
): boolean {
  const value = item.costValues.find((entry) => entry.fieldId === fieldId)
  const answered = Boolean(value && (value.choiceId !== null || value.amount !== null))
  if (!answered) {
    // A blank is not a small amount, and "5 min" is not a request for
    // "unknown" (R111): an unanswered item is in only when asked for.
    return cost.unanswered
  }
  const field = fields.find((candidate) => candidate.id === fieldId)
  if (field?.costMode === 'NUMERIC') {
    const amount = value!.amount
    if (amount === null) {
      return cost.unanswered
    }
    if (cost.min === null && cost.max === null) {
      // The filter is active, and the only thing selected is "not answered".
      return false
    }
    return (
      (cost.min === null || amount >= cost.min) && (cost.max === null || amount <= cost.max)
    )
  }
  if (cost.choiceIds.length === 0) {
    // As above: only "not answered" is selected, and this item answered.
    return false
  }
  return value!.choiceId !== null && cost.choiceIds.includes(value!.choiceId)
}

/**
 * The items to draw, filtered and ordered (R113, R114).
 *
 * <p>The sort is **stable by construction**: every comparison falls back to the
 * item's manual `position`, and that fallback is never reversed - so equal keys
 * keep the order the member arranged, and a re-render cannot reshuffle rows.
 *
 * <p>Blanks sort last in **both** directions. The null handling lives here, in
 * one place, rather than in each key's comparator: "the one key that forgot" is
 * the whole failure mode, and reversing the direction is exactly when a crowd
 * of undated items would otherwise arrive at the top.
 */
export function visibleItems(
  items: Item[],
  filter: ItemFilter,
  fields: ListField[],
  zone: Zone,
  today: Civil,
): Item[] {
  const direction = filter.descending ? -1 : 1
  // Manual order is the order the server sent, which is `todo_item.position`
  // ascending. The column itself is not on the wire, so the index stands in
  // for it - captured before the sort, because `indexOf` on the array being
  // sorted would be measuring the sort's own progress.
  const manual = new Map(items.map((item, index) => [item.id, index]))
  const order = (item: Item) => manual.get(item.id) ?? 0
  return items
    .filter((item) => matchesFilter(item, filter, fields, zone, today))
    .slice()
    .sort((left, right) => {
      const a = sortValue(left, filter.sort, fields, order)
      const b = sortValue(right, filter.sort, fields, order)
      if (a === null || b === null) {
        if (a === b) {
          return order(left) - order(right)
        }
        return a === null ? 1 : -1
      }
      const primary =
        typeof a === 'number' && typeof b === 'number'
          ? a - b
          : String(a).localeCompare(String(b))
      return primary !== 0 ? primary * direction : order(left) - order(right)
    })
}

function sortValue(
  item: Item,
  key: SortKey,
  fields: ListField[],
  order: (item: Item) => number,
): string | number | null {
  if (key === 'manual') {
    return order(item)
  }
  if (key === 'due') {
    // An instant is enough to order by; only the filter needs civil days.
    return item.dueAt === null ? null : Date.parse(item.dueAt)
  }
  if (key === 'description') {
    return item.description
  }
  if (key === 'status') {
    return item.completed ? 1 : 0
  }
  if (key === 'assignee') {
    return item.assignee?.displayName ?? null
  }
  const fieldId = key.slice('cost:'.length)
  const value = item.costValues.find((entry) => entry.fieldId === fieldId)
  if (!value) {
    return null
  }
  if (value.amount !== null) {
    return value.amount
  }
  // By the choice's **configured position**, not its label: 5 min / 30 min /
  // 60 min is the order the owner arranged, and alphabetically it is 30, 5, 60.
  const field = fields.find((candidate) => candidate.id === fieldId)
  const index = field?.choices.findIndex((choice) => choice.id === value.choiceId) ?? -1
  return index < 0 ? null : index
}

/**
 * The sort keys this list can actually answer (R113, R120).
 *
 * <p>Here rather than in `FilterBar`, because the parser validates a
 * `cost:<id>` against the same configuration the menu is built from - and
 * because a component file that also exports helpers breaks fast refresh.
 */
export function sortOptions(fields: ListField[]): { key: SortKey; label: string }[] {
  const options: { key: SortKey; label: string }[] = [
    { key: 'manual', label: 'Manual order' },
    { key: 'due', label: 'Due date' },
    { key: 'description', label: 'Description' },
    { key: 'status', label: 'Status' },
  ]
  if (declares(fields, 'ASSIGNEE')) {
    options.push({ key: 'assignee', label: 'Assignee' })
  }
  for (const field of fields) {
    if (field.kind === 'COST' && field.id) {
      options.push({ key: `cost:${field.id}`, label: field.label ?? 'Cost' })
    }
  }
  return options
}

export function sortLabel(key: SortKey, fields: ListField[]): string {
  return sortOptions(fields).find((option) => option.key === key)?.label ?? 'Manual order'
}

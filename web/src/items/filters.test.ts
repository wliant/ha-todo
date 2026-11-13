import { describe, expect, it } from 'vitest'
import {
  DEFAULT_FILTER,
  UNASSIGNED,
  countActive,
  emptyCostFilter,
  matchesFilter,
  parseFilter,
  sortOptions,
  toggleStatus,
  visibleItems,
  writeFilter,
  type ItemFilter,
} from './filters'
import type { Item } from '../api/items'
import type { ListField } from '../api/lists'

const ZONE = 'Asia/Singapore'
/** A Thursday, in the household's zone, at lunchtime. */
const TODAY = new Date(2026, 10, 12, 13, 0)

function field(overrides: Partial<ListField>): ListField {
  return {
    id: 'id-field',
    kind: 'DESCRIPTION',
    label: null,
    required: true,
    costMode: null,
    costUnitLabel: null,
    pointsPerUnit: null,
    choices: [],
    ...overrides,
  }
}

const description = field({ id: 'id-desc' })
const assignee = field({ id: 'id-assignee', kind: 'ASSIGNEE', required: false })

const effort = field({
  id: 'id-effort',
  kind: 'COST',
  label: 'Effort',
  required: false,
  costMode: 'FIXED',
  choices: [
    { id: 'id-5', label: '5 min', points: 1 },
    { id: 'id-30', label: '30 min', points: 3 },
    { id: 'id-60', label: '60 min', points: 6 },
  ],
})

const money = field({
  id: 'id-money',
  kind: 'COST',
  label: 'Money',
  required: false,
  costMode: 'NUMERIC',
  costUnitLabel: 'dollars',
})

const FIELDS = [description, assignee, effort, money]
const SHAPE = { fields: FIELDS, memberIds: ['id-ana', 'id-ben'] }

function item(overrides: Partial<Item> = {}): Item {
  return {
    id: 'id-item',
    listId: 'id-list',
    description: 'Buy milk',
    detailHtml: null,
    assignee: null,
    selfAssigned: false,
    dueAt: null,
    state: 'OPEN',
    completedBy: null,
    completedAt: null,
    completionNoteHtml: null,
    lapsedBy: null,
    lapsedAt: null,
    reopenedAt: null,
    pointsEarned: null,
    costValues: [],
    version: 1,
    ...overrides,
  }
}

function filter(overrides: Partial<ItemFilter> = {}): ItemFilter {
  return { ...DEFAULT_FILTER, ...overrides }
}

function keeps(one: Item, over: Partial<ItemFilter>): boolean {
  return matchesFilter(one, filter(over), FIELDS, ZONE, TODAY)
}

function shown(items: Item[], over: Partial<ItemFilter> = {}): string[] {
  return visibleItems(items, filter(over), FIELDS, ZONE, TODAY).map((one) => one.description)
}

describe('the address', () => {
  it('writes nothing for the default view', () => {
    // R115: an untouched list has a clean URL, the rule `?on=` follows too.
    expect(writeFilter(DEFAULT_FILTER).toString()).toBe('')
  })

  it('reads the default view out of an empty address', () => {
    expect(parseFilter(new URLSearchParams(''), SHAPE)).toEqual(DEFAULT_FILTER)
  })

  it('round-trips every filter the bar can produce', () => {
    const filters: ItemFilter[] = [
      filter({ status: ['open', 'done'] }),
      filter({ status: ['done'] }),
      filter({ assigneeIds: ['id-ben', UNASSIGNED] }),
      filter({ due: { kind: 'overdue' } }),
      filter({ due: { kind: 'range', from: '2026-11-01', to: '2026-11-30' } }),
      filter({ costs: { 'id-effort': { ...emptyCostFilter(), choiceIds: ['id-5', 'id-30'] } } }),
      filter({ costs: { 'id-effort': { ...emptyCostFilter(), unanswered: true } } }),
      filter({ costs: { 'id-money': { ...emptyCostFilter(), min: 2, max: 8, unanswered: true } } }),
      filter({ sort: 'due', descending: true }),
      filter({ sort: 'cost:id-effort' }),
    ]
    for (const one of filters) {
      expect(parseFilter(writeFilter(one), SHAPE)).toEqual(one)
    }
  })

  it('drops each malformed value on its own, keeping the rest', () => {
    // R116. A truncated link must not be a screen a member cannot get past,
    // and one broken parameter must not take a good one with it.
    const parsed = parseFilter(
      new URLSearchParams(
        'status=done&who=id-ben,id-ghost&due=not-a-date&cost.id-effort=id-5,id-gone&sort=cost:id-missing&dir=sideways',
      ),
      SHAPE,
    )

    expect(parsed.status).toEqual(['done'])
    expect(parsed.assigneeIds).toEqual(['id-ben'])
    expect(parsed.due).toBeNull()
    expect(parsed.costs['id-effort'].choiceIds).toEqual(['id-5'])
    expect(parsed.sort).toBe('manual')
    expect(parsed.descending).toBe(false)
  })

  it('drops a filter on a field this list does not declare', () => {
    const parsed = parseFilter(
      new URLSearchParams('who=id-ben&cost.id-effort=id-5'),
      { fields: [description], memberIds: ['id-ben'] },
    )

    expect(parsed.assigneeIds).toEqual([])
    expect(parsed.costs).toEqual({})
  })

  it('counts only what is narrowing the list', () => {
    // The default counts as nothing, or an untouched list would announce a
    // filter; the sort counts as nothing, because ordering hides nothing.
    expect(countActive(DEFAULT_FILTER)).toBe(0)
    expect(countActive(filter({ sort: 'due', descending: true }))).toBe(0)
    expect(countActive(filter({ status: ['done'] }))).toBe(1)
    expect(
      countActive(
        filter({
          status: ['open', 'done'],
          assigneeIds: [UNASSIGNED],
          due: { kind: 'overdue' },
          costs: {
            'id-effort': { ...emptyCostFilter(), choiceIds: ['id-5'] },
            'id-money': emptyCostFilter(),
          },
        }),
      ),
    ).toBe(4)
  })

  it('never leaves the status filter empty', () => {
    // An empty status set would be an empty list for a reason the member
    // cannot see; unticking the last chip means everything (adr/0046).
    expect(toggleStatus(['open'], 'open')).toEqual(['open', 'done', 'lapsed'])
    expect(toggleStatus(['open'], 'done')).toEqual(['open', 'done'])
    expect(toggleStatus(['open'], 'lapsed')).toEqual(['open', 'lapsed'])
    expect(toggleStatus(['open', 'done'], 'open')).toEqual(['done'])
  })
})

describe('matching', () => {
  it('shows one status, or some, or - from a hand-edited address - everything', () => {
    const open = item({ description: 'Open' })
    const done = item({ description: 'Done', state: 'COMPLETED' })
    const lapsed = item({ description: 'Lapsed', state: 'LAPSED' })
    const all = [open, done, lapsed]

    expect(shown(all)).toEqual(['Open'])
    expect(shown(all, { status: ['done'] })).toEqual(['Done'])
    expect(shown(all, { status: ['lapsed'] })).toEqual(['Lapsed'])
    // Two of three. The old predicate read `status[0]` whenever fewer than all
    // were selected, which was right for two values and silently wrong here:
    // it would have shown only the open row.
    expect(shown(all, { status: ['open', 'done'] })).toEqual(['Open', 'Done'])
    expect(shown(all, { status: ['done', 'lapsed'] })).toEqual(['Done', 'Lapsed'])
    expect(shown(all, { status: ['open', 'done', 'lapsed'] })).toEqual([
      'Open',
      'Done',
      'Lapsed',
    ])
    expect(shown(all, { status: [] })).toEqual(['Open', 'Done', 'Lapsed'])
  })

  it('filters by assignee, and by nobody', () => {
    const bens = item({
      description: 'Ben’s',
      assignee: { id: 'id-ben', displayName: 'Ben', avatarImageId: null },
    })
    const nobodys = item({ description: 'Nobody’s' })

    expect(shown([bens, nobodys], { assigneeIds: ['id-ben'] })).toEqual(['Ben’s'])
    expect(shown([bens, nobodys], { assigneeIds: [UNASSIGNED] })).toEqual(['Nobody’s'])
    expect(shown([bens, nobodys], { assigneeIds: ['id-ben', UNASSIGNED] })).toEqual([
      'Ben’s',
      'Nobody’s',
    ])
  })

  it('buckets a due date by the household’s day, not by the hour', () => {
    // R112, and adr/0040's reasoning: a chore due at 09:00 is today's until
    // midnight. 01:00 UTC is 09:00 in Singapore, which is this morning - the
    // member filtering "today" at lunchtime must still see it.
    const thisMorning = item({ description: 'This morning', dueAt: '2026-11-12T01:00:00Z' })
    const yesterday = item({ description: 'Yesterday', dueAt: '2026-11-11T01:00:00Z' })
    const nextWeek = item({ description: 'Next week', dueAt: '2026-11-20T01:00:00Z' })
    const undated = item({ description: 'Undated' })
    const all = [thisMorning, yesterday, nextWeek, undated]

    expect(shown(all, { due: { kind: 'today' } })).toEqual(['This morning'])
    expect(shown(all, { due: { kind: 'overdue' } })).toEqual(['Yesterday'])
    expect(shown(all, { due: { kind: 'week' } })).toEqual(['This morning'])
    expect(shown(all, { due: { kind: 'none' } })).toEqual(['Undated'])
  })

  it('reads a day in the household’s zone, not the device’s', () => {
    // adr/0036: 16:00 UTC on the 12th is already the 13th in Singapore. The
    // same instant filtered by the same household day must answer the same way
    // whatever the device thinks, and this is the one that would differ.
    const lateUtc = item({ description: 'Late', dueAt: '2026-11-12T16:00:00Z' })

    expect(keeps(lateUtc, { due: { kind: 'today' } })).toBe(false)
    expect(keeps(lateUtc, { due: { kind: 'week' } })).toBe(true)
    expect(matchesFilter(lateUtc, filter({ due: { kind: 'today' } }), FIELDS, 'Europe/London', TODAY))
      .toBe(true)
  })

  it('includes both ends of a range', () => {
    const first = item({ description: 'First', dueAt: '2026-11-01T02:00:00Z' })
    const last = item({ description: 'Last', dueAt: '2026-11-30T02:00:00Z' })
    const outside = item({ description: 'Outside', dueAt: '2026-12-01T02:00:00Z' })

    expect(
      shown([first, last, outside], {
        due: { kind: 'range', from: '2026-11-01', to: '2026-11-30' },
      }),
    ).toEqual(['First', 'Last'])
  })

  it('filters a fixed cost by its choices', () => {
    const quick = item({
      description: 'Quick',
      costValues: [{ fieldId: 'id-effort', choiceId: 'id-5', amount: null }],
    })
    const slow = item({
      description: 'Slow',
      costValues: [{ fieldId: 'id-effort', choiceId: 'id-60', amount: null }],
    })

    expect(shown([quick, slow], { costs: { 'id-effort': { ...emptyCostFilter(), choiceIds: ['id-5'] } } }))
      .toEqual(['Quick'])
  })

  it('filters a numeric cost by a range, either bound optional', () => {
    const cheap = item({
      description: 'Cheap',
      costValues: [{ fieldId: 'id-money', choiceId: null, amount: 3 }],
    })
    const dear = item({
      description: 'Dear',
      costValues: [{ fieldId: 'id-money', choiceId: null, amount: 30 }],
    })
    const both = [cheap, dear]

    expect(shown(both, { costs: { 'id-money': { ...emptyCostFilter(), min: null, max: 10 } } }))
      .toEqual(['Cheap'])
    expect(shown(both, { costs: { 'id-money': { ...emptyCostFilter(), min: 10, max: null } } }))
      .toEqual(['Dear'])
    expect(shown(both, { costs: { 'id-money': { ...emptyCostFilter(), min: 3, max: 3 } } }))
      .toEqual(['Cheap'])
  })

  it('hides what never answered a cost unless asked for it', () => {
    // R111: a blank is not a small amount, and "5 min" is not a request for
    // "unknown".
    const silent = item({ description: 'Silent' })
    const quick = item({
      description: 'Quick',
      costValues: [{ fieldId: 'id-effort', choiceId: 'id-5', amount: null }],
    })
    const both = [silent, quick]

    expect(shown(both, { costs: { 'id-effort': { ...emptyCostFilter(), choiceIds: ['id-5'] } } }))
      .toEqual(['Quick'])
    expect(shown(both, { costs: { 'id-effort': { ...emptyCostFilter(), unanswered: true } } }))
      .toEqual(['Silent'])
    expect(
      shown(both, {
        costs: { 'id-effort': { ...emptyCostFilter(), choiceIds: ['id-5'], unanswered: true } },
      }),
    ).toEqual(['Silent', 'Quick'])
  })

  it('applies every active cost filter at once', () => {
    const one = item({
      description: 'Both',
      costValues: [
        { fieldId: 'id-effort', choiceId: 'id-5', amount: null },
        { fieldId: 'id-money', choiceId: null, amount: 4 },
      ],
    })

    expect(
      keeps(one, {
        costs: {
          'id-effort': { ...emptyCostFilter(), choiceIds: ['id-5'] },
          'id-money': { ...emptyCostFilter(), max: 2 },
        },
      }),
    ).toBe(false)
  })
})

describe('sorting', () => {
  const late = item({ id: 'a', description: 'Zebra', dueAt: '2026-11-20T02:00:00Z' })
  const soon = item({ id: 'b', description: 'Apple', dueAt: '2026-11-13T02:00:00Z' })
  const undated = item({ id: 'c', description: 'Mango' })
  const all = [late, soon, undated]

  it('keeps the member’s manual order by default', () => {
    expect(shown(all, { status: ['open', 'done'] })).toEqual(['Zebra', 'Apple', 'Mango'])
  })

  it('sorts by due date, soonest first', () => {
    expect(shown(all, { sort: 'due' })).toEqual(['Apple', 'Zebra', 'Mango'])
  })

  it('puts blanks last in both directions', () => {
    // R114: reversing the direction must not bring a crowd of undated items
    // to the top of the list.
    expect(shown(all, { sort: 'due', descending: true })).toEqual(['Zebra', 'Apple', 'Mango'])
    expect(shown(all, { sort: 'assignee' })).toEqual(['Zebra', 'Apple', 'Mango'])
    expect(shown(all, { sort: 'assignee', descending: true })).toEqual(['Zebra', 'Apple', 'Mango'])
  })

  it('sorts by description, in either direction', () => {
    expect(shown(all, { sort: 'description' })).toEqual(['Apple', 'Mango', 'Zebra'])
    expect(shown(all, { sort: 'description', descending: true })).toEqual(['Zebra', 'Mango', 'Apple'])
  })

  it('sorts a fixed cost by the owner’s order, not alphabetically', () => {
    // 5 min / 30 min / 60 min is the order the owner arranged; by label it is
    // 30, 5, 60.
    const items = [
      item({ id: 'a', description: 'Hour', costValues: [{ fieldId: 'id-effort', choiceId: 'id-60', amount: null }] }),
      item({ id: 'b', description: 'Half', costValues: [{ fieldId: 'id-effort', choiceId: 'id-30', amount: null }] }),
      item({ id: 'c', description: 'Quick', costValues: [{ fieldId: 'id-effort', choiceId: 'id-5', amount: null }] }),
    ]

    expect(shown(items, { sort: 'cost:id-effort' })).toEqual(['Quick', 'Half', 'Hour'])
  })

  it('is stable: equal keys keep the manual order, in both directions', () => {
    const items = [
      item({ id: 'a', description: 'Same' }),
      item({ id: 'b', description: 'Same' }),
      item({ id: 'c', description: 'Same' }),
    ]
    const ids = (descending: boolean) =>
      visibleItems(items, filter({ sort: 'description', descending }), FIELDS, ZONE, TODAY).map(
        (one) => one.id,
      )

    expect(ids(false)).toEqual(['a', 'b', 'c'])
    expect(ids(true)).toEqual(['a', 'b', 'c'])
  })

  it('offers only the keys this list can answer', () => {
    expect(sortOptions([description]).map((option) => option.key)).toEqual([
      'manual',
      'due',
      'description',
      'status',
    ])
    expect(sortOptions(FIELDS).map((option) => option.key)).toContain('cost:id-effort')
    expect(sortOptions(FIELDS).map((option) => option.label)).toContain('Effort')
  })
})

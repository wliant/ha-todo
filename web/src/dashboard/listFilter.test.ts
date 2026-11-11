import { describe, expect, it } from 'vitest'
import {
  known,
  listFilterLabel,
  onlyLists,
  parseLists,
  writeLists,
} from './listFilter'
import type { DashboardItem } from '../api/dashboard'
import type { ListSummary } from '../api/lists'

function list(id: string, name: string): ListSummary {
  return {
    id,
    name,
    description: null,
    icon: 'CHECKLIST',
    owned: true,
    pinned: false,
    memberCount: 1,
  }
}

function item(id: string, listId: string): DashboardItem {
  return {
    id,
    listId,
    listName: listId,
    listIcon: 'CHECKLIST',
    description: id,
    detailHtml: null,
    assignee: null,
    selfAssigned: false,
    dueAt: null,
    costValues: [],
    version: 0,
  }
}

const groceries = list('id-groceries', 'Groceries')
const chores = list('id-chores', 'Chores')

describe('parseLists', () => {
  it('reads nothing as every list', () => {
    expect(parseLists(new URLSearchParams())).toEqual([])
    expect(parseLists(new URLSearchParams('lists='))).toEqual([])
  })

  it('reads a comma-separated selection', () => {
    expect(parseLists(new URLSearchParams('lists=id-groceries,id-chores'))).toEqual([
      'id-groceries',
      'id-chores',
    ])
  })

  it('drops blanks and repeats rather than failing', () => {
    expect(parseLists(new URLSearchParams('lists=,id-chores, ,id-chores'))).toEqual(['id-chores'])
  })
})

describe('writeLists', () => {
  it('writes nothing for the default, so the plain agenda is a plain address', () => {
    expect(writeLists(new URLSearchParams(), []).toString()).toBe('')
  })

  it('keeps the rest of the address - the period, the scope, the open item', () => {
    const params = new URLSearchParams('on=2026-11-12&scope=mine&item=id-item')
    const next = writeLists(params, ['id-chores'])
    expect(next.get('on')).toBe('2026-11-12')
    expect(next.get('scope')).toBe('mine')
    expect(next.get('item')).toBe('id-item')
    expect(next.get('lists')).toBe('id-chores')
  })

  it('takes the param off again when the selection empties', () => {
    const params = new URLSearchParams('on=2026-11-12&lists=id-chores')
    expect(writeLists(params, []).toString()).toBe('on=2026-11-12')
  })

  it('round-trips a selection', () => {
    const ids = ['id-groceries', 'id-chores']
    expect(parseLists(writeLists(new URLSearchParams(), ids))).toEqual(ids)
  })
})

describe('onlyLists', () => {
  const items = [item('a', 'id-groceries'), item('b', 'id-chores')]

  it('keeps everything when nothing is chosen', () => {
    expect(onlyLists(items, [])).toEqual(items)
  })

  it('keeps only the chosen lists', () => {
    expect(onlyLists(items, ['id-chores']).map((row) => row.id)).toEqual(['b'])
  })
})

describe('known', () => {
  it('leaves the selection alone until the lists have loaded', () => {
    expect(known(['id-gone'], undefined)).toEqual(['id-gone'])
  })

  it('drops a list the member can no longer see', () => {
    expect(known(['id-chores', 'id-gone'], [groceries, chores])).toEqual(['id-chores'])
  })

  it('empties rather than matching nothing when every id is unknown', () => {
    // An empty selection is no filter, so a link naming a deleted list shows
    // the whole agenda rather than an empty one (R128).
    expect(known(['id-gone'], [groceries, chores])).toEqual([])
  })
})

describe('listFilterLabel', () => {
  it('names the list when there is one', () => {
    expect(listFilterLabel(['id-chores'], [groceries, chores])).toBe('Lists: Chores')
  })

  it('counts when there are several', () => {
    expect(listFilterLabel(['id-chores', 'id-groceries'], [groceries, chores])).toBe('Lists: 2')
  })

  it('says only "Lists" when nothing is chosen', () => {
    expect(listFilterLabel([], [groceries, chores])).toBe('Lists')
  })

  it('counts rather than guessing a name it does not have', () => {
    expect(listFilterLabel(['id-chores'], undefined)).toBe('Lists: 1')
  })
})

import { describe, expect, it } from 'vitest'
import type { LiveNotification } from '../api/live'
import {
  announce,
  nothingToSay,
  next,
  say,
  wordFor,
  type Announcements,
} from './announcements'

function notification(over: Partial<LiveNotification> = {}): LiveNotification {
  return {
    type: 'item.created',
    itemId: 'i1',
    listId: 'l1',
    listName: 'Groceries',
    description: 'Milk',
    actorDisplayName: 'Ben',
    byYou: false,
    ...over,
  }
}

/** Drains the queue into the order the member would actually read. */
function readAll(state: Announcements): string[] {
  const seen: string[] = []
  let current = state
  while (current.showing !== null) {
    seen.push(current.showing)
    current = next(current)
  }
  return seen
}

describe('wording', () => {
  it('names whoever added it', () => {
    expect(wordFor(notification())).toBe('Ben added Milk to Groceries.')
  })

  it('names nobody when the standing instruction added it', () => {
    expect(wordFor(notification({ actorDisplayName: null, description: 'Bins' })))
      .toBe('Bins was added to Groceries.')
  })
})

describe('the queue', () => {
  it('shows the first one straight away', () => {
    expect(announce(nothingToSay, 'one').showing).toBe('one')
  })

  it('shows several in turn', () => {
    let state = announce(nothingToSay, 'one')
    state = announce(state, 'two')
    state = announce(state, 'three')

    expect(readAll(state)).toEqual(['one', 'two', 'three'])
  })

  it('names up to five and counts the rest', () => {
    let state = nothingToSay
    for (const message of ['1', '2', '3', '4', '5', '6', '7']) {
      state = announce(state, message)
    }

    expect(readAll(state)).toEqual(['1', '2', '3', '4', '5', 'and 2 more chores were added.'])
  })

  it('keeps counting while the collapsed message is still pending', () => {
    let state = nothingToSay
    for (const message of ['1', '2', '3', '4', '5', '6']) {
      state = announce(state, message)
    }
    expect(state.overflow).toBe(1)

    state = announce(state, '7')
    expect(state.overflow).toBe(2)
  })

  it('counts one extra as one chore, not chores', () => {
    let state = nothingToSay
    for (const message of ['1', '2', '3', '4', '5', '6']) {
      state = announce(state, message)
    }

    expect(readAll(state).at(-1)).toBe('and 1 more chore was added.')
  })

  it('ends up saying nothing', () => {
    const state = next(announce(nothingToSay, 'one'))

    expect(state).toEqual(nothingToSay)
  })
})

describe("the member's own act", () => {
  it('pre-empts whatever is on screen', () => {
    const state = say('Milk is done.')

    expect(state.showing).toBe('Milk is done.')
  })

  /*
   * R185. Both code paths are individually correct and individually tested, so
   * the failure this prevents - the member never learning whether their own
   * completion landed - would show up nowhere else.
   */
  it('clears announcements that were queued behind it', () => {
    let state = announce(nothingToSay, 'Ben added Milk to Groceries.')
    state = announce(state, 'Ben added Bread to Groceries.')

    state = say('Take the bins out is done.')

    expect(readAll(state)).toEqual(['Take the bins out is done.'])
  })
})

import { describe, expect, it } from 'vitest'
import { repin } from './repin'

/**
 * Rewriting a pin wherever it sits in a cached payload.
 *
 * <p>The envelope branch names each kind's array by hand, and a kind left out
 * of it fails **nowhere**: the star simply does not move until the refetch
 * lands. These tests are what makes that a failure.
 */
describe('repin', () => {
  it('flips the flag on a summary with a matching id', () => {
    const summaries = [
      { id: 'id-a', name: 'A', pinned: false },
      { id: 'id-b', name: 'B', pinned: false },
    ]

    expect(repin(summaries, 'id-a', true)).toEqual([
      { id: 'id-a', name: 'A', pinned: true },
      { id: 'id-b', name: 'B', pinned: false },
    ])
  })

  it('rewrites a tracker sidebar envelope', () => {
    const envelope = {
      pinned: [],
      trackers: [{ id: 'id-w', name: 'Body Weight', pinned: false }],
    }

    expect(repin(envelope, 'id-w', true)).toEqual({
      pinned: [],
      trackers: [{ id: 'id-w', name: 'Body Weight', pinned: true }],
    })
  })

  it('rewrites a lists envelope and a collections envelope the same way', () => {
    expect(
      repin({ pinned: [], lists: [{ id: 'id-l', pinned: false }] }, 'id-l', true),
    ).toEqual({ pinned: [], lists: [{ id: 'id-l', pinned: true }] })
    expect(
      repin({ pinned: [], collections: [{ id: 'id-c', pinned: false }] }, 'id-c', true),
    ).toEqual({ pinned: [], collections: [{ id: 'id-c', pinned: true }] })
  })

  it('does not move a row between the envelope’s two arrays', () => {
    // Where it belongs afterwards depends on an ordering only the server can
    // compute, so the flag flips at once and `onSettled` relocates the row.
    const envelope = {
      pinned: [],
      trackers: [{ id: 'id-w', pinned: false }],
    }
    const result = repin(envelope, 'id-w', true) as typeof envelope

    expect(result.pinned).toHaveLength(0)
    expect(result.trackers).toHaveLength(1)
  })

  it('leaves a payload that merely happens to carry the same id alone', () => {
    // Shape-specific on purpose: a generic walk would rewrite things this has
    // no business touching.
    const item = { id: 'id-w', description: 'Weigh the dog', listId: 'id-l' }

    expect(repin(item, 'id-w', true)).toEqual(item)
  })

  it('leaves the envelope’s pinned array alone when it is not an array of summaries', () => {
    expect(repin({ pinned: true, id: 'id-w' }, 'id-w', false)).toEqual({
      pinned: false,
      id: 'id-w',
    })
  })
})

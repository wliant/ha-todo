/**
 * Rewrites one entry's pin flag wherever it appears in a cached payload.
 *
 * <p>Shared by the list, collection and tracker query modules because all three
 * have pins (adr/0043, 0019 R170) and all three cache the same two shapes: a
 * bare array of summaries, and a sidebar envelope holding a `pinned` array
 * beside its group.
 *
 * <p>Each kind's group array is named here by hand, which is fine at three and
 * is the point at which it stops scaling: a **fourth** kind should make the
 * envelope carry one discriminated array rather than adding a fourth name to
 * this list. Adding a kind and forgetting it here fails nowhere - the star
 * simply does not move until the refetch lands.
 *
 * <p>Shape-specific on purpose. A generic walk over every value of every
 * cached object would rewrite payloads this has no business touching - an
 * item's `pinned`-less list reference, say, or a share-picker row that happens
 * to carry the same id.
 *
 * <p>What this deliberately does **not** do is *move* the entry between the
 * envelope's two arrays. Where it belongs afterwards depends on an ordering
 * only the server can compute - name order in the pinned section, frequency
 * order in the capped group - so the flag flips instantly (which is what the
 * star under the finger needs) and the row relocates when the refetch lands.
 */
export function repin(data: unknown, id: string, pinned: boolean): unknown {
  if (Array.isArray(data)) {
    return data.map((entry) => repin(entry, id, pinned))
  }
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>
    // A summary: `pinned` is the boolean being rewritten. The typeof guard is
    // what keeps this from matching the envelope below, whose `pinned` is an
    // array.
    if (record.id === id && typeof record.pinned === 'boolean') {
      return { ...record, pinned }
    }
    if (
      Array.isArray(record.lists)
      || Array.isArray(record.collections)
      || Array.isArray(record.trackers)
    ) {
      return {
        ...record,
        ...(Array.isArray(record.pinned) ? { pinned: repin(record.pinned, id, pinned) } : {}),
        ...(Array.isArray(record.lists) ? { lists: repin(record.lists, id, pinned) } : {}),
        ...(Array.isArray(record.collections)
          ? { collections: repin(record.collections, id, pinned) }
          : {}),
        ...(Array.isArray(record.trackers)
          ? { trackers: repin(record.trackers, id, pinned) }
          : {}),
      }
    }
  }
  return data
}

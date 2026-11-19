import type { LiveNotification } from '../api/live'

/**
 * What the agenda says when work arrives, and how much of it (R180, R184).
 *
 * Pure: the queue is a value, every transition is a function of it, and nothing
 * here knows about React or about a snackbar. The 01:05 recurrence run is the
 * reason the cap exists at all - a household member should not have to sit
 * through eight messages to find out their week was filled in.
 */

/** Named, rather than numbered, for as long as that is reasonable. */
export const NAMED_AT_MOST = 5

export interface Announcements {
  /** What is on screen now, or null when nothing is. */
  showing: string | null
  /** Named messages still to show, oldest first. */
  waiting: string[]
  /** How many arrived beyond what will be named. */
  overflow: number
}

export const nothingToSay: Announcements = { showing: null, waiting: [], overflow: 0 }

/** "Ben added Milk to Groceries." / "Milk was added to Groceries." */
export function wordFor(notification: LiveNotification): string {
  const { actorDisplayName, description, listName } = notification
  return actorDisplayName
    ? `${actorDisplayName} added ${description} to ${listName}.`
    : `${description} was added to ${listName}.`
}

function overflowWord(count: number): string {
  return count === 1
    ? 'and 1 more chore was added.'
    : `and ${count} more chores were added.`
}

/**
 * Something arrived.
 *
 * Beyond {@link NAMED_AT_MOST} the tail stops being named and becomes a count,
 * which keeps recounting as more arrive - so a burst ends in one honest number
 * rather than a queue nobody reads.
 */
export function announce(state: Announcements, message: string): Announcements {
  if (state.showing === null && state.waiting.length === 0 && state.overflow === 0) {
    return { showing: message, waiting: [], overflow: 0 }
  }
  // The one being shown counts towards what has been named.
  const named = (state.showing === null ? 0 : 1) + state.waiting.length
  if (named >= NAMED_AT_MOST) {
    return { ...state, overflow: state.overflow + 1 }
  }
  return { ...state, waiting: [...state.waiting, message] }
}

/**
 * The member's own act, which **pre-empts and clears what was queued**
 * (R185).
 *
 * Completing, lapsing, saving and a refused write all confirm a gesture that
 * was just made, and the row vanishing is otherwise the only feedback
 * (adr/0039). A message about somebody else's chore overwriting that one, a
 * frame later, leaves the member never learning whether their own act landed -
 * and both code paths are individually correct, so nothing fails.
 */
export function say(message: string): Announcements {
  return { showing: message, waiting: [], overflow: 0 }
}

/** The current message has had its time. */
export function next(state: Announcements): Announcements {
  const [head, ...rest] = state.waiting
  if (head !== undefined) {
    return { showing: head, waiting: rest, overflow: state.overflow }
  }
  if (state.overflow > 0) {
    return { showing: overflowWord(state.overflow), waiting: [], overflow: 0 }
  }
  return nothingToSay
}

import { useCallback, useEffect, useRef, useState } from 'react'
import type { LiveNotification } from '../api/live'
import {
  announce as queue,
  next,
  nothingToSay,
  say as preempt,
  wordFor,
  type Announcements,
} from './announcements'

/** How long one announcement holds the snackbar. */
const ANNOUNCEMENT_MS = 4000
/** The member's own act gets longer, as it always has on this page. */
const OWN_ACT_MS = 6000

/**
 * The agenda's one message slot, with a queue behind it.
 *
 * **Deliberately local to `dashboard/`, not the app's first shared snackbar
 * provider.** Four surfaces own a local snackbar today and exactly one of them
 * is acquiring a second message source; a provider built for one real user is
 * the generalisation this codebase defers on principle, the way adr/0032 sets
 * its threshold at a fourth owner and adr/0051 at a fourth kind. **The
 * threshold for a shared provider is a second surface that needs a queue**, not
 * a second writer on one surface.
 */
export function useAnnouncer() {
  const [state, setState] = useState<Announcements>(nothingToSay)
  // Whether what is showing is the member's own act, which gets the longer
  // dwell. Held in a ref rather than in the state because it is about the
  // message currently on screen, not about the queue behind it.
  const ownAct = useRef(false)

  /** Something the member did. Pre-empts and clears the queue (R185). */
  const say = useCallback((message: string) => {
    ownAct.current = true
    setState(preempt(message))
  }, [])

  /** Something that arrived (R180). */
  const announce = useCallback((notification: LiveNotification) => {
    setState((current) => queue(current, wordFor(notification)))
  }, [])

  const dismiss = useCallback(() => {
    ownAct.current = false
    setState(next)
  }, [])

  // One timer, restarted whenever the message changes. The snackbar's own
  // autoHideDuration cannot do this: a queued message replacing the current one
  // without the component closing in between would inherit the old timer.
  useEffect(() => {
    if (state.showing === null) {
      return
    }
    const timer = setTimeout(dismiss, ownAct.current ? OWN_ACT_MS : ANNOUNCEMENT_MS)
    return () => clearTimeout(timer)
  }, [state.showing, dismiss])

  return { message: state.showing, say, announce, dismiss }
}

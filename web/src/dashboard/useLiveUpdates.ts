import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import {
  ITEM_CREATED,
  liveStreamSupported,
  openLiveStream,
  parseNotification,
  STREAM_CLOSED,
  type LiveNotification,
} from '../api/live'
import { currentUserKey } from '../auth/useCurrentUser'
import { listKeys } from '../lists/queries'
import { dashboardKeys } from './queries'

/** How long a stream has to open before we give up on it and poll instead. */
const OPEN_WITHIN_MS = 10_000
/** The fallback's cadence (adr/0056). */
const POLL_MS = 60_000
/** A burst of events is one refetch round, not thirty. */
const SETTLE_MS = 250
/** After this many permanent closures we stop trying until the tab is looked at. */
const GIVE_UP_AFTER = 5

/**
 * The agenda's connection to what the rest of the household is doing
 * (requirements/0021, adr/0054).
 *
 * Mounted by `DashboardPage` and nowhere else. The revisit threshold is a
 * *second* surface that needs telling; at that point this moves to `app/`,
 * takes a predicate for what to invalidate, and `AppShell` opens the one
 * stream.
 */
export function useLiveUpdates(announce: (notification: LiveNotification) => void) {
  const queryClient = useQueryClient()
  // Re-reading the latest announce without re-opening the stream: the effect
  // below must not depend on a callback identity, or an unstable callback
  // passed by any future caller would make every render a reconnect.
  const announceRef = useRef(announce)
  useEffect(() => {
    announceRef.current = announce
  }, [announce])

  useEffect(() => {
    let source: EventSource | null = null
    let openTimer: ReturnType<typeof setTimeout> | undefined
    let settleTimer: ReturnType<typeof setTimeout> | undefined
    let poll: ReturnType<typeof setInterval> | undefined
    let closures = 0
    let stopped = false
    // The first open is not a reconnection: the page's own queries are already
    // in flight, so refreshing there would be a second fetch of everything for
    // nothing. Every open after it is catch-up for whatever was missed.
    let everOpened = false

    function refresh() {
      // Both key trees. This hook is a reader rather than one of adr/0039's
      // writers, so that rule does not formally bind it - but its reason does:
      // the two prefixes are siblings and neither reaches the other, and the
      // chore that just appeared is on a list as well as on the agenda. Naming
      // only ['dashboard'] leaves a member who switches to the list screen
      // looking at a payload one chore short, with nothing to refetch it.
      void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
      void queryClient.invalidateQueries({ queryKey: listKeys.all })
    }

    function refreshSoon() {
      clearTimeout(settleTimer)
      settleTimer = setTimeout(refresh, SETTLE_MS)
    }

    /** adr/0056: the agenda stays current even when nothing can be pushed. */
    function startPolling() {
      if (poll !== undefined || stopped) {
        return
      }
      poll = setInterval(() => {
        // Polling forever in a background tab is the cost push was chosen to
        // avoid, and refetchOnWindowFocus already covers a tab coming back.
        if (!document.hidden) {
          void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
        }
      }, POLL_MS)
    }

    function stopPolling() {
      clearInterval(poll)
      poll = undefined
    }

    function connect() {
      if (stopped || !liveStreamSupported()) {
        startPolling()
        return
      }

      // Captured, and every listener below reads `stream` rather than the
      // outer `source`: after a reconnect the outer binding points at the new
      // stream, so an old stream's late error would read the new one's state.
      const stream = openLiveStream()
      source = stream

      // A stream that connects but is buffered by a proxy never fires `open`
      // in time, which is exactly the failure this product cannot detect any
      // other way.
      openTimer = setTimeout(startPolling, OPEN_WITHIN_MS)

      stream.addEventListener('open', () => {
        clearTimeout(openTimer)
        closures = 0
        stopPolling()
        if (everOpened) {
          // Catch-up for whatever happened while we were not listening.
          refresh()
        }
        everOpened = true
      })

      stream.addEventListener(ITEM_CREATED, (event) => {
        const notification = parseNotification((event as MessageEvent<string>).data)
        if (notification === null) {
          return
        }
        refreshSoon()
        // byYou is decided by the server, per recipient (adr/0055). The
        // refresh happens either way: the member may have a second device
        // open, and that one still needs it (R181).
        if (!notification.byYou) {
          announceRef.current(notification)
        }
      })

      stream.addEventListener('error', () => {
        // EventSource does NOT retry a non-2xx: per spec it fires error, sets
        // CLOSED and stops. So an expired session ends the stream for good
        // rather than hammering - and the hazard is the opposite one, a tab
        // that is silently no longer live.
        if (stream.readyState !== STREAM_CLOSED) {
          return
        }
        clearTimeout(openTimer)
        closures += 1
        startPolling()
        // If the session really is gone, ProtectedRoute redirects and this
        // hook's cleanup closes everything. Asked once per closure, never in a
        // loop of our own.
        void queryClient.invalidateQueries({ queryKey: currentUserKey })
        if (closures < GIVE_UP_AFTER) {
          connect()
        }
      })
    }

    /** A bound, not a loop: a tab somebody looks at again gets another try. */
    function retryWhenLookedAt() {
      if (!document.hidden && closures >= GIVE_UP_AFTER) {
        closures = 0
        connect()
      }
    }

    connect()
    document.addEventListener('visibilitychange', retryWhenLookedAt)

    return () => {
      stopped = true
      document.removeEventListener('visibilitychange', retryWhenLookedAt)
      clearTimeout(openTimer)
      clearTimeout(settleTimer)
      stopPolling()
      // Genuinely closed and nulled: StrictMode double-invokes effects in
      // development, and a leaked stream means every message appears twice -
      // a dev-only symptom that reads as a server bug.
      source?.close()
      source = null
    }
  }, [queryClient])
}

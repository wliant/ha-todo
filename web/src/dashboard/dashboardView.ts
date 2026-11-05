import { createContext, useContext } from 'react'
import type { CalendarEvent } from '../api/calendars'
import type { DashboardScope } from '../api/dashboard'
import type { Civil, Zone } from './calendar'

/** What the three range views need from the layout around them. */
export interface DashboardView {
  scope: DashboardScope
  /** The household's zone (adr/0036): every day boundary on this page. */
  zone: Zone
  /**
   * The day being looked at - today unless the address says otherwise
   * (adr/0037).
   *
   * <p>Each view derives its own range from this, which is what makes
   * switching range keep the period. It is **not** "now": today's highlight
   * still asks the clock, and conflating the two is the one mistake this
   * feature made available.
   */
  anchor: Civil
  open: (itemId: string) => void
  /**
   * The event itself rather than an id: a collapsed event has no single row
   * behind it, and the payload is already in hand - so there is nothing to look
   * up and nothing to fetch.
   */
  openEvent: (event: CalendarEvent) => void
}

export const ViewContext = createContext<DashboardView | null>(null)

export function useDashboardView(): DashboardView {
  const view = useContext(ViewContext)
  if (!view) {
    throw new Error('A dashboard view must be rendered inside DashboardPage')
  }
  return view
}

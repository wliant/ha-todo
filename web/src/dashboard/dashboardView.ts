import { createContext, useContext } from 'react'
import type { CalendarEvent } from '../api/calendars'
import type { DashboardItem, DashboardScope } from '../api/dashboard'
import type { Civil, Zone } from './calendar'

/** What the three range views need from the layout around them. */
export interface DashboardView {
  scope: DashboardScope
  /**
   * The lists the agenda is narrowed to, empty meaning every list (0014 R123).
   *
   * <p>Applied by each view to the rows it already has, never to the query:
   * the filter happens in the browser over one fetch, so changing it costs no
   * request (adr/0047). **Events are never filtered by it** - an event belongs
   * to a calendar feed and has no list, so narrowing to the grocery list must
   * not take somebody's dentist appointment off the day (R126).
   */
  lists: string[]
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
   * Tick a chore off without leaving the agenda (0010 R83).
   *
   * <p>The item rather than an id, because the write carries the version the
   * row was drawn from and the payload already has it. One mutation for the
   * page, here rather than per view (adr/0039).
   */
  complete: (item: DashboardItem) => void
  /** The same completion, with a line about how it went first (0010 R84). */
  writeNote: (item: DashboardItem) => void
  /**
   * Give up on a chore from the agenda (0015 R138), which is where the
   * decision is actually made: the member is looking at a day with too much
   * on it. It leaves the agenda exactly as a completion does, and carries no
   * note (R134).
   */
  lapse: (item: DashboardItem) => void
  /**
   * Add another chore like this one, on the list it came from (0018 R156).
   *
   * <p>The only create the agenda can offer: it crosses lists, so every other
   * kind of add would have to ask which list first. The item rather than an id
   * because the description and the detail being copied are already in hand.
   */
  duplicate: (item: DashboardItem) => void
  /**
   * Change this chore, on the list it belongs to (0020 R173).
   *
   * <p>The item rather than an id, as every other verb here: the form is
   * seeded from the payload the row was drawn from, version included, so the
   * save is refused rather than overwriting somebody else's change.
   */
  edit: (item: DashboardItem) => void
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

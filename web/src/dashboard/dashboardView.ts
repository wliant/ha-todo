import { createContext, useContext } from 'react'
import type { DashboardScope } from '../api/dashboard'
import type { Zone } from './calendar'

/** What the three range views need from the layout around them. */
export interface DashboardView {
  scope: DashboardScope
  /** The household's zone (adr/0036): every day boundary on this page. */
  zone: Zone
  open: (itemId: string) => void
}

export const ViewContext = createContext<DashboardView | null>(null)

export function useDashboardView(): DashboardView {
  const view = useContext(ViewContext)
  if (!view) {
    throw new Error('A dashboard view must be rendered inside DashboardPage')
  }
  return view
}

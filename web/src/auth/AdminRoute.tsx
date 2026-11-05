import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useCurrentUser } from './useCurrentUser'

/**
 * Keeps members out of the admin screen. A convenience, not a control - the
 * control is hasRole("ADMIN") on /api/admin/** in the backend.
 */
export function AdminRoute({ children }: { children: ReactNode }) {
  const { data: user, isPending } = useCurrentUser()

  if (isPending) {
    return <p role="status">Checking your session…</p>
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (user.role !== 'ADMIN') {
    // Where they would normally be, which is the dashboard now (R44).
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}

import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useCurrentUser } from './useCurrentUser'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { data: user, isPending } = useCurrentUser()
  const location = useLocation()

  if (isPending) {
    return <p role="status">Checking your session…</p>
  }

  if (!user) {
    // Remembered so a shared link that arrives while signed out is not lost:
    // sign in, and the share resumes where it was going (adr/0008).
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <>{children}</>
}

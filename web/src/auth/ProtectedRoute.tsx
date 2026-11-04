import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useCurrentUser } from './useCurrentUser'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { data: user, isPending } = useCurrentUser()

  if (isPending) {
    return <p role="status">Checking your session…</p>
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

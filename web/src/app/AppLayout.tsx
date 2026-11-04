import { Outlet } from 'react-router-dom'
import { useCurrentUser, useLogout } from '../auth/useCurrentUser'
import { NavBar } from './NavBar'

/**
 * The signed-in shell: navigation mounted once, around whichever screen is
 * routed into it. Rendered inside ProtectedRoute, so a user is always present
 * by the time this draws.
 */
export function AppLayout() {
  const { data: user } = useCurrentUser()
  const logoutMutation = useLogout()

  if (!user) {
    return null
  }

  return (
    <div className="shell">
      <NavBar user={user} onSignOut={() => logoutMutation.mutate()} />
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}

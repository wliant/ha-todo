import { Link } from 'react-router-dom'
import { useCurrentUser, useLogout } from '../auth/useCurrentUser'

/**
 * Placeholder. The household / list / item domain lands on its own feature
 * branch; this exists so the authenticated shell is navigable end to end.
 */
export function ListsPage() {
  const { data: user } = useCurrentUser()
  const logoutMutation = useLogout()

  return (
    <main className="page">
      <header className="row">
        <h1>Lists</h1>
        <span className="row">
          {user?.role === 'ADMIN' && <Link to="/admin">Users</Link>}
          <button type="button" onClick={() => logoutMutation.mutate()}>
            Sign out
          </button>
        </span>
      </header>

      <p>
        Signed in as <strong>{user?.displayName}</strong>
      </p>
      <p role="status">No lists yet.</p>
    </main>
  )
}

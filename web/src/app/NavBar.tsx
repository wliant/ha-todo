import { NavLink } from 'react-router-dom'
import type { CurrentUser } from '../api/client'

/**
 * The one navigation surface (R15). Placement is CSS, not JavaScript: the same
 * markup is a top bar from 48rem up and a bottom bar below it, so nothing
 * re-renders on resize and the two layouts cannot drift apart.
 */
export function NavBar({
  user,
  onSignOut,
}: {
  user: CurrentUser
  onSignOut: () => void
}) {
  return (
    <>
      <header className="appbar">
        <span className="wordmark">List</span>

        <span className="nav-links nav-links--inline">
          <NavLink to="/lists">Lists</NavLink>
          {user.role === 'ADMIN' && <NavLink to="/admin">Users</NavLink>}
        </span>

        <span className="identity">
          <span className="identity-text">
            Signed in as <strong>{user.displayName}</strong>
          </span>
          <button type="button" className="ghost" onClick={onSignOut}>
            Sign out
          </button>
        </span>
      </header>

      {/* Thumb reach on a phone. Hidden at the breakpoint, where the links
          above take over - and duplicated rather than moved so neither copy
          needs a viewport hook to decide whether to exist. */}
      <nav className="nav-links nav-links--bottom" aria-label="Main">
        <NavLink to="/lists">Lists</NavLink>
        {user.role === 'ADMIN' && <NavLink to="/admin">Users</NavLink>}
      </nav>
    </>
  )
}

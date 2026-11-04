import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import type { LoginUser } from '../api/client'
import { useCurrentUser, useLogin, useLoginUsers } from '../auth/useCurrentUser'

/**
 * Sign-in is picking yourself out of the household, not filling in a form.
 * Choosing someone who needs no password signs them straight in; choosing
 * someone who does reveals a password field for that person alone.
 * See docs/requirements/0002-accounts-and-administration.md R11.
 */
export function LoginPage() {
  const [chosen, setChosen] = useState<LoginUser | null>(null)
  const [password, setPassword] = useState('')
  const { data: user } = useCurrentUser()
  const { data: users, isPending, isError } = useLoginUsers()
  const loginMutation = useLogin()

  if (user) {
    return <Navigate to="/lists" replace />
  }

  function choose(candidate: LoginUser) {
    loginMutation.reset()
    if (candidate.passwordRequired) {
      setChosen(candidate)
      setPassword('')
    } else {
      // No secret to ask for: being on the list is the whole credential.
      loginMutation.mutate({ username: candidate.username })
    }
  }

  function goBack() {
    loginMutation.reset()
    setChosen(null)
    setPassword('')
  }

  return (
    <main className="page">
      <h1>ha-todo</h1>

      {chosen ? (
        <form
          className="stack"
          onSubmit={(event) => {
            event.preventDefault()
            loginMutation.mutate({ username: chosen.username, password })
          }}
        >
          <p>
            Signing in as <strong>{chosen.displayName}</strong>
          </p>

          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            // eslint-disable-next-line jsx-a11y/no-autofocus -- the member has already said who they are; the password is the only thing left to do
            autoFocus
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />

          <button type="submit" disabled={loginMutation.isPending}>
            {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
          </button>

          <button type="button" className="link" onClick={goBack}>
            Not {chosen.displayName}?
          </button>

          {loginMutation.isError && <p role="alert">That password did not match.</p>}
        </form>
      ) : (
        <>
          <h2>Who&apos;s signing in?</h2>

          {isPending && <p role="status">Loading the household…</p>}
          {isError && <p role="alert">Could not load the household. Is the server up?</p>}

          {users && users.length > 0 && (
            <ul className="user-list">
              {users.map((candidate) => (
                <li key={candidate.id}>
                  <button
                    type="button"
                    className="user-choice"
                    disabled={loginMutation.isPending}
                    onClick={() => choose(candidate)}
                  >
                    <span className="user-name">{candidate.displayName}</span>
                    <span className="user-hint">
                      {candidate.role === 'ADMIN'
                        ? 'Admin · needs a password'
                        : candidate.passwordRequired
                          ? 'Needs a password'
                          : 'Tap to sign in'}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {users && users.length === 0 && <p role="status">Nobody has been added yet.</p>}

          {loginMutation.isError && <p role="alert">Could not sign in. Please try again.</p>}
        </>
      )}
    </main>
  )
}

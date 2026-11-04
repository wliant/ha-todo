import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { isUnauthorized, type LoginUser } from '../api/client'
import { useCurrentUser, useLogin, useLoginUsers } from '../auth/useCurrentUser'

/**
 * Sign-in is picking yourself out of the household, not filling in a form.
 *
 * Each row shows a name and nothing else: the roster is public, so it must not
 * say who administers the household or who has no password (R17, adr/0009).
 * The client therefore does not know in advance whether a password is needed
 * and finds out by trying - a 401 on a passwordless attempt *is* the question
 * "what is your password?".
 */
export function LoginPage() {
  const [chosen, setChosen] = useState<LoginUser | null>(null)
  const [askPassword, setAskPassword] = useState(false)
  const [password, setPassword] = useState('')
  const [failure, setFailure] = useState<string | null>(null)

  const { data: user } = useCurrentUser()
  const { data: users, isPending, isError } = useLoginUsers()
  const loginMutation = useLogin()
  const location = useLocation()

  if (user) {
    // Back to wherever they were headed - a shared link, usually (adr/0008).
    const from = (
      location.state as { from?: { pathname: string; search: string } } | null
    )?.from
    return <Navigate to={from ? `${from.pathname}${from.search}` : '/lists'} replace />
  }

  async function attempt(candidate: LoginUser, secret?: string) {
    setFailure(null)
    try {
      await loginMutation.mutateAsync({ username: candidate.username, password: secret })
    } catch (error) {
      if (isUnauthorized(error) && secret === undefined) {
        // This person has a password. Now - and only now - ask for it.
        setAskPassword(true)
        return
      }
      setFailure(
        isUnauthorized(error)
          ? 'Sorry, that did not work. Try again.'
          : 'Could not sign in. Please try again.',
      )
    }
  }

  function choose(candidate: LoginUser) {
    setChosen(candidate)
    setAskPassword(false)
    setPassword('')
    void attempt(candidate)
  }

  function goBack() {
    setChosen(null)
    setAskPassword(false)
    setPassword('')
    setFailure(null)
  }

  return (
    <main className="login">
      <div className="login-card">
        <h1 className="wordmark wordmark--large">List</h1>

        {chosen && askPassword ? (
          <form
            className="stack"
            onSubmit={(event) => {
              event.preventDefault()
              void attempt(chosen, password)
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

            <button type="submit" className="primary" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
            </button>

            <button type="button" className="link" onClick={goBack}>
              Not {chosen.displayName}?
            </button>

            {failure && <p role="alert">{failure}</p>}
          </form>
        ) : (
          <>
            <h2>Who&apos;s signing in?</h2>

            {isPending && <p role="status">Loading the household…</p>}
            {isError && (
              <p role="alert">Could not load the household. Is the server up?</p>
            )}

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
                      {candidate.displayName}
                    </button>
                  </li>
                ))}
              </ul>
            )}

            {users && users.length === 0 && (
              <p role="status">Nobody has been added yet.</p>
            )}

            {failure && <p role="alert">{failure}</p>}
          </>
        )}
      </div>
    </main>
  )
}

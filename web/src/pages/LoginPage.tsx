import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useCurrentUser, useLogin } from '../auth/useCurrentUser'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { data: user } = useCurrentUser()
  const loginMutation = useLogin()

  if (user) {
    return <Navigate to="/lists" replace />
  }

  return (
    <main className="page">
      <h1>ha-todo</h1>
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          loginMutation.mutate({ email, password })
        }}
      >
        <label htmlFor="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        <button type="submit" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
        </button>

        {loginMutation.isError && (
          <p role="alert">That email and password did not match.</p>
        )}
      </form>
    </main>
  )
}

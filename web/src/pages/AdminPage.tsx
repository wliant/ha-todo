import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAdminUsers, useCreateUser, useDeleteUser } from '../auth/useAdminUsers'
import { useCurrentUser } from '../auth/useCurrentUser'

/**
 * The admin's only job: add people and remove them (requirements/0002 R9).
 * Editing a user afterwards is deliberately not offered - delete and re-add is
 * the answer at household scale.
 */
export function AdminPage() {
  const { data: admin } = useCurrentUser()
  const { data: users, isPending, isError } = useAdminUsers()
  const createMutation = useCreateUser()
  const deleteMutation = useDeleteUser()

  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [passwordRequired, setPasswordRequired] = useState(false)
  const [password, setPassword] = useState('')

  function addUser() {
    createMutation.mutate(
      {
        username: username.trim(),
        displayName: displayName.trim(),
        password: passwordRequired ? password : undefined,
      },
      {
        onSuccess: () => {
          setUsername('')
          setDisplayName('')
          setPassword('')
          setPasswordRequired(false)
        },
      },
    )
  }

  return (
    <main className="page">
      <header className="row">
        <h1>Users</h1>
        <Link to="/lists">Back to lists</Link>
      </header>

      <section>
        <h2>Add someone</h2>
        <form
          className="stack"
          onSubmit={(event) => {
            event.preventDefault()
            addUser()
          }}
        >
          <label htmlFor="displayName">Name</label>
          <input
            id="displayName"
            name="displayName"
            required
            maxLength={100}
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />

          <label htmlFor="username">Login name</label>
          <input
            id="username"
            name="username"
            required
            maxLength={50}
            pattern="[A-Za-z0-9._\-]+"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />

          <label className="checkbox">
            <input
              type="checkbox"
              checked={passwordRequired}
              onChange={(event) => setPasswordRequired(event.target.checked)}
            />
            Require a password
          </label>

          {passwordRequired && (
            <>
              <label htmlFor="newPassword">Password</label>
              <input
                id="newPassword"
                name="newPassword"
                type="password"
                autoComplete="new-password"
                required
                maxLength={100}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </>
          )}

          <button type="submit" disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Adding…' : 'Add user'}
          </button>

          {createMutation.isError && (
            <p role="alert">{errorMessage(createMutation.error, 'Could not add that user.')}</p>
          )}
        </form>
      </section>

      <section>
        <h2>Household</h2>

        {isPending && <p role="status">Loading users…</p>}
        {isError && <p role="alert">Could not load the users.</p>}

        {users && (
          <ul className="user-list">
            {users.map((user) => (
              <li key={user.id} className="row">
                <span>
                  <strong>{user.displayName}</strong> <span className="user-hint">{user.username}</span>
                  <span className="user-hint">
                    {user.role === 'ADMIN'
                      ? ' · admin'
                      : user.passwordRequired
                        ? ' · password required'
                        : ' · no password'}
                  </span>
                </span>
                {/* R12: the admin account is undeletable, so it is not offered. */}
                {user.role === 'ADMIN' ? (
                  <span className="user-hint">
                    {user.id === admin?.id ? 'that’s you' : ''}
                  </span>
                ) : (
                  <button
                    type="button"
                    disabled={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(user.id)}
                  >
                    Remove {user.displayName}
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        {deleteMutation.isError && (
          <p role="alert">{errorMessage(deleteMutation.error, 'Could not remove that user.')}</p>
        )}
      </section>
    </main>
  )
}

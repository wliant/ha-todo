import { useState } from 'react'
import { errorMessage, type AdminUser } from '../api/client'
import { useAdminUsers, useCreateUser, useDeleteUser } from '../auth/useAdminUsers'
import { useCurrentUser } from '../auth/useCurrentUser'

/**
 * The admin's only job: add people and remove them (requirements/0002 R9),
 * presented as master-detail (R16) - the household on one side, the selected
 * person or the add form on the other.
 *
 * Which pane is visible is CSS: below 48rem the detail replaces the list and
 * offers a way back, above it both are columns. The component tree is the same
 * either way, so there is no viewport state to keep in sync.
 */
type Selection = { kind: 'user'; id: string } | { kind: 'new' } | null

export function AdminPage() {
  const { data: admin } = useCurrentUser()
  const { data: users, isPending, isError } = useAdminUsers()
  const deleteMutation = useDeleteUser()
  const [selection, setSelection] = useState<Selection>(null)

  const selected =
    selection?.kind === 'user'
      ? users?.find((user) => user.id === selection.id)
      : undefined

  // The selected person may have been removed, by this admin or by a concurrent
  // one, so the detail pane cannot outlive its subject. Derived rather than
  // corrected in an effect: the stale pane must never render even once.
  const shown: Selection =
    selection?.kind === 'user' && users && !selected ? null : selection

  return (
    <section className="master-detail" data-detail-open={shown !== null}>
      <div className="master">
        <header className="master-header">
          <h1>Users</h1>
          <button
            type="button"
            className="primary"
            onClick={() => setSelection({ kind: 'new' })}
          >
            Add someone
          </button>
        </header>

        {isPending && <p role="status">Loading users…</p>}
        {isError && <p role="alert">Could not load the users.</p>}

        {users && (
          <ul className="user-list" aria-label="Household">
            {users.map((user) => (
              <li key={user.id}>
                <button
                  type="button"
                  className="user-choice"
                  aria-current={shown?.kind === 'user' && shown.id === user.id}
                  onClick={() => setSelection({ kind: 'user', id: user.id })}
                >
                  <span className="user-name">{user.displayName}</span>
                  <span className="user-hint">{user.username}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="detail">
        <button
          type="button"
          className="link detail-back"
          onClick={() => setSelection(null)}
        >
          ← Household
        </button>

        {shown === null && (
          <p className="muted detail-empty">
            Pick someone to see their details, or add someone new.
          </p>
        )}

        {shown?.kind === 'new' && (
          <AddSomeone onAdded={(user) => setSelection({ kind: 'user', id: user.id })} />
        )}

        {selected && (
          <article className="stack">
            <h2>{selected.displayName}</h2>

            <dl className="facts">
              <dt>Login name</dt>
              <dd>{selected.username}</dd>

              <dt>Signing in</dt>
              <dd>
                {selected.passwordRequired
                  ? 'Needs a password'
                  : 'Picked from the list, no password'}
              </dd>

              <dt>Role</dt>
              <dd>{selected.role === 'ADMIN' ? 'Admin' : 'Member'}</dd>

              <dt>Added</dt>
              <dd>{new Date(selected.createdAt).toLocaleDateString()}</dd>
            </dl>

            {/* R12: the admin account is undeletable, so it is not offered. */}
            {selected.role === 'ADMIN' ? (
              <p className="muted">
                {selected.id === admin?.id
                  ? 'This is you. The admin account cannot be removed.'
                  : 'The admin account cannot be removed.'}
              </p>
            ) : (
              <button
                type="button"
                className="danger"
                disabled={deleteMutation.isPending}
                onClick={() =>
                  deleteMutation.mutate(selected.id, {
                    onSuccess: () => setSelection(null),
                  })
                }
              >
                Remove {selected.displayName}
              </button>
            )}

            {deleteMutation.isError && (
              <p role="alert">
                {errorMessage(deleteMutation.error, 'Could not remove that user.')}
              </p>
            )}
          </article>
        )}
      </div>
    </section>
  )
}

function AddSomeone({ onAdded }: { onAdded: (user: AdminUser) => void }) {
  const createMutation = useCreateUser()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [passwordRequired, setPasswordRequired] = useState(false)
  const [password, setPassword] = useState('')

  return (
    <form
      className="stack"
      onSubmit={(event) => {
        event.preventDefault()
        createMutation.mutate(
          {
            username: username.trim(),
            displayName: displayName.trim(),
            password: passwordRequired ? password : undefined,
          },
          { onSuccess: onAdded },
        )
      }}
    >
      <h2>Add someone</h2>

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
        autoCapitalize="none"
        autoCorrect="off"
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

      <button type="submit" className="primary" disabled={createMutation.isPending}>
        {createMutation.isPending ? 'Adding…' : 'Add user'}
      </button>

      {createMutation.isError && (
        <p role="alert">
          {errorMessage(createMutation.error, 'Could not add that user.')}
        </p>
      )}
    </form>
  )
}

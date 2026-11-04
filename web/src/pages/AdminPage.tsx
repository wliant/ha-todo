import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Divider from '@mui/material/Divider'
import FormControlLabel from '@mui/material/FormControlLabel'
import List from '@mui/material/List'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import { errorMessage } from '../api/http'
import type { AdminUser } from '../api/admin'
import {
  useAdminUsers,
  useCreateUser,
  useDeleteUser,
  useSetUserPassword,
} from '../auth/useAdminUsers'
import { useCurrentUser } from '../auth/useCurrentUser'

/**
 * The admin's job: add people, remove them, and reset a password
 * (requirements/0002 R9, requirements/0004 R33), presented as master-detail
 * (R16) - the household on one side, the selected person or the add form on the
 * other.
 *
 * Which pane is visible below the breakpoint is a responsive `display` driven by
 * the selection the component already holds, so there is still no viewport hook
 * and no second source of layout truth.
 */
type Selection = { kind: 'user'; id: string } | { kind: 'new' } | null

/** Shown only on the pane that is hidden at this width. */
const PHONE_ONLY = { xs: 'block', sm: 'none' } as const

export function AdminPage() {
  const { data: admin } = useCurrentUser()
  const { data: users, isPending, isError } = useAdminUsers()
  const deleteMutation = useDeleteUser()
  const [selection, setSelection] = useState<Selection>(null)

  const selected =
    selection?.kind === 'user' ? users?.find((user) => user.id === selection.id) : undefined

  // The selected person may have been removed, by this admin or by a concurrent
  // one, so the detail pane cannot outlive its subject. Derived rather than
  // corrected in an effect: the stale pane must never render even once.
  const shown: Selection = selection?.kind === 'user' && users && !selected ? null : selection

  const detailOpen = shown !== null

  return (
    <Box
      sx={{
        display: 'grid',
        gap: 3,
        alignItems: 'start',
        gridTemplateColumns: { sm: 'minmax(16rem, 22rem) 1fr' },
      }}
    >
      <Stack
        spacing={1.5}
        sx={{ display: detailOpen ? { xs: 'none', sm: 'flex' } : 'flex' }}
      >
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}
        >
          <Typography component="h1" variant="h5">
            Users
          </Typography>
          <Button variant="contained" onClick={() => setSelection({ kind: 'new' })}>
            Add someone
          </Button>
        </Stack>

        {isPending && <Typography role="status">Loading users…</Typography>}
        {isError && <Alert severity="error">Could not load the users.</Alert>}

        {users && (
          <List aria-label="Household" disablePadding>
            {users.map((user) => (
              <ListItemButton
                key={user.id}
                aria-current={shown?.kind === 'user' && shown.id === user.id}
                selected={shown?.kind === 'user' && shown.id === user.id}
                onClick={() => setSelection({ kind: 'user', id: user.id })}
              >
                <ListItemText primary={user.displayName} secondary={user.username} />
              </ListItemButton>
            ))}
          </List>
        )}
      </Stack>

      <Paper
        elevation={0}
        sx={{
          display: detailOpen ? 'block' : { xs: 'none', sm: 'block' },
          p: { sm: 2.5 },
          border: { sm: 1 },
          borderColor: { sm: 'divider' },
          minHeight: { sm: '12rem' },
          bgcolor: { xs: 'transparent', sm: 'background.paper' },
        }}
      >
        <Button
          size="small"
          startIcon={<ArrowBackRounded />}
          onClick={() => setSelection(null)}
          sx={{ display: PHONE_ONLY, mb: 1 }}
        >
          Household
        </Button>

        {shown === null && (
          <Typography color="text.secondary">
            Pick someone to see their details, or add someone new.
          </Typography>
        )}

        {shown?.kind === 'new' && (
          <AddSomeone onAdded={(user) => setSelection({ kind: 'user', id: user.id })} />
        )}

        {selected && (
          <Stack component="article" spacing={2}>
            <Typography component="h2" variant="h6">
              {selected.displayName}
            </Typography>

            <Box
              component="dl"
              sx={{
                display: 'grid',
                gridTemplateColumns: 'auto 1fr',
                columnGap: 2,
                rowGap: 0.75,
                m: 0,
                '& dt': { color: 'text.secondary' },
                '& dd': { m: 0 },
              }}
            >
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
            </Box>

            {/* R12: the admin account is undeletable, so it is not offered. */}
            {selected.role === 'ADMIN' ? (
              <Typography color="text.secondary">
                {selected.id === admin?.id
                  ? 'This is you. The admin account cannot be removed, and its password comes from the deployment configuration.'
                  : 'The admin account cannot be removed.'}
              </Typography>
            ) : (
              <>
                <Divider />
                <ResetPassword user={selected} />
                <Divider />
                <Button
                  color="error"
                  variant="outlined"
                  disabled={deleteMutation.isPending}
                  sx={{ alignSelf: 'flex-start' }}
                  onClick={() =>
                    deleteMutation.mutate(selected.id, {
                      onSuccess: () => setSelection(null),
                    })
                  }
                >
                  Remove {selected.displayName}
                </Button>
              </>
            )}

            {deleteMutation.isError && (
              <Alert severity="error">
                {errorMessage(deleteMutation.error, 'Could not remove that user.')}
              </Alert>
            )}
          </Stack>
        )}
      </Paper>
    </Box>
  )
}

/**
 * The recovery path for R33. A member can set their own password with no old
 * password, so a forgotten one - or a sibling's prank - needs an admin who can
 * clear it again.
 */
function ResetPassword({ user }: { user: AdminUser }) {
  const setPassword = useSetUserPassword()
  const [value, setValue] = useState('')
  const [done, setDone] = useState<string | null>(null)

  function submit(password: string | null) {
    setDone(null)
    setPassword.mutate(
      { id: user.id, password },
      {
        onSuccess: () => {
          setValue('')
          setDone(
            password === null
              ? `${user.displayName} can now sign in with one tap.`
              : `${user.displayName}'s password has been set.`,
          )
        },
      },
    )
  }

  return (
    <Stack
      component="form"
      spacing={1.5}
      onSubmit={(event) => {
        event.preventDefault()
        submit(value)
      }}
    >
      <Typography component="h3" variant="subtitle1">
        Password
      </Typography>

      <TextField
        id={`reset-password-${user.id}`}
        label="Set a new password"
        type="password"
        autoComplete="new-password"
        size="small"
        required
        slotProps={{ htmlInput: { maxLength: 100 } }}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        helperText="They are not asked for their old one."
      />

      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
        <Button type="submit" variant="outlined" disabled={setPassword.isPending}>
          Set password
        </Button>
        {user.passwordRequired && (
          <Button
            type="button"
            variant="text"
            disabled={setPassword.isPending}
            onClick={() => submit(null)}
          >
            Remove password
          </Button>
        )}
      </Stack>

      {done && <Alert severity="success">{done}</Alert>}
      {setPassword.isError && (
        <Alert severity="error">
          {errorMessage(setPassword.error, 'Could not change that password.')}
        </Alert>
      )}
    </Stack>
  )
}

function AddSomeone({ onAdded }: { onAdded: (user: AdminUser) => void }) {
  const createMutation = useCreateUser()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [passwordRequired, setPasswordRequired] = useState(false)
  const [password, setPassword] = useState('')

  return (
    <Stack
      component="form"
      spacing={2}
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
      <Typography component="h2" variant="h6">
        Add someone
      </Typography>

      <TextField
        id="displayName"
        name="displayName"
        label="Name"
        required
        slotProps={{ htmlInput: { maxLength: 100 } }}
        value={displayName}
        onChange={(event) => setDisplayName(event.target.value)}
      />

      <TextField
        id="username"
        name="username"
        label="Login name"
        required
        slotProps={{ htmlInput: { maxLength: 50, pattern: '[A-Za-z0-9._\\-]+' } }}
        autoCapitalize="none"
        autoCorrect="off"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
      />

      <FormControlLabel
        control={
          <Checkbox
            checked={passwordRequired}
            onChange={(event) => setPasswordRequired(event.target.checked)}
          />
        }
        label="Require a password"
      />

      {passwordRequired && (
        <TextField
          id="newPassword"
          name="newPassword"
          label="Password"
          type="password"
          autoComplete="new-password"
          required
          slotProps={{ htmlInput: { maxLength: 100 } }}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
      )}

      <Button
        type="submit"
        variant="contained"
        disabled={createMutation.isPending}
        sx={{ alignSelf: 'flex-start' }}
      >
        {createMutation.isPending ? 'Adding…' : 'Add user'}
      </Button>

      {createMutation.isError && (
        <Alert severity="error">
          {errorMessage(createMutation.error, 'Could not add that user.')}
        </Alert>
      )}
    </Stack>
  )
}

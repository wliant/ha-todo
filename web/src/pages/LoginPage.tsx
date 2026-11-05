import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import List from '@mui/material/List'
import ListItemAvatar from '@mui/material/ListItemAvatar'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { isUnauthorized } from '../api/http'
import type { LoginUser } from '../api/auth'
import { useCurrentUser, useLogin, useLoginUsers } from '../auth/useCurrentUser'
import { MemberAvatar } from '../app/MemberAvatar'

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
    const from = (location.state as { from?: { pathname: string; search: string } } | null)
      ?.from
    return <Navigate to={from ? `${from.pathname}${from.search}` : '/dashboard'} replace />
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
    <Box
      component="main"
      sx={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        p: 2,
        bgcolor: 'background.default',
      }}
    >
      <Paper
        elevation={0}
        sx={{
          width: '100%',
          maxWidth: '26rem',
          p: { xs: 2.5, sm: 3 },
          border: 1,
          borderColor: 'divider',
        }}
      >
        <Typography
          component="h1"
          sx={{ fontWeight: 700, fontSize: '2rem', textAlign: 'center', mb: 2 }}
        >
          List
        </Typography>

        {chosen && askPassword ? (
          <Stack
            component="form"
            spacing={2}
            onSubmit={(event) => {
              event.preventDefault()
              void attempt(chosen, password)
            }}
          >
            <Typography>
              Signing in as <strong>{chosen.displayName}</strong>
            </Typography>

            <TextField
              id="password"
              name="password"
              label="Password"
              type="password"
              autoComplete="current-password"
              // eslint-disable-next-line jsx-a11y/no-autofocus -- the member has already said who they are; the password is the only thing left to do
              autoFocus
              required
              fullWidth
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />

            <Button type="submit" variant="contained" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
            </Button>

            <Button type="button" variant="text" onClick={goBack}>
              Not {chosen.displayName}?
            </Button>

            {failure && <Alert severity="error">{failure}</Alert>}
          </Stack>
        ) : (
          <Stack spacing={2}>
            <Typography component="h2" variant="h6">
              Who&apos;s signing in?
            </Typography>

            {isPending && (
              <Stack direction="row" spacing={1.5} role="status" sx={{ alignItems: 'center' }}>
                <CircularProgress size={20} />
                <Typography>Loading the household…</Typography>
              </Stack>
            )}
            {isError && (
              <Alert severity="error">Could not load the household. Is the server up?</Alert>
            )}

            {users && users.length > 0 && (
              <List disablePadding>
                {users.map((candidate) => (
                  <ListItemButton
                    key={candidate.id}
                    disabled={loginMutation.isPending}
                    onClick={() => choose(candidate)}
                  >
                    {/* A face, and the name as the row's only text (R17).
                        The picture is the one field adr/0019 adds here; it is
                        aria-hidden, so the row still reads as just the name. */}
                    <ListItemAvatar aria-hidden>
                      <MemberAvatar
                        displayName={candidate.displayName}
                        avatarImageId={candidate.avatarImageId}
                        fallback="icon"
                      />
                    </ListItemAvatar>
                    <ListItemText primary={candidate.displayName} />
                  </ListItemButton>
                ))}
              </List>
            )}

            {users && users.length === 0 && (
              <Typography role="status">Nobody has been added yet.</Typography>
            )}

            {failure && <Alert severity="error">{failure}</Alert>}
          </Stack>
        )}
      </Paper>
    </Box>
  )
}

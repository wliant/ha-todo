import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import FormControlLabel from '@mui/material/FormControlLabel'
import Paper from '@mui/material/Paper'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import { useCurrentUser } from '../auth/useCurrentUser'
import { useOwnPassword } from '../auth/useOwnPassword'
import { AvatarUpload } from '../profile/AvatarUpload'
import { CalendarFeeds } from '../profile/CalendarFeeds'
import { useSidebarListCount, useThemePreference } from '../theme/useThemePreference'
import type { ThemePreference } from '../api/profile'

/** A member's own settings: how the app looks, and how they sign in. */
export function ProfilePage() {
  return (
    <Stack spacing={3}>
      <Typography component="h1" variant="h5">
        Your profile
      </Typography>
      <Picture />
      <Appearance />
      <Navigation />
      <Calendars />
      <PasswordSection />
    </Stack>
  )
}

/**
 * Labels stay single words where they can. A label is the control's accessible
 * name, so an explanatory clause inside one makes "Match my device" collide
 * with "Dark" for anything selecting by name - tests and screen-reader users
 * alike. The explanation belongs in the section's own text.
 */
const CHOICES: { value: ThemePreference; label: string }[] = [
  { value: 'SYSTEM', label: 'Match my device' },
  { value: 'LIGHT', label: 'Light' },
  { value: 'DARK', label: 'Dark' },
]

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper
      component="section"
      elevation={0}
      sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
    >
      <Stack spacing={2}>
        <Typography component="h2" variant="h6">
          {title}
        </Typography>
        {children}
      </Stack>
    </Paper>
  )
}

function Picture() {
  const { data: user } = useCurrentUser()
  if (!user) {
    return null
  }
  return (
    <Section title="Your picture">
      <AvatarUpload user={user} />
    </Section>
  )
}

/**
 * Where a calendar subscription lives: in the member's own settings, not behind
 * a Calendar menu entry. The events are read on the agenda, and a second navigation
 * surface for a read-only strip is how a feature nobody opens gets built
 * (requirements/0007).
 */
function Calendars() {
  return (
    <Section title="Your calendars">
      <CalendarFeeds />
    </Section>
  )
}

function Appearance() {
  const { stored, choose, error } = useThemePreference()

  return (
    <Section title="Appearance">
      <Typography color="text.secondary">
        Matching your device follows its light or dark setting. Either way the choice is
        saved to your account, so it follows you to another device.
      </Typography>

      <RadioGroup
        aria-label="Colour scheme"
        value={stored ?? 'SYSTEM'}
        onChange={(event) => choose(event.target.value as ThemePreference)}
      >
        {CHOICES.map((choice) => (
          <FormControlLabel
            key={choice.value}
            value={choice.value}
            control={<Radio />}
            label={choice.label}
          />
        ))}
      </RadioGroup>

      {error && (
        <Alert severity="error">
          {errorMessage(error, 'Could not save that preference.')}
        </Alert>
      )}
    </Section>
  )
}

/**
 * How many lists and collections the sidebar's groups show (0012 R102).
 *
 * <p>Its own section rather than a line inside Appearance: this is about what
 * the navigation reaches, not about how the app looks. A number field with
 * bounds rather than a slider - a member who wants seven wants seven, and the
 * server refuses anything outside 1 to 30 anyway.
 */
function Navigation() {
  const { count, choose, error } = useSidebarListCount()
  // Held locally while it is being typed, because an empty box and a half-typed
  // "1" of "12" are both states the server must not be sent. Seeded from the
  // stored value once it arrives.
  const [draft, setDraft] = useState<string | null>(null)
  const shown = draft ?? (count === undefined ? '' : String(count))

  function commit() {
    const parsed = Number(draft)
    setDraft(null)
    if (!draft || !Number.isInteger(parsed) || parsed < MIN_SIDEBAR || parsed > MAX_SIDEBAR) {
      return
    }
    if (parsed !== count) {
      choose(parsed)
    }
  }

  return (
    <Section title="Navigation">
      <Typography color="text.secondary">
        The sidebar groups your lists and collections, showing the ones you use most
        often first - with anything you have pinned ahead of them.
      </Typography>

      <TextField
        label="Lists shown in each group"
        type="number"
        value={shown}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            commit()
          }
        }}
        slotProps={{ htmlInput: { min: MIN_SIDEBAR, max: MAX_SIDEBAR, step: 1 } }}
        helperText={`Between ${MIN_SIDEBAR} and ${MAX_SIDEBAR}.`}
        sx={{ maxWidth: '16rem' }}
      />

      {error && (
        <Alert severity="error">
          {errorMessage(error, 'Could not save that preference.')}
        </Alert>
      )}
    </Section>
  )
}

/** Mirrors the bounds the server's check constraint enforces. */
const MIN_SIDEBAR = 1
const MAX_SIDEBAR = 30

/**
 * Setting or clearing one's own password, with no current password asked for -
 * an authenticated session already proves as much as this product asks, and
 * half these accounts have no password to be asked about (adr/0017).
 */
function PasswordSection() {
  const { data: user } = useCurrentUser()
  const mutation = useOwnPassword()
  const [wantsPassword, setWantsPassword] = useState(true)
  const [password, setPassword] = useState('')
  const [done, setDone] = useState<string | null>(null)

  if (user?.role === 'ADMIN') {
    return (
      <Section title="Password">
        <Alert severity="info">
          The admin password is set from the deployment configuration, so it cannot be
          changed here.
        </Alert>
      </Section>
    )
  }

  return (
    <Section title="Password">
      <Stack
        component="form"
        spacing={2}
        onSubmit={(event) => {
          event.preventDefault()
          setDone(null)
          mutation.mutate(wantsPassword ? password : null, {
            onSuccess: () => {
              setPassword('')
              setDone(
                wantsPassword
                  ? 'Saved. You will be asked for this next time you sign in.'
                  : 'Saved. You can now sign in with one tap.',
              )
            },
          })
        }}
      >
        <FormControlLabel
          control={
            <Switch
              checked={wantsPassword}
              onChange={(event) => {
                setWantsPassword(event.target.checked)
                setDone(null)
              }}
            />
          }
          label="Ask for a password when I sign in"
        />

        {wantsPassword ? (
          <TextField
            id="own-password"
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            slotProps={{ htmlInput: { maxLength: 100 } }}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            helperText="You are not asked for your current one."
          />
        ) : (
          <Typography color="text.secondary">
            You will sign in by picking your name, with no password.
          </Typography>
        )}

        <Button
          type="submit"
          variant="contained"
          disabled={mutation.isPending}
          sx={{ alignSelf: 'flex-start' }}
        >
          {mutation.isPending ? 'Saving…' : 'Save'}
        </Button>

        {done && <Alert severity="success">{done}</Alert>}
        {mutation.isError && (
          <Alert severity="error">
            {errorMessage(mutation.error, 'Could not change your password.')}
          </Alert>
        )}
      </Stack>
    </Section>
  )
}

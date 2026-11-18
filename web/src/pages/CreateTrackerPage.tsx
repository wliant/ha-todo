import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import { useCurrentUser } from '../auth/useCurrentUser'
import { DEFAULT_ICON } from '../lists/listIconTokens'
import { useMembers } from '../lists/queries'
import { TrackerForm, type TrackerFormValue } from '../trackers/TrackerForm'
import { useCreateTracker } from '../trackers/queries'

/**
 * A route rather than a dialog, for the reason the other two creates are: it
 * is deep-linkable, and a form that declares a unit, a period, a scope and a
 * rollup is a bad dialog on a phone.
 */
export function CreateTrackerPage() {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const createTracker = useCreateTracker()

  const [value, setValue] = useState<TrackerFormValue>({
    name: '',
    description: '',
    icon: DEFAULT_ICON,
    unit: '',
    // Daily and one-each: the morning weigh-in is the case this feature was
    // asked for, and the one a member is most likely to be creating.
    period: 'DAY',
    scope: 'PER_MEMBER',
    // Keeping the last reading suits a level (weight, a balance). A member
    // tracking a total picks SUM, which the form explains in those words.
    rollup: 'LATEST',
    decimals: 1,
    target: '',
    targetDirection: 'AT_MOST',
    // Private by default, as the other two kinds are.
    memberIds: [],
  })

  const others = (members ?? []).filter((member) => member.id !== user?.id)

  async function create() {
    try {
      const tracker = await createTracker.mutateAsync({
        name: value.name.trim(),
        description: value.description.trim() || undefined,
        icon: value.icon,
        unit: value.unit.trim(),
        period: value.period,
        scope: value.scope,
        rollup: value.rollup,
        decimals: value.decimals,
        // Both halves or neither: a figure with no direction cannot be read.
        target: value.target === '' ? null : Number(value.target),
        targetDirection: value.target === '' ? null : value.targetDirection,
        memberIds: value.memberIds,
      })
      navigate(`/trackers/${tracker.id}`)
    } catch {
      // Rendered from the mutation's own error state, below.
    }
  }

  const ready = value.name.trim().length > 0 && value.unit.trim().length > 0

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        New tracker
      </Typography>

      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
        <TrackerForm
          value={value}
          onChange={setValue}
          members={others}
          ownerName={user?.displayName ?? 'You'}
        />
      </Paper>

      {createTracker.isError && (
        <Alert severity="error">
          {errorMessage(createTracker.error, 'Could not create that tracker.')}
        </Alert>
      )}

      <Stack direction="row" spacing={1.5}>
        <Button
          variant="contained"
          disabled={!ready || createTracker.isPending}
          onClick={create}
        >
          Create tracker
        </Button>
        <Button component={RouterLink} to="/trackers">
          Cancel
        </Button>
      </Stack>
    </Stack>
  )
}

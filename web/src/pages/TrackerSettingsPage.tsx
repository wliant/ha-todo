import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage, isNotFound } from '../api/http'
import type { TrackerDetail } from '../api/trackers'
import { useCurrentUser } from '../auth/useCurrentUser'
import { useMembers } from '../lists/queries'
import { TrackerForm, type TrackerFormValue } from '../trackers/TrackerForm'
import {
  useDeleteTracker,
  useTracker,
  useUpdateTracker,
  useUpdateTrackerAccess,
} from '../trackers/queries'

/** Renaming, re-sharing and deleting. Owner only (requirements/0019 R164). */
export function TrackerSettingsPage() {
  const { trackerId } = useParams()
  const { data: tracker, isPending, error, refetch } = useTracker(trackerId)
  const [reloads, setReloads] = useState(0)

  if (isPending) {
    return <Typography role="status">Loading…</Typography>
  }

  if (error || !tracker) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Alert severity={isNotFound(error) ? 'info' : 'error'}>
          {isNotFound(error)
            ? 'That tracker no longer exists, or was never shared with you.'
            : 'That could not be loaded.'}
        </Alert>
        <Button component={RouterLink} to="/trackers">
          Back to trackers
        </Button>
      </Stack>
    )
  }

  return (
    <Editor
      key={`${tracker.id}-${reloads}`}
      tracker={tracker}
      onReload={() => {
        void refetch()
        setReloads((count) => count + 1)
      }}
    />
  )
}

function Editor({ tracker, onReload }: { tracker: TrackerDetail; onReload: () => void }) {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const { data: members } = useMembers()
  const update = useUpdateTracker(tracker.id)
  const updateAccess = useUpdateTrackerAccess(tracker.id)
  const remove = useDeleteTracker()

  const [value, setValue] = useState<TrackerFormValue>({
    name: tracker.name,
    description: tracker.description ?? '',
    icon: tracker.icon,
    unit: tracker.unit,
    period: tracker.period,
    scope: tracker.scope,
    rollup: tracker.rollup,
    decimals: tracker.decimals,
    target: tracker.target === null ? '' : String(tracker.target),
    targetDirection: tracker.targetDirection ?? 'AT_MOST',
    memberIds: tracker.members.filter((member) => !member.owner).map((member) => member.id),
  })
  const [problem, setProblem] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  const others = (members ?? []).filter((member) => member.id !== user?.id)
  const busy = update.isPending || updateAccess.isPending || remove.isPending

  /**
   * Two writes, in this order, each echoing the version the previous one
   * returned - the access rows are children, so changing them moves the
   * tracker's version (markChanged) and a second write from the old one would
   * be refused.
   */
  async function save() {
    setProblem(null)
    try {
      const saved = await update.mutateAsync({
        version: tracker.version,
        name: value.name.trim(),
        description: value.description.trim() || undefined,
        icon: value.icon,
        unit: value.unit.trim(),
        period: value.period,
        scope: value.scope,
        rollup: value.rollup,
        decimals: value.decimals,
        target: value.target === '' ? null : Number(value.target),
        targetDirection: value.target === '' ? null : value.targetDirection,
      })
      await updateAccess.mutateAsync({
        version: saved.version,
        memberIds: value.memberIds,
      })
      navigate(`/trackers/${tracker.id}`)
    } catch (error) {
      // The server's own sentence, which already distinguishes a 403 from a
      // 409 and from "this tracker already holds readings" (R161). A second
      // copy of that wording here would drift.
      setProblem(errorMessage(error, 'That could not be saved.'))
    }
  }

  async function destroy() {
    setProblem(null)
    try {
      await remove.mutateAsync(tracker.id)
      navigate('/trackers')
    } catch (error) {
      setProblem(errorMessage(error, 'That could not be deleted.'))
    }
  }

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        Tracker settings
      </Typography>

      {problem && (
        <Alert
          severity="warning"
          action={
            <Button size="small" onClick={onReload}>
              Reload
            </Button>
          }
        >
          {problem}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <TrackerForm
          value={value}
          onChange={setValue}
          members={others}
          ownerName={tracker.members.find((member) => member.owner)?.displayName ?? 'The owner'}
          // The server's answer to R161, not a count this screen interprets.
          reconfigurable={tracker.reconfigurable}
        />
      </Paper>

      <Stack direction="row" spacing={1}>
        <Button
          variant="contained"
          onClick={save}
          disabled={busy || !value.name.trim() || !value.unit.trim()}
        >
          Save
        </Button>
        <Button component={RouterLink} to={`/trackers/${tracker.id}`}>
          Cancel
        </Button>
      </Stack>

      <Divider />

      <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Typography variant="h6" component="h2">
          Delete this tracker
        </Typography>
        <Typography color="text.secondary">
          Every number on it goes too. There is no undo.
        </Typography>
        <Button color="error" onClick={() => setConfirming(true)} disabled={busy}>
          Delete tracker
        </Button>
      </Stack>

      <Dialog open={confirming} onClose={() => setConfirming(false)}>
        <DialogTitle>Delete “{tracker.name}”?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Every number recorded on it is deleted as well, for everybody it is shared with. This
            cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirming(false)}>Keep it</Button>
          <Button color="error" onClick={destroy} disabled={busy}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

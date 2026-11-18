import { useMemo, useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import InputAdornment from '@mui/material/InputAdornment'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { errorMessage, isConflict } from '../api/http'
import type { Reading } from '../api/trackerReadings'
import type { TrackerPeriod } from '../api/trackers'
import { useHouseholdZone } from '../app/useHouseholdZone'
import { now } from '../dashboard/calendar'
import { PeriodField } from './PeriodField'
import { periodKey } from './period'
import { useRecordReading, useUpdateReading } from './queries'

/**
 * Recording a number, or correcting one (requirements/0019 R162, R165).
 *
 * <p>One dialog for both, because they ask the same three questions. Which it
 * is comes from whether a reading was handed in, exactly as the item form
 * decides create from update by the presence of an id.
 *
 * <p>The bucket defaults to the current one - or, when the agenda's nudge
 * opened this, to the bucket the **server** named (R171). Either way it is a
 * household date, never a reading of the device's clock.
 */
export function ReadingDialog({
  trackerId,
  period,
  unit,
  editing,
  defaultPeriodStart,
  onClose,
  onSaved,
}: {
  trackerId: string
  period: TrackerPeriod
  unit: string
  /** The reading being corrected, or undefined to record a new one. */
  editing?: Reading
  /** The bucket to open on; today's when absent. */
  defaultPeriodStart?: string
  onClose: () => void
  onSaved?: (message: string) => void
}) {
  const zone = useHouseholdZone()
  // Seeded once. `now(zone)` inside the initialiser rather than in the render
  // body, for the reason PeriodField reads it inside a memo.
  const [bucket, setBucket] = useState(
    () => editing?.periodStart ?? defaultPeriodStart ?? periodKey(now(zone), period),
  )
  const [amount, setAmount] = useState(editing ? String(editing.amount) : '')
  const [note, setNote] = useState(editing?.note ?? '')

  const record = useRecordReading()
  const update = useUpdateReading()
  const saving = record.isPending || update.isPending
  const error = record.error ?? update.error

  // A blank is not a zero: an empty box must not save 0 kg.
  const parsed = useMemo(() => (amount.trim() === '' ? null : Number(amount)), [amount])
  const valid = parsed !== null && Number.isFinite(parsed)

  function save() {
    if (!valid) {
      return
    }
    const payload = { periodStart: bucket, amount: parsed, note: note.trim() || null }
    const done = {
      onSuccess: () => {
        onSaved?.(editing ? 'Updated.' : 'Recorded.')
        onClose()
      },
    }
    if (editing) {
      update.mutate({ id: editing.id, version: editing.version, ...payload }, done)
    } else {
      record.mutate({ trackerId, ...payload }, done)
    }
  }

  return (
    <Dialog open fullWidth maxWidth="xs" onClose={onClose}>
      <DialogTitle>{editing ? 'Edit this number' : 'Record a number'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          <TextField
            label="Amount"
            required
            autoFocus
            type="number"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            slotProps={{
              htmlInput: { step: 'any', inputMode: 'decimal' },
              input: {
                endAdornment: <InputAdornment position="end">{unit}</InputAdornment>,
              },
            }}
          />

          <PeriodField period={period} value={bucket} onChange={setBucket} />

          <TextField
            label="Note"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 200 } }}
            helperText="Optional - what was going on"
          />

          {error && (
            <Alert severity={isConflict(error) ? 'warning' : 'error'}>
              {errorMessage(error, 'Could not save that number.')}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={saving || !valid} onClick={save}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  )
}

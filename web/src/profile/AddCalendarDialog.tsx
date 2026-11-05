import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import type { CalendarPreview } from '../api/calendars'
import { useMembers } from '../lists/queries'
import { useCurrentUser } from '../auth/useCurrentUser'
import { useHouseholdZone } from '../app/useHouseholdZone'
import { eventTime } from '../dashboard/entries'
import { usePreviewCalendar, useSubscribeCalendar } from './calendarQueries'

/**
 * Subscribing a calendar, in two steps (requirements/0007 R62, R64, R65).
 *
 * <p>The preview is not a nicety. Nothing in this product can tell a curated
 * family calendar from somebody's whole working life - both are a URL that
 * parses - so the only defence against publishing every work meeting and
 * medical appointment to the household is showing the member what came back,
 * at the one moment they are paying attention. Do not let the save button
 * bypass it.
 */
export function AddCalendarDialog({ onClose }: { onClose: () => void }) {
  const [url, setUrl] = useState('')
  const [label, setLabel] = useState('')
  const [memberIds, setMemberIds] = useState<string[]>([])
  const [preview, setPreview] = useState<CalendarPreview | null>(null)

  const previewing = usePreviewCalendar()
  const subscribing = useSubscribeCalendar()
  const { data: members } = useMembers()
  const { data: me } = useCurrentUser()
  const zone = useHouseholdZone()

  const others = (members ?? []).filter((member) => member.id !== me?.id)

  async function look() {
    const result = await previewing.mutateAsync(url.trim())
    setPreview(result)
    if (!label.trim()) {
      setLabel(result.provider === 'GOOGLE' ? 'Google calendar' : 'My calendar')
    }
  }

  async function save() {
    await subscribing.mutateAsync({ label: label.trim(), url: url.trim(), memberIds })
    onClose()
  }

  return (
    <Dialog open fullWidth maxWidth="sm" onClose={onClose}>
      <DialogTitle>{preview ? 'Who can see this?' : 'Add a calendar'}</DialogTitle>
      <DialogContent dividers>
        {preview ? (
          <Stack spacing={2}>
            <Typography color="text.secondary">
              This is what the people you choose will see. If any of it should stay private,
              go back and publish a calendar that holds only what you want to share.
            </Typography>

            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Stack spacing={1}>
                {preview.next.length === 0 ? (
                  <Typography role="status" color="text.secondary">
                    Nothing is coming up on that calendar.
                  </Typography>
                ) : (
                  preview.next.map((event, index) => (
                    <Stack key={index} direction="row" spacing={1}>
                      <Typography variant="body2" sx={{ fontWeight: 700, minWidth: '5rem' }}>
                        {eventTime(event, zone)}
                      </Typography>
                      <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                        {event.summary}
                      </Typography>
                    </Stack>
                  ))
                )}
                {preview.totalInWindow > preview.next.length && (
                  <Typography variant="caption" color="text.secondary">
                    and {preview.totalInWindow - preview.next.length} more
                  </Typography>
                )}
              </Stack>
            </Paper>

            <TextField
              label="Name"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              helperText="What the household will see this calendar called."
              fullWidth
            />

            <Stack>
              <Typography variant="subtitle2">Who can see these events</Typography>
              <Typography variant="caption" color="text.secondary">
                Nobody, unless you choose someone. They will see every detail of every event
                on this calendar.
              </Typography>
              {others.map((member) => (
                <FormControlLabel
                  key={member.id}
                  control={
                    <Checkbox
                      checked={memberIds.includes(member.id)}
                      onChange={(e) =>
                        setMemberIds(
                          e.target.checked
                            ? [...memberIds, member.id]
                            : memberIds.filter((id) => id !== member.id),
                        )
                      }
                    />
                  }
                  label={member.displayName}
                />
              ))}
            </Stack>

            {subscribing.error && (
              <Alert severity="error">
                {errorMessage(subscribing.error, 'Could not add that calendar.')}
              </Alert>
            )}
          </Stack>
        ) : (
          <Stack spacing={2}>
            <Typography color="text.secondary">
              This reads a calendar you already keep somewhere else. Nothing is ever written
              back to it, and nothing here can add an event.
            </Typography>

            <Paper variant="outlined" sx={{ p: 1.5 }}>
              <Typography variant="subtitle2">Google Calendar</Typography>
              <Typography variant="body2" color="text.secondary">
                Open the calendar’s settings, scroll to <b>Integrate calendar</b>, and copy
                the <b>Secret address in iCal format</b> – not the public address beside it.
              </Typography>
              <Typography variant="subtitle2" sx={{ mt: 1.5 }}>
                Outlook
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Settings → Calendar → <b>Shared calendars</b> → <b>Publish a calendar</b>,
                then copy the ICS link.
              </Typography>
            </Paper>

            <TextField
              label="Calendar address"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://…/basic.ics"
              helperText="Keep this address private: anyone who has it can read that calendar."
              fullWidth
              multiline
              minRows={2}
            />

            {previewing.error && (
              <Alert severity="error">
                {errorMessage(previewing.error, 'That calendar could not be read.')}
              </Alert>
            )}

            <Typography variant="caption" color="text.secondary">
              Sharing your calendar in Google or Outlook as well is a good idea – this exists
              so your events show up next to the chores, not instead of your own calendar.
            </Typography>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        {preview ? (
          <>
            <Button onClick={() => setPreview(null)}>Back</Button>
            <Button
              variant="contained"
              onClick={save}
              disabled={!label.trim() || subscribing.isPending}
            >
              Add calendar
            </Button>
          </>
        ) : (
          <Button
            variant="contained"
            onClick={look}
            disabled={!url.trim() || previewing.isPending}
          >
            {previewing.isPending ? 'Reading…' : 'Look at it'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}

import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import CloseRounded from '@mui/icons-material/CloseRounded'
import PlaceRounded from '@mui/icons-material/PlaceRounded'
import ScheduleRounded from '@mui/icons-material/ScheduleRounded'
import { MemberAvatar } from '../app/MemberAvatar'
import type { CalendarEvent } from '../api/calendars'
import { civilIn, type Zone } from './calendar'

/**
 * An event, opened (requirements/0007 R68).
 *
 * <p>Read-only, with no actions at all: there is nothing this product can do to
 * somebody else's calendar entry, and a button suggesting otherwise would be a
 * lie about what the feature is.
 *
 * <p>The description is rendered as **text**. A feed is the one input in this
 * product that nobody in this household wrote, so it never goes near
 * `dangerouslySetInnerHTML` - the sanitizer of adr/0018 guards our own stored
 * HTML and has nothing to say about this.
 */
export function DashboardEventDialog({
  event,
  zone,
  onClose,
}: {
  event: CalendarEvent
  zone: Zone
  onClose: () => void
}) {
  return (
    <Dialog open fullWidth maxWidth="sm" onClose={onClose}>
      <DialogTitle sx={{ pr: 6, overflowWrap: 'anywhere' }}>
        {event.summary}
        <IconButton
          aria-label="Close"
          onClick={onClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <CloseRounded />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <ScheduleRounded fontSize="small" color="action" aria-hidden />
            <Typography>{when(event, zone)}</Typography>
          </Stack>

          {event.location && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <PlaceRounded fontSize="small" color="action" aria-hidden />
              <Typography sx={{ overflowWrap: 'anywhere' }}>{event.location}</Typography>
            </Stack>
          )}

          {event.description && (
            <Typography color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
              {event.description}
            </Typography>
          )}

          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
            {event.sources.map((source) => (
              <Chip
                key={source.feedId}
                size="small"
                variant="outlined"
                avatar={
                  <MemberAvatar
                    displayName={source.memberDisplayName}
                    avatarImageId={source.avatarImageId}
                    size={24}
                  />
                }
                label={`${source.memberDisplayName} · ${source.label}`}
              />
            ))}
          </Stack>
        </Stack>
      </DialogContent>
    </Dialog>
  )
}

/**
 * When it is, in words.
 *
 * <p>An all-day event is described by its dates and never by a time, because it
 * has none - and DTEND is exclusive, so a one-day event's end is tomorrow and
 * must not be shown.
 */
function when(event: CalendarEvent, zone: Zone): string {
  if (event.allDay) {
    const start = event.startDate ?? event.occursOn
    const last = lastDayOf(event)
    return last && last !== start
      ? `All day, ${dateText(start)} to ${dateText(last)}`
      : `All day, ${dateText(start)}`
  }
  if (!event.startsAt) {
    return ''
  }
  const start = civilIn(new Date(event.startsAt), zone)
  const end = event.endsAt ? civilIn(new Date(event.endsAt), zone) : null
  const day = start.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })
  const from = start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (!end) {
    return `${day}, ${from}`
  }
  return `${day}, ${from} – ${end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
}

/** The last day the event covers: DTEND is exclusive. */
function lastDayOf(event: CalendarEvent): string | null {
  if (!event.endDate) {
    return null
  }
  const end = new Date(`${event.endDate}T00:00:00`)
  end.setDate(end.getDate() - 1)
  const month = `${end.getMonth() + 1}`.padStart(2, '0')
  const day = `${end.getDate()}`.padStart(2, '0')
  return `${end.getFullYear()}-${month}-${day}`
}

/** A plain `YYYY-MM-DD` read as a date, never as an instant. */
function dateText(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString([], {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}

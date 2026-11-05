import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import type { CalendarEvent } from '../api/calendars'
import { eventSources, eventTime } from './entries'
import type { Zone } from './calendar'

/**
 * One event in the week or month view.
 *
 * <p>Deliberately not a {@link DashboardItemChip} in another colour: it has an
 * outline rather than a fill and leads with its time, so nobody scanning a
 * crowded day mistakes an appointment for a chore they could tick. There is
 * nothing to do to an event, so there is no affordance suggesting otherwise.
 */
export function DashboardEventChip({
  event,
  zone,
  onOpen,
}: {
  event: CalendarEvent
  zone: Zone
  onOpen: () => void
}) {
  const time = eventTime(event, zone)
  return (
    <ButtonBase
      onClick={onOpen}
      aria-label={`${event.summary}, ${time}, from ${eventSources(event)}`}
      sx={{
        width: '100%',
        justifyContent: 'flex-start',
        textAlign: 'left',
        borderRadius: 1,
        px: 0.5,
        py: 0.25,
        border: 1,
        borderColor: 'primary.main',
        color: 'text.primary',
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Typography
        variant="caption"
        sx={{
          display: 'block',
          width: '100%',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        <Typography component="span" variant="caption" sx={{ fontWeight: 700, mr: 0.5 }}>
          {event.allDay ? '•' : time}
        </Typography>
        {event.summary}
      </Typography>
    </ButtonBase>
  )
}

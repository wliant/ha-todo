import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import type { CalendarEvent } from '../api/calendars'
import { eventSources, eventTime } from './entries'
import type { Zone } from './calendar'

/**
 * One event in the week or month view.
 *
 * <p>Deliberately not a {@link DashboardItemChip} in another colour: it has an
 * outline where an item has a fill, so nobody scanning a crowded day mistakes
 * an appointment for a chore they could tick. There is nothing to do to an
 * event, so there is no affordance suggesting otherwise.
 *
 * <p>**The summary and nothing else**, for the reason R44 gives about items: a
 * week column is about six characters wide on a phone, and a chip that leads
 * with "07:30 PM" spends all of them on the time and truncates away the one
 * thing that identifies the event. The time is in the accessible name and in
 * the popup, which is one tap away.
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
        {event.summary}
      </Typography>
    </ButtonBase>
  )
}

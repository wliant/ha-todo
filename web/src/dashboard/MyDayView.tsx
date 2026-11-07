import { useMemo } from 'react'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import EventRounded from '@mui/icons-material/EventRounded'
import { MemberAvatar } from '../app/MemberAvatar'
import { CompleteControl } from '../items/CompleteControl'
import { ListIcon } from '../lists/listIcons'
import { useDashboardView } from './dashboardView'
import { civilIn, dayKey, dayRange, now, windowOf, type Zone } from './calendar'
import { periodName } from './period'
import { byDayEntries, eventSources, eventTime, type DayEntry } from './entries'
import { useScheduled } from './queries'

/**
 * What today holds, in full rows - this is the view a member acts from.
 *
 * <p>The day on screen is the anchor's, not necessarily today's (R74).
 *
 * <p>Chores and events in one sequence rather than two sections
 * (requirements/0007 R67): what matters at breakfast is the shape of the day,
 * and an appointment at seven is why nobody can take the seven o'clock chore.
 */
export function MyDayView() {
  const { scope, zone, anchor, open, openEvent, complete, writeNote } = useDashboardView()
  // The day on screen, which is today unless the member stepped away from it
  // (R74). See WeekView for why ranges are not computed during render.
  const window = useMemo(() => windowOf(dayRange(anchor), zone), [anchor, zone])
  const { data, isPending } = useScheduled(window.from, window.to, scope)

  const entries =
    byDayEntries(data?.items ?? [], data?.events ?? [], zone).get(dayKey(anchor)) ?? []

  return (
    <Stack spacing={1.5}>
      <Typography component="h2" variant="h6" aria-live="polite">
        {periodName('day', anchor, now(zone))}
      </Typography>

      {isPending && <Typography role="status">Loading…</Typography>}

      {data && entries.length === 0 && (
        <Typography role="status" color="text.secondary">
          Nothing is due, and nothing is happening.
        </Typography>
      )}

      {entries.map((entry) =>
        entry.kind === 'item' ? (
          <ItemRow
            key={entry.key}
            entry={entry}
            zone={zone}
            onOpen={open}
            onComplete={() => complete(entry.item)}
            onWriteNote={() => writeNote(entry.item)}
          />
        ) : (
          <EventRow key={entry.key} entry={entry} zone={zone} onOpen={openEvent} />
        ),
      )}
    </Stack>
  )
}

/**
 * A chore's row: what it is, and the control that ticks it off (0010 R83).
 *
 * <p>The disclosure wraps the description only, with the completion control as
 * its **sibling** - a row-wide button containing a button is invalid nesting
 * and a screen reader reads it as neither. `items/ItemRow` has the same shape
 * for the same reason.
 */
function ItemRow({
  entry,
  zone,
  onOpen,
  onComplete,
  onWriteNote,
}: {
  entry: Extract<DayEntry, { kind: 'item' }>
  zone: Zone
  onOpen: (id: string) => void
  onComplete: () => void
  onWriteNote: () => void
}) {
  const item = entry.item
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      {/* Wraps on a narrow phone: the control is wide enough that it and a
          long description cannot share one line on every device. */}
      <Stack
        direction="row"
        sx={{ alignItems: 'center', width: '100%', minWidth: 0, flexWrap: 'wrap', gap: 1.5 }}
      >
        <Avatar
          aria-hidden
          variant="rounded"
          sx={{
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            width: 32,
            height: 32,
          }}
        >
          <ListIcon token={item.listIcon} />
        </Avatar>
        <ButtonBase
          onClick={() => onOpen(item.id)}
          aria-label={`${item.description}, on ${item.listName}`}
          sx={{
            flex: '1 1 10rem',
            minWidth: 0,
            justifyContent: 'flex-start',
            textAlign: 'left',
            borderRadius: 1,
            px: 0.5,
            mx: -0.5,
          }}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ overflowWrap: 'anywhere' }}>{item.description}</Typography>
            <Typography variant="caption" color="text.secondary">
              {item.listName}
              {item.dueAt ? ` · ${formatTime(item.dueAt, zone)}` : ''}
            </Typography>
          </Box>
        </ButtonBase>
        <Stack direction="row" sx={{ alignItems: 'center', gap: 1, ml: 'auto' }}>
          {item.assignee && (
            <Chip
              size="small"
              avatar={
                <MemberAvatar
                  displayName={item.assignee.displayName}
                  avatarImageId={item.assignee.avatarImageId}
                  size={24}
                />
              }
              label={item.assignee.displayName}
              sx={
                item.selfAssigned
                  ? { bgcolor: 'selfClaimed.main', color: 'selfClaimed.contrastText' }
                  : undefined
              }
            />
          )}
          <CompleteControl
            description={item.description}
            onComplete={onComplete}
            onWriteNote={onWriteNote}
          />
        </Stack>
      </Stack>
    </Paper>
  )
}

/**
 * An event's row.
 *
 * <p>Outlined where an item's is filled, and with no completion affordance at
 * all - the two have to read differently at a glance or a member will try to
 * tick somebody's dentist appointment.
 */
function EventRow({
  entry,
  zone,
  onOpen,
}: {
  entry: Extract<DayEntry, { kind: 'event' }>
  zone: Zone
  onOpen: (event: Extract<DayEntry, { kind: 'event' }>['event']) => void
}) {
  const event = entry.event
  return (
    <Paper variant="outlined" sx={{ borderColor: 'primary.main', borderStyle: 'dashed' }}>
      <ButtonBase
        onClick={() => onOpen(event)}
        aria-label={`${event.summary}, ${eventTime(event, zone)}, from ${eventSources(event)}`}
        sx={{ width: '100%', p: 1.5, justifyContent: 'flex-start', textAlign: 'left' }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', width: '100%' }}>
          <Avatar
            aria-hidden
            variant="rounded"
            sx={{
              bgcolor: 'transparent',
              color: 'primary.main',
              border: 1,
              borderColor: 'primary.main',
              width: 32,
              height: 32,
            }}
          >
            <EventRounded fontSize="small" />
          </Avatar>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ overflowWrap: 'anywhere' }}>{event.summary}</Typography>
            <Typography variant="caption" color="text.secondary">
              {eventTime(event, zone)}
              {event.location ? ` · ${event.location}` : ''}
              {` · ${eventSources(event)}`}
            </Typography>
          </Box>
        </Stack>
      </ButtonBase>
    </Paper>
  )
}

/** The household's clock, not the device's (adr/0036). */
function formatTime(iso: string, zone: Zone): string {
  return civilIn(new Date(iso), zone).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  })
}

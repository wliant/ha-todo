import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { errorMessage } from '../api/http'
import type { CalendarFeed } from '../api/calendars'
import { useCurrentUser } from '../auth/useCurrentUser'
import { useMembers } from '../lists/queries'
import { AddCalendarDialog } from './AddCalendarDialog'
import {
  useMyCalendars,
  useRefreshCalendar,
  useUnsubscribeCalendar,
  useUpdateCalendar,
} from './calendarQueries'

/**
 * A member's subscribed calendars (requirements/0007 R62-R66, R71).
 *
 * <p>**No address appears on this screen, ever.** The server does not return
 * one, so there is nothing here to render even by accident - and editing a feed
 * means replacing the address, never seeing it. A member who has lost it
 * regenerates it at the provider.
 */
export function CalendarFeeds() {
  const { data: feeds, isPending } = useMyCalendars()
  const [adding, setAdding] = useState(false)

  return (
    <Stack spacing={2}>
      <Typography color="text.secondary">
        Your events appear on the agenda, next to the chores. Nothing here is ever written back to
        Google or Outlook, and nobody sees a calendar until you share it.
      </Typography>

      {isPending && <Typography role="status">Loading…</Typography>}

      {feeds?.length === 0 && (
        <Typography role="status" color="text.secondary">
          You have not added a calendar yet.
        </Typography>
      )}

      {feeds?.map((feed) => <FeedRow key={feed.id} feed={feed} />)}

      <Button variant="outlined" onClick={() => setAdding(true)} sx={{ alignSelf: 'flex-start' }}>
        Add a calendar
      </Button>

      {adding && <AddCalendarDialog onClose={() => setAdding(false)} />}
    </Stack>
  )
}

function FeedRow({ feed }: { feed: CalendarFeed }) {
  const { data: members } = useMembers()
  const { data: me } = useCurrentUser()
  const update = useUpdateCalendar()
  const refresh = useRefreshCalendar()
  const unsubscribe = useUnsubscribeCalendar()
  const [confirming, setConfirming] = useState(false)

  const others = (members ?? []).filter((member) => member.id !== me?.id)

  function toggle(memberId: string, shared: boolean) {
    update.mutate({
      id: feed.id,
      request: {
        label: feed.label,
        memberIds: shared
          ? [...feed.memberIds, memberId]
          : feed.memberIds.filter((id) => id !== memberId),
      },
    })
  }

  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack spacing={1}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Typography sx={{ flex: 1, overflowWrap: 'anywhere' }}>{feed.label}</Typography>
          <Chip size="small" label={providerName(feed.provider)} variant="outlined" />
          <Tooltip title="Refresh now">
            <IconButton
              aria-label={`Refresh ${feed.label}`}
              onClick={() => refresh.mutate(feed.id)}
              disabled={refresh.isPending}
            >
              <RefreshRounded />
            </IconButton>
          </Tooltip>
          <Tooltip title="Remove">
            <IconButton
              aria-label={`Remove ${feed.label}`}
              onClick={() => setConfirming(true)}
            >
              <DeleteOutlineRounded />
            </IconButton>
          </Tooltip>
        </Stack>

        <Typography variant="caption" color="text.secondary">
          {feed.eventCount} event{feed.eventCount === 1 ? '' : 's'}
          {feed.lastSuccessAt
            ? ` · updated ${new Date(feed.lastSuccessAt).toLocaleString([], {
                hour: '2-digit',
                minute: '2-digit',
                day: 'numeric',
                month: 'short',
              })}`
            : ' · never updated'}
        </Typography>

        {/* The owner sees the error on the row, because the alternative is a
            calendar that silently shows nothing (R71). */}
        {feed.stale && (
          <Alert severity="warning" role="status">
            {feed.lastError ?? 'This calendar has not updated recently.'}
          </Alert>
        )}

        <Stack>
          <Typography variant="caption" color="text.secondary">
            Who can see these events
          </Typography>
          <Stack direction="row" sx={{ flexWrap: 'wrap' }}>
            {others.map((member) => (
              <FormControlLabel
                key={member.id}
                control={
                  <Checkbox
                    size="small"
                    checked={feed.memberIds.includes(member.id)}
                    onChange={(event) => toggle(member.id, event.target.checked)}
                  />
                }
                label={member.displayName}
              />
            ))}
            {others.length === 0 && (
              <Typography variant="caption" color="text.secondary">
                There is nobody else here yet.
              </Typography>
            )}
          </Stack>
        </Stack>

        {update.error && (
          <Alert severity="error">
            {errorMessage(update.error, 'Could not change who can see that.')}
          </Alert>
        )}

        {confirming && (
          <Alert
            severity="warning"
            action={
              <Stack direction="row" spacing={1}>
                <Button size="small" onClick={() => setConfirming(false)}>
                  Keep
                </Button>
                <Button
                  size="small"
                  color="error"
                  onClick={() => unsubscribe.mutate(feed.id)}
                  disabled={unsubscribe.isPending}
                >
                  Remove
                </Button>
              </Stack>
            }
          >
            Removing this takes its events off the agenda for everybody you shared it with.
          </Alert>
        )}
      </Stack>
    </Paper>
  )
}

function providerName(provider: CalendarFeed['provider']): string {
  if (provider === 'GOOGLE') {
    return 'Google'
  }
  return provider === 'OUTLOOK' ? 'Outlook' : 'iCalendar'
}

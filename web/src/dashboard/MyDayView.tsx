import { useMemo } from 'react'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { MemberAvatar } from '../app/MemberAvatar'
import { ListIcon } from '../lists/listIcons'
import { useDashboardView } from './dashboardView'
import { dayRange, now, windowOf } from './calendar'
import { useScheduled } from './queries'

/** What today holds, in full rows - this is the view a member acts from. */
export function MyDayView() {
  const { scope, zone, open } = useDashboardView()
  // Once per mount; see WeekView for why this is not computed during render.
  const window = useMemo(() => windowOf(dayRange(now(zone)), zone), [zone])
  const { data, isPending } = useScheduled(window.from, window.to, scope)

  return (
    <Stack spacing={1.5}>
      <Typography component="h2" variant="h6">
        Due today
      </Typography>

      {isPending && <Typography role="status">Loading…</Typography>}

      {data && data.items.length === 0 && (
        <Typography role="status" color="text.secondary">
          Nothing is due today.
        </Typography>
      )}

      {data?.items.map((item) => (
        <Paper key={item.id} variant="outlined">
          <ButtonBase
            onClick={() => open(item.id)}
            aria-label={`${item.description}, on ${item.listName}`}
            sx={{ width: '100%', p: 1.5, justifyContent: 'flex-start', textAlign: 'left' }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', width: '100%' }}>
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
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography sx={{ overflowWrap: 'anywhere' }}>{item.description}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {item.listName}
                  {item.dueAt ? ` · ${formatTime(item.dueAt)}` : ''}
                </Typography>
              </Box>
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
            </Stack>
          </ButtonBase>
        </Paper>
      ))}
    </Stack>
  )
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

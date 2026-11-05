import { useMemo } from 'react'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { MemberAvatar } from '../app/MemberAvatar'
import { PointsPanel } from './PointsPanel'
import { useActivity } from './queries'

/** Midnight seven days ago, in this member's own zone, to now. */
function lastSevenDays(): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to)
  // setDate, never a subtraction of milliseconds: the arithmetic has to cross
  // a daylight-saving boundary without losing or gaining an hour.
  from.setDate(from.getDate() - 7)
  from.setHours(0, 0, 0, 0)
  return { from: from.toISOString(), to: to.toISOString() }
}

/**
 * Who did what this week (R43).
 *
 * <p>Beside the all-time standing rather than instead of it: "this week" and
 * "ever" are different questions, and a household asks both - "ever" stops
 * being motivating around the third week.
 */
export function ActivitySection({ listId }: { listId: string }) {
  // Computed once per mount: recomputing every render would change the query
  // key on each one and refetch forever.
  const window = useMemo(() => lastSevenDays(), [])
  const { data: activity, isPending } = useActivity(listId, window.from, window.to)

  return (
    <Stack spacing={2.5}>
      <Stack spacing={1.5}>
        <Typography component="h2" variant="h6">
          This week
        </Typography>

        {isPending && <Typography role="status">Loading activity…</Typography>}

        {activity && activity.members.length === 0 && (
          <Typography role="status" color="text.secondary">
            Nothing finished on this list in the last seven days.
          </Typography>
        )}

        {activity && activity.members.length > 0 && (
          <Paper
            component="section"
            elevation={0}
            sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
          >
            <Stack spacing={1.5}>
              {activity.members.map((member) => (
                <Stack
                  key={member.userId}
                  direction="row"
                  spacing={1.5}
                  sx={{ alignItems: 'center' }}
                >
                  <MemberAvatar
                    displayName={member.displayName}
                    avatarImageId={member.avatarImageId}
                    size={28}
                  />
                  <Typography sx={{ flex: 1 }}>{member.displayName}</Typography>
                  <Typography color="text.secondary" variant="body2">
                    {member.completed === 1 ? '1 item' : `${member.completed} items`}
                  </Typography>
                  {member.points > 0 && (
                    <Typography sx={{ fontWeight: 600 }}>{member.points}</Typography>
                  )}
                </Stack>
              ))}

              {activity.events.length > 0 && (
                <>
                  <Divider />
                  <Stack spacing={1} component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                    {activity.events.map((event) => (
                      <Stack
                        key={`${event.itemId}-${event.completedAt}`}
                        component="li"
                        direction="row"
                        spacing={1}
                        sx={{ alignItems: 'baseline', flexWrap: 'wrap', gap: 0.5 }}
                      >
                        <Typography
                          variant="body2"
                          sx={{
                            flex: 1,
                            minWidth: '8rem',
                            overflowWrap: 'anywhere',
                            // A completion that was undone is shown, not
                            // dropped - it happened, and R49 kept the record
                            // so it could be seen.
                            color: event.reopened ? 'text.secondary' : 'text.primary',
                          }}
                        >
                          {event.description}
                        </Typography>
                        {event.reopened && (
                          <Chip size="small" variant="outlined" label="reopened since" />
                        )}
                        <Typography variant="caption" color="text.secondary">
                          {event.completedBy?.displayName ?? 'Somebody'} ·{' '}
                          {formatWhen(event.completedAt)}
                        </Typography>
                      </Stack>
                    ))}
                  </Stack>
                </>
              )}
            </Stack>
          </Paper>
        )}
      </Stack>

      <PointsPanel listId={listId} />
    </Stack>
  )
}

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

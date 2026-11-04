import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { MemberAvatar } from '../app/MemberAvatar'
import { usePoints } from './queries'

/**
 * Who has earned what on this list (R23).
 *
 * Points go to whoever completed an item, not whoever it was assigned to -
 * which is the whole reason they are worth having.
 */
export function PointsPanel({ listId }: { listId: string }) {
  const { data: points } = usePoints(listId)

  if (!points || points.length === 0) {
    return null
  }

  return (
    <Paper
      component="section"
      elevation={0}
      sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
    >
      <Stack spacing={1.5}>
        <Typography component="h2" variant="h6">
          Points
        </Typography>
        <Stack spacing={1}>
          {points.map((member) => (
            <Stack
              key={member.userId}
              direction="row"
              spacing={1.5}
              sx={{ alignItems: 'center' }}
            >
              <MemberAvatar displayName={member.displayName} size={28} />
              <Typography sx={{ flex: 1 }}>{member.displayName}</Typography>
              <Typography sx={{ fontWeight: 600 }}>{member.points}</Typography>
            </Stack>
          ))}
        </Stack>
      </Stack>
    </Paper>
  )
}

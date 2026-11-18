import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import type { TrackerSummary } from '../api/trackers'
import { ListIcon } from '../lists/listIcons'
import { PinButton } from '../lists/PinButton'
import { usePinTracker } from './queries'
import { describeShape } from './shape'

/**
 * A tracker as a card.
 *
 * <p>It names the **shape** rather than a count, which is what a collection's
 * card does and what a list's does: "kg, daily" is how a member tells their
 * morning weight from the one they keep for the dog, and a reading count tells
 * them nothing they can act on.
 */
export function TrackerCard({ tracker }: { tracker: TrackerSummary }) {
  const pin = usePinTracker()
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
        <CardActionArea
          component={RouterLink}
          to={`/trackers/${tracker.id}`}
          sx={{ p: 2, flex: 1, alignItems: 'flex-start' }}
        >
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
            <Avatar
              aria-hidden
              variant="rounded"
              sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
            >
              <ListIcon token={tracker.icon} />
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                {tracker.name}
              </Typography>
              {tracker.description && (
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  {tracker.description}
                </Typography>
              )}
              <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', rowGap: 0.5 }}>
                <Chip size="small" label={describeShape(tracker)} />
                {tracker.scope === 'PER_MEMBER' && (
                  <Chip size="small" variant="outlined" label="One series each" />
                )}
                {!tracker.owned && (
                  <Chip size="small" variant="outlined" label="Shared with you" />
                )}
              </Stack>
            </Box>
          </Stack>
        </CardActionArea>
        <Box sx={{ p: 1 }}>
          <PinButton
            name={tracker.name}
            pinned={tracker.pinned}
            onToggle={(pinned) => pin.mutate({ id: tracker.id, pinned })}
          />
        </Box>
      </Box>
    </Card>
  )
}

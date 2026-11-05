import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import type { CollectionSummary } from '../api/dataLists'
import { ListIcon } from '../lists/listIcons'

/**
 * No pin button, unlike a list card: collections have no per-member state
 * (adr/0029). The entry count takes its place, because "how much is in here"
 * is the thing a member actually wants from a card.
 */
export function CollectionCard({ collection }: { collection: CollectionSummary }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardActionArea
        component={RouterLink}
        to={`/collections/${collection.id}`}
        sx={{ p: 2, alignItems: 'flex-start' }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
          <Avatar
            aria-hidden
            variant="rounded"
            sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
          >
            <ListIcon token={collection.icon} />
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
              {collection.name}
            </Typography>
            {collection.description && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ overflowWrap: 'anywhere' }}
              >
                {collection.description}
              </Typography>
            )}
            <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', rowGap: 0.5 }}>
              <Chip
                size="small"
                label={
                  collection.entryCount === 1 ? '1 entry' : `${collection.entryCount} entries`
                }
              />
              {!collection.owned && <Chip size="small" variant="outlined" label="Shared with you" />}
            </Stack>
          </Box>
        </Stack>
      </CardActionArea>
    </Card>
  )
}

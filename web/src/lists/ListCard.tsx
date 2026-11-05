import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import type { ListSummary } from '../api/lists'
import { ListIcon } from './listIcons'
import { PinButton } from './PinButton'

export function ListCard({ list }: { list: ListSummary }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
        <CardActionArea
          component={RouterLink}
          to={`/lists/${list.id}`}
          sx={{ p: 2, flex: 1, alignItems: 'flex-start' }}
        >
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
            <Avatar
              aria-hidden
              variant="rounded"
              sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
            >
              <ListIcon token={list.icon} />
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                {list.name}
              </Typography>
              {list.description && (
                <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                  {list.description}
                </Typography>
              )}
              <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
                <Chip
                  size="small"
                  label={list.memberCount === 1 ? 'Only you' : `${list.memberCount} people`}
                />
                {!list.owned && <Chip size="small" variant="outlined" label="Shared with you" />}
              </Stack>
            </Box>
          </Stack>
        </CardActionArea>
        <Box sx={{ p: 1 }}>
          <PinButton id={list.id} name={list.name} pinned={list.pinned} />
        </Box>
      </Box>
    </Card>
  )
}

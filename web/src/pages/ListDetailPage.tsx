import { useEffect } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import { isNotFound } from '../api/http'
import { ListIcon } from '../lists/listIcons'
import { PinButton } from '../lists/PinButton'
import { useList, useTouchList } from '../lists/queries'

/** One list: who it is for, and - once items exist - what is on it. */
export function ListDetailPage() {
  const { listId } = useParams()
  const { data: list, isPending, error } = useList(listId)
  const touch = useTouchList()

  useEffect(() => {
    if (list) {
      touch(list.id)
    }
    // Once per list, guarded inside the hook: refetch-on-focus must not turn
    // into a write every time the member glances at another tab.
  }, [list, touch])

  if (isPending) {
    return <Typography role="status">Loading…</Typography>
  }

  if (error) {
    // A list the caller holds no grant on answers 404, exactly as a deleted one
    // does - the existence of the list is itself what R19 hides.
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Alert severity={isNotFound(error) ? 'info' : 'error'}>
          {isNotFound(error)
            ? 'That list no longer exists, or was never shared with you.'
            : 'Could not load that list.'}
        </Alert>
        <Button component={RouterLink} to="/lists" variant="text">
          Back to lists
        </Button>
      </Stack>
    )
  }

  return (
    <Stack spacing={2.5}>
      <Stack
        direction="row"
        spacing={1.5}
        sx={{ alignItems: 'flex-start', justifyContent: 'space-between' }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0 }}>
          <Avatar
            aria-hidden
            variant="rounded"
            sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
          >
            <ListIcon token={list.icon} />
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="h1" variant="h5" sx={{ overflowWrap: 'anywhere' }}>
              {list.name}
            </Typography>
            {list.description && (
              <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                {list.description}
              </Typography>
            )}
          </Box>
        </Stack>

        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
          <PinButton id={list.id} name={list.name} pinned={list.pinned} />
          {list.owned && (
            <Button
              component={RouterLink}
              to={`/lists/${list.id}/settings`}
              startIcon={<SettingsRounded />}
              size="small"
            >
              Settings
            </Button>
          )}
        </Stack>
      </Stack>

      <Paper
        component="section"
        elevation={0}
        sx={{ p: { xs: 2, sm: 2.5 }, border: 1, borderColor: 'divider' }}
      >
        <Stack spacing={1.5}>
          <Typography component="h2" variant="h6">
            Who can see this
          </Typography>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
            {list.members.map((member) => (
              <Chip
                key={member.id}
                label={member.owner ? `${member.displayName} (owner)` : member.displayName}
                variant={member.owner ? 'filled' : 'outlined'}
              />
            ))}
          </Stack>
        </Stack>
      </Paper>

      <Alert severity="info">
        Items are the next thing to be built. This list is ready for them.
      </Alert>
    </Stack>
  )
}

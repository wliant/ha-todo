import { useEffect } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import { isNotFound } from '../api/http'
import { ListAudience } from '../lists/ListAudience'
import { ItemsSection } from '../items/ItemsSection'
import { PointsPanel } from '../items/PointsPanel'
import { ListIcon } from '../lists/listIcons'
import { PinButton } from '../lists/PinButton'
import { useList, useListFields, useTouchList } from '../lists/queries'

/** One list: what is on it, with its audience in the header. */
export function ListDetailPage() {
  const { listId } = useParams()
  const { data: list, isPending, error } = useList(listId)
  const { data: fields } = useListFields(listId)
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

        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <ListAudience members={list.members} />
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

      <ItemsSection list={list} fields={fields ?? []} />

      <PointsPanel listId={list.id} />
    </Stack>
  )
}

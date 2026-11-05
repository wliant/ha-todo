import { useEffect } from 'react'
import {
  Link as RouterLink,
  Outlet,
  useLocation,
  useOutletContext,
  useParams,
} from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import Typography from '@mui/material/Typography'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import { isNotFound } from '../api/http'
import { ListAudience } from '../lists/ListAudience'
import { ActivitySection } from '../items/ActivitySection'
import { ItemsSection } from '../items/ItemsSection'
import { ListIcon } from '../lists/listIcons'
import { PinButton } from '../lists/PinButton'
import type { ListDetail, ListField } from '../api/lists'
import { useList, useListFields, useTouchList } from '../lists/queries'

/** What the tabs below the header are handed, so neither refetches the list. */
type ListContext = { list: ListDetail; fields: ListField[] }

/**
 * One list: a header, and a tab for each way of looking at it.
 *
 * <p>The tabs are bound to the pathname rather than to state, so they are
 * deep-linkable, correct under the back button, and have nothing to keep in
 * sync. `/lists/:listId` stays the index route, so every existing link, the
 * sidebar's selected check and the e2e suite's URLs are unaffected.
 */
export function ListDetailPage() {
  const { listId } = useParams()
  const { pathname } = useLocation()
  // Derived from the path, so there is no tab state to disagree with the URL.
  const tab = pathname.endsWith('/activity') ? 'activity' : 'items'
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
      {/* Below the breakpoint the actions sit under the name rather than
          beside it: squeezed into the same row they do not shrink, and the
          name is left wrapping one character per line. */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: 'flex-start', justifyContent: 'space-between' }}
      >
        <Stack
          direction="row"
          spacing={1.5}
          sx={{ alignItems: 'center', minWidth: 0, maxWidth: '100%' }}
        >
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

        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center', flexShrink: 0, flexWrap: 'wrap' }}
        >
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

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} aria-label="How to look at this list">
          <Tab label="Items" value="items" component={RouterLink} to={`/lists/${list.id}`} />
          <Tab
            label="Activity"
            value="activity"
            component={RouterLink}
            to={`/lists/${list.id}/activity`}
          />
        </Tabs>
      </Box>

      <Outlet context={{ list, fields: fields ?? [] } satisfies ListContext} />
    </Stack>
  )
}

export function ItemsTab() {
  const { list, fields } = useOutletContext<ListContext>()
  return <ItemsSection list={list} fields={fields} />
}

export function ActivityTab() {
  const { list } = useOutletContext<ListContext>()
  return <ActivitySection listId={list.id} />
}

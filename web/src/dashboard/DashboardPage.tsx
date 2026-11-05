import { useMemo } from 'react'
import Box from '@mui/material/Box'
import Stack from '@mui/material/Stack'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { Link as RouterLink, Outlet, useLocation, useSearchParams } from 'react-router-dom'
import type { DashboardScope } from '../api/dashboard'
import { DashboardItemDialog } from './DashboardItemDialog'
import { ViewContext } from './dashboardView'
import { UndatedPanel } from './UndatedPanel'
import { useScheduled, useUndated } from './queries'
import { monthRange, now } from './calendar'

/**
 * What a member needs to do, across every list they can see (R44, R45).
 *
 * <p>The ranges are tabs bound to the pathname, as the list's are. `scope`
 * stays a search param because it filters each view rather than being a
 * different view - so it is deep-linkable and adds no state.
 *
 * <p>The undated panel is a sibling of the outlet rather than part of each
 * view, so switching ranges does not refetch it.
 */
export function DashboardPage() {
  const { pathname } = useLocation()
  const [params, setParams] = useSearchParams()
  const scope: DashboardScope = params.get('scope') === 'mine' ? 'mine' : 'all'
  const tab = pathname.endsWith('/week') ? 'week' : pathname.endsWith('/month') ? 'month' : 'day'

  function open(itemId: string) {
    const next = new URLSearchParams(params)
    next.set('item', itemId)
    setParams(next)
  }

  function close() {
    const next = new URLSearchParams(params)
    next.delete('item')
    setParams(next)
  }

  function setScope(next: DashboardScope) {
    const updated = new URLSearchParams(params)
    if (next === 'all') {
      updated.delete('scope')
    } else {
      updated.set('scope', next)
    }
    setParams(updated)
  }

  const openId = params.get('item')

  return (
    <ViewContext.Provider value={{ scope, open }}>
      <Stack spacing={2.5}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Typography component="h1" variant="h5">
            My day
          </Typography>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={scope}
            onChange={(_event, next: DashboardScope | null) => next && setScope(next)}
            aria-label="Whose items"
          >
            <ToggleButton value="all">Everything</ToggleButton>
            <ToggleButton value="mine">Mine</ToggleButton>
          </ToggleButtonGroup>
        </Stack>

        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tab} aria-label="Which range">
            <Tab label="Today" value="day" component={RouterLink} to="/dashboard/day" />
            <Tab label="Week" value="week" component={RouterLink} to="/dashboard/week" />
            <Tab label="Month" value="month" component={RouterLink} to="/dashboard/month" />
          </Tabs>
        </Box>

        {/* Above the breakpoint the undated panel is a column beside the view;
            below it, it stacks underneath. */}
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={2.5}
          sx={{ alignItems: 'flex-start' }}
        >
          <Box sx={{ flex: 1, minWidth: 0, width: '100%' }}>
            <Outlet />
          </Box>
          <Box sx={{ width: { xs: '100%', md: '16rem' }, flexShrink: 0 }}>
            <UndatedPanel scope={scope} />
          </Box>
        </Stack>
      </Stack>

      {openId && <OpenItem id={openId} scope={scope} onClose={close} />}
    </ViewContext.Provider>
  )
}

/**
 * Finds the item behind `?item=` in whatever is already cached.
 *
 * <p>No request of its own: every view carries the full payload for exactly
 * this reason, so the popup opens instantly on a phone rather than fetching on
 * every tap. The month range is the widest, so it is the fallback when the
 * item is not in the view the member is looking at.
 */
function OpenItem({
  id,
  scope,
  onClose,
}: {
  id: string
  scope: DashboardScope
  onClose: () => void
}) {
  // Once per mount. Every range here snaps to a midnight, so recomputing would
  // usually give the same value - but "usually" is how phase C's activity
  // window ended up as a query key that moved under its own refetch.
  const month = useMemo(() => monthRange(now()), [])
  const { data: scheduled } = useScheduled(
    month.from.toISOString(),
    month.to.toISOString(),
    scope,
  )
  const { data: undated } = useUndated(scope)

  const item =
    scheduled?.items.find((candidate) => candidate.id === id) ??
    undated?.items.find((candidate) => candidate.id === id)

  if (!item) {
    return null
  }
  return <DashboardItemDialog item={item} onClose={onClose} />
}

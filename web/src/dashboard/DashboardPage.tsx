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
import type { CalendarEvent } from '../api/calendars'
import { DashboardEventDialog } from './DashboardEventDialog'
import { DashboardItemDialog } from './DashboardItemDialog'
import { OutsideWindowNotice } from './OutsideWindowNotice'
import { StaleFeedsNotice } from './StaleFeedsNotice'
import { ViewContext } from './dashboardView'
import { UndatedPanel } from './UndatedPanel'
import { useScheduled, useUndated } from './queries'
import { useHouseholdZone } from '../app/useHouseholdZone'
import { useState } from 'react'
import {
  addDays,
  dayKey,
  dayRange,
  monthRange,
  now,
  parseAnchor,
  weekRange,
  windowOf,
  type Civil,
  type Range,
} from './calendar'
import { PeriodControls } from './PeriodControls'

/**
 * What a member needs to do, across every list they can see (R44, R45).
 *
 * <p>The ranges are tabs bound to the pathname, as the list's are. `scope` and
 * the period on screen stay search params because they parameterise each view
 * rather than being a different view - so both are deep-linkable and neither
 * adds state (adr/0037).
 *
 * <p>The undated panel is a sibling of the outlet rather than part of each
 * view, so switching ranges does not refetch it.
 */
export function DashboardPage() {
  const { pathname } = useLocation()
  // The one place this page asks what zone it is in; every view reads it from
  // the context rather than calling the hook again.
  const zone = useHouseholdZone()
  // Held in state rather than the URL, unlike the item popup: a collapsed event
  // has no id of its own to link to, and the whole event is already in hand.
  const [openedEvent, setOpenedEvent] = useState<CalendarEvent | null>(null)
  const [params, setParams] = useSearchParams()
  const scope: DashboardScope = params.get('scope') === 'mine' ? 'mine' : 'all'
  const tab = pathname.endsWith('/week') ? 'week' : pathname.endsWith('/month') ? 'month' : 'day'
  // The day being looked at (R76). Today unless the address says otherwise, and
  // today for anything unparseable - this is the landing page, so a bad ?on=
  // must not be a member who cannot get in.
  const anchor = useMemo(() => parseAnchor(params.get('on'), zone), [params, zone])
  const visible = useMemo(
    () =>
      tab === 'week' ? weekRange(anchor) : tab === 'month' ? monthRange(anchor) : dayRange(anchor),
    [tab, anchor],
  )
  const activeWindow = useMemo(() => windowOf(visible, zone), [visible, zone])

  /** Today drops the param rather than writing it, as scope=all does. */
  function moveTo(day: Civil) {
    const next = new URLSearchParams(params)
    if (dayKey(day) === dayKey(now(zone))) {
      next.delete('on')
    } else {
      next.set('on', dayKey(day))
    }
    // A popup belongs to the period it was opened from.
    next.delete('item')
    setOpenedEvent(null)
    setParams(next)
  }

  function open(itemId: string) {
    setOpenedEvent(null)
    const next = new URLSearchParams(params)
    next.set('item', itemId)
    setParams(next)
  }

  /** One popup at a time, as on a list (0005 R38). */
  function openEvent(event: CalendarEvent) {
    close()
    setOpenedEvent(event)
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

  /** Keeps the period when the range changes (R76). */
  function withPeriod(path: string): string {
    const carried = new URLSearchParams(params)
    carried.delete('item')
    const query = carried.toString()
    return query ? `${path}?${query}` : path
  }

  return (
    <ViewContext.Provider value={{ scope, zone, anchor, open, openEvent }}>
      <Stack spacing={2.5}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Typography component="h1" variant="h5">
            Agenda
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

        {/* The active tab's own window, so this shares the view's cache entry
            and costs no request of its own. */}
        <Notices window={activeWindow} scope={scope} visible={visible} />

        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          {/* Named for the range rather than for the present: a tab that can
              show another week is a tab whose "This week" label is a lie
              (R73). The routes are unchanged. */}
          <Tabs value={tab} aria-label="Which range">
            <Tab label="Day" value="day" component={RouterLink} to={withPeriod('/dashboard/day')} />
            <Tab label="Week" value="week" component={RouterLink} to={withPeriod('/dashboard/week')} />
            <Tab label="Month" value="month" component={RouterLink} to={withPeriod('/dashboard/month')} />
          </Tabs>
        </Box>

        <PeriodControls range={tab} anchor={anchor} zone={zone} onMove={moveTo} />

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

      {openId && (
        <OpenItem id={openId} scope={scope} zone={zone} anchor={anchor} onClose={close} />
      )}
      {openedEvent && (
        <DashboardEventDialog
          event={openedEvent}
          zone={zone}
          onClose={() => setOpenedEvent(null)}
        />
      )}
    </ViewContext.Provider>
  )
}

/**
 * Finds the item behind `?item=` in whatever is already cached.
 *
 * <p>No request of its own: every view carries the full payload for exactly
 * this reason, so the popup opens instantly on a phone rather than fetching on
 * every tap. The month range around the anchor is the widest, so it is the
 * fallback when the item is not in the view the member is looking at.
 */
function OpenItem({
  id,
  scope,
  zone,
  anchor,
  onClose,
}: {
  id: string
  scope: DashboardScope
  zone: string
  anchor: Civil
  onClose: () => void
}) {
  // Once per mount. Every range here snaps to a midnight, so recomputing would
  // usually give the same value - but "usually" is how phase C's activity
  // window ended up as a query key that moved under its own refetch.
  // The *anchor's* month, not the clock's: an item opened from November while
  // looking at February has to be found in the payload February asked for.
  const month = useMemo(() => windowOf(monthRange(anchor), zone), [anchor, zone])
  const { data: scheduled } = useScheduled(month.from, month.to, scope)
  const { data: undated } = useUndated(scope)

  const item =
    scheduled?.items.find((candidate) => candidate.id === id) ??
    undated?.items.find((candidate) => candidate.id === id)

  if (!item) {
    return null
  }
  return <DashboardItemDialog item={item} onClose={onClose} />
}

/**
 * What the agenda has to admit before it draws anything: a calendar that broke
 * (0007 R71), and a period nobody has read (0008 R77).
 *
 * <p>Asks for the window the visible view is already asking for - the same
 * query key, so the same cache entry - because neither notice depends on a
 * different range and a second one here would double every page load's
 * requests.
 */
function Notices({
  window,
  scope,
  visible,
}: {
  window: { from: string; to: string }
  scope: DashboardScope
  visible: Range
}) {
  const { data } = useScheduled(window.from, window.to, scope)
  return (
    <>
      <StaleFeedsNotice feeds={data?.staleFeeds ?? []} />
      {/* `to` is exclusive on a range and inclusive to a reader. */}
      <OutsideWindowNotice
        from={visible.from}
        to={addDays(visible.to, -1)}
        window={data?.eventWindow}
      />
    </>
  )
}

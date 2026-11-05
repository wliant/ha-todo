import { useMemo } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { DashboardEventChip } from './DashboardEventChip'
import { DashboardItemChip } from './DashboardItemChip'
import { useDashboardView } from './dashboardView'
import { dayKey, isSameDay, now, weekDays, weekRange, windowOf } from './calendar'
import { byDayEntries } from './entries'
import { periodName } from './period'
import { useScheduled } from './queries'

/**
 * This week, with its seven days as columns.
 *
 * <p>Descriptions only (R44): a date grid carrying chips is unreadable on a
 * phone. Events sit in the same columns as the chores (0007 R67), outlined
 * rather than filled so the two are never confused. Below the breakpoint the columns scroll sideways rather than being
 * squeezed to nothing - the page body must never scroll horizontally, so the
 * overflow lives here.
 */
export function WeekView() {
  const { scope, zone, anchor, open, openEvent } = useDashboardView()
  // Once per mount: a range snapped to midnight is stable through a day, and
  // computing it during every render would make the query key - and therefore
  // the refetch - depend on when React happened to re-render.
  // Layout follows the anchor; the highlight below follows the clock. Keeping
  // those apart is the whole of 0008's first consequence.
  const today = useMemo(() => now(zone), [zone])
  const window = useMemo(() => windowOf(weekRange(anchor), zone), [anchor, zone])
  const { data, isPending } = useScheduled(window.from, window.to, scope)
  const days = weekDays(anchor)
  const grouped = byDayEntries(data?.items ?? [], data?.events ?? [], zone)

  return (
    <Stack spacing={1.5}>
      <Typography component="h2" variant="h6" aria-live="polite">
        {periodName('week', anchor, today)}
      </Typography>

      {isPending && <Typography role="status">Loading…</Typography>}

      <Box sx={{ overflowX: 'auto' }}>
        <Stack direction="row" spacing={1} sx={{ minWidth: '44rem' }}>
          {days.map((day) => {
            const entries = grouped.get(dayKey(day)) ?? []
            return (
              <Paper
                key={dayKey(day)}
                variant="outlined"
                sx={{
                  flex: 1,
                  minWidth: '6rem',
                  p: 1,
                  borderColor: isSameDay(day, today) ? 'primary.main' : 'divider',
                }}
              >
                <Stack spacing={0.5}>
                  <Typography
                    variant="caption"
                    sx={{
                      fontWeight: isSameDay(day, today) ? 700 : 400,
                      color: isSameDay(day, today) ? 'primary.main' : 'text.secondary',
                    }}
                  >
                    {day.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })}
                  </Typography>
                  {entries.map((entry) =>
                    entry.kind === 'item' ? (
                      <DashboardItemChip
                        key={entry.key}
                        item={entry.item}
                        onOpen={() => open(entry.item.id)}
                      />
                    ) : (
                      <DashboardEventChip
                        key={entry.key}
                        event={entry.event}
                        zone={zone}
                        onOpen={() => openEvent(entry.event)}
                      />
                    ),
                  )}
                </Stack>
              </Paper>
            )
          })}
        </Stack>
      </Box>
    </Stack>
  )
}

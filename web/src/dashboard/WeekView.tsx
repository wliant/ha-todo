import { useMemo } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { DashboardItemChip } from './DashboardItemChip'
import { useDashboardView } from './dashboardView'
import { byDay, dayKey, isSameDay, now, weekDays, weekRange, windowOf } from './calendar'
import { useScheduled } from './queries'

/**
 * This week, with its seven days as columns.
 *
 * <p>Descriptions only (R44): a date grid carrying chips is unreadable on a
 * phone. Below the breakpoint the columns scroll sideways rather than being
 * squeezed to nothing - the page body must never scroll horizontally, so the
 * overflow lives here.
 */
export function WeekView() {
  const { scope, zone, open } = useDashboardView()
  // Once per mount: a range snapped to midnight is stable through a day, and
  // computing it during every render would make the query key - and therefore
  // the refetch - depend on when React happened to re-render.
  const today = useMemo(() => now(zone), [zone])
  const window = useMemo(() => windowOf(weekRange(today), zone), [today, zone])
  const { data, isPending } = useScheduled(window.from, window.to, scope)
  const days = weekDays(today)
  const grouped = byDay(data?.items ?? [], (item) => item.dueAt, zone)

  return (
    <Stack spacing={1.5}>
      <Typography component="h2" variant="h6">
        This week
      </Typography>

      {isPending && <Typography role="status">Loading…</Typography>}

      <Box sx={{ overflowX: 'auto' }}>
        <Stack direction="row" spacing={1} sx={{ minWidth: '44rem' }}>
          {days.map((day) => {
            const items = grouped.get(dayKey(day)) ?? []
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
                  {items.map((item) => (
                    <DashboardItemChip key={item.id} item={item} onOpen={() => open(item.id)} />
                  ))}
                </Stack>
              </Paper>
            )
          })}
        </Stack>
      </Box>
    </Stack>
  )
}

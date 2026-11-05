import { useMemo } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { DashboardItemChip } from './DashboardItemChip'
import { useDashboardView } from './dashboardView'
import {
  byDay,
  dayKey,
  isSameDay,
  isSameMonth,
  monthGrid,
  monthRange,
  now,
} from './calendar'
import { useScheduled } from './queries'

const COLUMN_HEADS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/**
 * This month, as a calendar.
 *
 * <p>Always six rows of seven, so the grid does not change height between
 * months and make the page jump. Descriptions only, as in the week view.
 */
export function MonthView() {
  const { scope, open } = useDashboardView()
  // Once per mount: a range snapped to midnight is stable through a day, and
  // computing it during every render would make the query key - and therefore
  // the refetch - depend on when React happened to re-render.
  const today = useMemo(() => now(), [])
  const range = useMemo(() => monthRange(today), [today])
  const { data, isPending } = useScheduled(
    range.from.toISOString(),
    range.to.toISOString(),
    scope,
  )
  const grid = monthGrid(today)
  const grouped = byDay(data?.items ?? [], (item) => item.dueAt)

  return (
    <Stack spacing={1.5}>
      <Typography component="h2" variant="h6">
        {today.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
      </Typography>

      {isPending && <Typography role="status">Loading…</Typography>}

      <Box sx={{ overflowX: 'auto' }}>
        <Box sx={{ minWidth: '44rem' }}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(7, 1fr)',
              gap: 0.5,
              mb: 0.5,
            }}
          >
            {COLUMN_HEADS.map((head) => (
              <Typography key={head} variant="caption" color="text.secondary" align="center">
                {head}
              </Typography>
            ))}
          </Box>
          <Box
            sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 0.5 }}
            role="grid"
            aria-label="This month"
          >
            {grid.map((day) => {
              const items = grouped.get(dayKey(day)) ?? []
              const thisMonth = isSameMonth(day, today)
              return (
                <Paper
                  key={dayKey(day)}
                  variant="outlined"
                  role="gridcell"
                  sx={{
                    p: 0.5,
                    minHeight: '4.5rem',
                    borderColor: isSameDay(day, today) ? 'primary.main' : 'divider',
                    // The leading and trailing days are kept rather than left
                    // blank, so a date at the edge of a month is still visible.
                    opacity: thisMonth ? 1 : 0.5,
                  }}
                >
                  <Stack spacing={0.25}>
                    <Typography
                      variant="caption"
                      sx={{
                        fontWeight: isSameDay(day, today) ? 700 : 400,
                        color: isSameDay(day, today) ? 'primary.main' : 'text.secondary',
                      }}
                    >
                      {day.getDate()}
                    </Typography>
                    {items.map((item) => (
                      <DashboardItemChip
                        key={item.id}
                        item={item}
                        onOpen={() => open(item.id)}
                      />
                    ))}
                  </Stack>
                </Paper>
              )
            })}
          </Box>
        </Box>
      </Box>
    </Stack>
  )
}

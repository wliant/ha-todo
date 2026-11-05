import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useSearchParams } from 'react-router-dom'
import type { DashboardScope } from '../api/dashboard'
import { DashboardItemChip } from './DashboardItemChip'
import { useUndated } from './queries'

/**
 * Everything nobody has dated (R45).
 *
 * <p>Rendered by the layout, beside all three views, so switching between them
 * is not a refetch. It is long by construction and will stay long: after
 * adr/0022 every list declares a due date and nobody has filled most of them
 * in, so the server caps the page and tells us the real total.
 */
export function UndatedPanel({ scope }: { scope: DashboardScope }) {
  const { data, isPending } = useUndated(scope)
  const [params, setParams] = useSearchParams()

  function open(id: string) {
    const next = new URLSearchParams(params)
    next.set('item', id)
    setParams(next)
  }

  return (
    <Paper
      component="section"
      elevation={0}
      sx={{ p: 2, border: 1, borderColor: 'divider', minWidth: 0 }}
    >
      <Stack spacing={1}>
        <Typography component="h2" variant="subtitle1">
          No date yet
        </Typography>

        {isPending && <Typography role="status" variant="body2">Loading…</Typography>}

        {data && data.items.length === 0 && (
          <Typography role="status" variant="body2" color="text.secondary">
            Everything has a date.
          </Typography>
        )}

        <Stack spacing={0.5}>
          {data?.items.map((item) => (
            <DashboardItemChip key={item.id} item={item} onOpen={() => open(item.id)} />
          ))}
        </Stack>

        {data && data.total > data.items.length && (
          <Typography variant="caption" color="text.secondary">
            and {data.total - data.items.length} more
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}

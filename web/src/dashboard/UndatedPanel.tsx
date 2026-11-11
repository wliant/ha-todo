import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useSearchParams } from 'react-router-dom'
import type { DashboardScope } from '../api/dashboard'
import { DashboardItemChip } from './DashboardItemChip'
import { onlyLists } from './listFilter'
import { useUndated } from './queries'

/**
 * Everything nobody has dated (R45).
 *
 * <p>Rendered by the layout, beside all three views, so switching between them
 * is not a refetch. It is long by construction and will stay long: after
 * adr/0022 every list declares a due date and nobody has filled most of them
 * in, so the server caps the page and tells us the real total.
 */
export function UndatedPanel({ scope, lists }: { scope: DashboardScope; lists: string[] }) {
  const { data, isPending } = useUndated(scope)
  const rows = onlyLists(data?.items ?? [], lists)
  // The page the server sent was capped, so it is holding more than it showed
  // us. Unfiltered we can say how many; filtered we cannot, because the rows
  // it withheld were never narrowed - see below.
  const capped = data ? data.total > data.items.length : false
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

        {data && rows.length === 0 && (
          <Typography role="status" variant="body2" color="text.secondary">
            {lists.length > 0 ? 'Nothing from the lists you picked.' : 'Everything has a date.'}
          </Typography>
        )}

        <Stack spacing={0.5}>
          {rows.map((item) => (
            <DashboardItemChip key={item.id} item={item} onOpen={() => open(item.id)} />
          ))}
        </Stack>

        {/* A client-side filter cannot narrow a server-side count, so a
            filtered panel says that there may be more rather than inventing a
            number out of rows it never saw (adr/0047). */}
        {capped && (
          <Typography variant="caption" color="text.secondary">
            {lists.length > 0
              ? 'and possibly more'
              : `and ${data!.total - data!.items.length} more`}
          </Typography>
        )}
      </Stack>
    </Paper>
  )
}

import { useState } from 'react'
import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import Button from '@mui/material/Button'
import Collapse from '@mui/material/Collapse'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { DashboardScope } from '../api/dashboard'
import { AgendaItemRow } from './AgendaItemRow'
import { civilIn, now, type Zone } from './calendar'
import { useDashboardView } from './dashboardView'
import { latenessLabel } from './period'
import { onlyLists } from './listFilter'
import { useOverdue } from './queries'

/**
 * What is late (0011 R88-R93).
 *
 * <p>Above the period rather than beside it, and that placement is the design:
 * at `xs` the right-hand column stacks *below* the outlet, so the undated
 * panel's home would put the highest-priority category in the product under a
 * six-row month grid. See adr/0040.
 *
 * <p>**It does not depend on the period on screen.** No window, no anchor -
 * only the scope. Navigating to February cannot change what is overdue (R89),
 * and the shared cache entry means stepping through months does not refetch
 * this.
 *
 * <p>Collapsed to a count by default. A household that is keeping up sees
 * nothing at all, because a strip reading "nothing is late" is a line of
 * furniture on every view forever.
 */
export function OverduePanel({ scope, lists }: { scope: DashboardScope; lists: string[] }) {
  const { zone, open, complete, writeNote, lapse } = useDashboardView()
  const { data } = useOverdue(scope)
  const [expanded, setExpanded] = useState(false)

  const rows = onlyLists(data?.items ?? [], lists)
  const capped = data ? data.total > data.items.length : false

  // Nothing while it loads, too: this sits above the grid, and a placeholder
  // here pushes the whole agenda down and then lets it spring back. Nothing
  // either when the filter leaves no late work: a strip announcing other
  // lists' arrears is the one thing a narrowed agenda promised not to show.
  if (!data || rows.length === 0) {
    return null
  }

  return (
    <Alert severity="warning" role="status" icon={false} sx={{ '& .MuiAlert-message': { width: '100%' } }}>
      <Stack spacing={1} sx={{ width: '100%' }}>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}
        >
          {/* The count is in the text rather than only in a badge, because the
              `role="status"` is what tells a member who cannot see the grid
              that something is behind them. */}
          {/* Filtered, the count is of the rows on screen: `total` counts
              what the server holds across every list (adr/0047). */}
          <AlertTitle sx={{ mb: 0 }}>
            {headline(lists.length > 0 ? rows.length : data.total)}
          </AlertTitle>
          <Button size="small" color="inherit" onClick={() => setExpanded(!expanded)}>
            {expanded ? 'Hide' : 'Show'}
          </Button>
        </Stack>

        <Collapse in={expanded} unmountOnExit>
          <Stack spacing={1}>
            {rows.map((item) => (
              <AgendaItemRow
                key={item.id}
                item={item}
                caption={caption(item.dueAt, item.listName, zone)}
                onOpen={open}
                onComplete={() => complete(item)}
                onWriteNote={() => writeNote(item)}
                onLapse={() => lapse(item)}
              />
            ))}
            {capped && (
              <Typography variant="caption">
                {lists.length > 0 ? 'and possibly more' : `and ${data.total - data.items.length} more`}
              </Typography>
            )}
          </Stack>
        </Collapse>
      </Stack>
    </Alert>
  )
}

/**
 * "item", not "chore". The product calls the things on a list items everywhere
 * a member can act on one - the add form, the row, the completion snackbar -
 * and the agenda is the one surface that reads across every list, so a second
 * word for the same thing is most confusing exactly here. "Chore" survives in
 * comments and in docs, where it is describing the domain rather than labelling
 * a control.
 */
function headline(total: number): string {
  return total === 1 ? '1 item is overdue' : `${total} items are overdue`
}

/**
 * The list it came from, and how late it is.
 *
 * <p>`now(zone)` rather than the anchor: this is the one thing on the page that
 * is about the present, and a member who navigated to March must still be told
 * that this chore was due last Tuesday - not that it is due in four months.
 */
function caption(dueAt: string | null, listName: string, zone: Zone): string {
  if (!dueAt) {
    return listName
  }
  return `${listName} · ${latenessLabel(civilIn(new Date(dueAt), zone), now(zone))}`
}

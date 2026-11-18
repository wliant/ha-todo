import { Suspense, lazy, useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import Paper from '@mui/material/Paper'
import Skeleton from '@mui/material/Skeleton'
import Snackbar from '@mui/material/Snackbar'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import type { Reading } from '../api/trackerReadings'
import { ListIcon } from '../lists/listIcons'
import { ListAudience } from '../lists/ListAudience'
import { useHouseholdZone } from '../app/useHouseholdZone'
import { ReadingDialog } from '../trackers/ReadingDialog'
import { ReadingsTable } from '../trackers/ReadingsTable'
import { TrackerHeadline } from '../trackers/TrackerHeadline'
import { RangeChips } from '../trackers/RangeChips'
import { DEFAULT_RANGE, parseRange, windowFor } from '../trackers/range'
import { describeShape } from '../trackers/shape'
import {
  useDeleteReading,
  useReadings,
  useSeries,
  useTouchTracker,
  useTracker,
} from '../trackers/queries'

/**
 * Loaded only when a member actually opens a tracker.
 *
 * <p>`@mui/x-charts` and its bundled d3 are the heaviest dependency in the
 * app, and most sessions never come here - the same trade `items/DetailEditor`
 * makes with ProseMirror, for the same reason (adr/0053). The skeleton is the
 * chart's height, so the table below it does not jump when the chunk lands.
 */
const TrackerChart = lazy(() =>
  import('../trackers/TrackerChart').then((module) => ({ default: module.TrackerChart })),
)

const CHART_HEIGHT = 260

/**
 * One tracker: the shape its numbers make, then the numbers
 * (requirements/0019 R167).
 */
export function TrackerDetailPage() {
  const { trackerId } = useParams()
  const [params, setParams] = useSearchParams()
  const zone = useHouseholdZone()
  const touch = useTouchTracker()

  const [recording, setRecording] = useState(false)
  const [editing, setEditing] = useState<Reading | null>(null)
  const [deleting, setDeleting] = useState<Reading | null>(null)
  const [said, setSaid] = useState<string | null>(null)

  const { data: tracker, isPending, isError } = useTracker(trackerId)
  const range = parseRange(params.get('range'))
  // Depends on the tracker's period, so it waits for it rather than guessing a
  // grain the server would refuse.
  const window = useMemo(
    () => (tracker ? windowFor(range, tracker.period, zone) : { by: 'DAY' as const }),
    [range, tracker, zone],
  )
  const { data: series, isPending: seriesPending, isError: seriesError } = useSeries(
    tracker ? trackerId : undefined,
    window,
  )
  const { data: readings } = useReadings(trackerId)
  const removeReading = useDeleteReading()

  useEffect(() => {
    if (trackerId) {
      touch(trackerId)
    }
    // `touch` is stable per render but not referentially; the module-level
    // guard inside it is what makes this once-per-session rather than this
    // dependency list.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trackerId])

  function setRange(next: string) {
    const updated = new URLSearchParams(params)
    if (next === DEFAULT_RANGE) {
      updated.delete('range')
    } else {
      updated.set('range', next)
    }
    // `replace`: four taps on the range chips are one decision, not four
    // history entries (adr/0045).
    setParams(updated, { replace: true })
  }

  if (isPending) {
    return <Typography role="status">Loading…</Typography>
  }
  if (isError || !tracker) {
    return <Alert severity="error">That tracker is not here.</Alert>
  }

  return (
    <Stack spacing={2.5}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { sm: 'flex-start' }, justifyContent: 'space-between' }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0 }}>
          <Avatar
            aria-hidden
            variant="rounded"
            sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
          >
            <ListIcon token={tracker.icon} />
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="h1" variant="h5" sx={{ overflowWrap: 'anywhere' }}>
              {tracker.name}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {describeShape(tracker)}
            </Typography>
          </Box>
        </Stack>

        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <ListAudience members={tracker.members} />
          <Button
            variant="contained"
            startIcon={<AddRounded />}
            onClick={() => setRecording(true)}
          >
            Record
          </Button>
          {tracker.owned && (
            <Button
              component={RouterLink}
              to={`/trackers/${tracker.id}/settings`}
              startIcon={<SettingsRounded />}
            >
              Settings
            </Button>
          )}
        </Stack>
      </Stack>

      {tracker.description && (
        <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {tracker.description}
        </Typography>
      )}

      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 2.5 } }}>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={1.5}
            sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-start' } }}
          >
            {series ? (
              <TrackerHeadline series={series} />
            ) : (
              <Skeleton variant="text" width="8rem" height={48} />
            )}
            <RangeChips value={range} onChange={setRange} />
          </Stack>

          {/* Three states for the chart region, as every other region in the
              app has to have: a failed request must not read as "nothing
              recorded". */}
          {seriesPending && <Skeleton variant="rounded" height={CHART_HEIGHT} />}
          {seriesError && <Alert severity="error">Could not read this tracker’s numbers.</Alert>}
          {series &&
            (series.series.length === 0 ? (
              <Typography role="status" color="text.secondary" sx={{ py: 4 }}>
                Nothing recorded in this period.
              </Typography>
            ) : (
              <Suspense fallback={<Skeleton variant="rounded" height={CHART_HEIGHT} />}>
                <TrackerChart series={series} height={CHART_HEIGHT} />
              </Suspense>
            ))}
        </Stack>
      </Paper>

      <Typography component="h2" variant="h6">
        Numbers
      </Typography>
      <ReadingsTable
        readings={readings ?? []}
        period={tracker.period}
        scope={tracker.scope}
        unit={tracker.unit}
        decimals={tracker.decimals}
        onEdit={setEditing}
        onDelete={setDeleting}
      />

      {(recording || editing) && (
        <ReadingDialog
          // Keyed, so the form seeds itself afresh each time: its value is
          // initial content and never re-syncs.
          key={editing?.id ?? 'new'}
          trackerId={tracker.id}
          period={tracker.period}
          unit={tracker.unit}
          editing={editing ?? undefined}
          onClose={() => {
            setRecording(false)
            setEditing(null)
          }}
          onSaved={setSaid}
        />
      )}

      <Dialog open={deleting !== null} onClose={() => setDeleting(null)}>
        <DialogTitle>Delete this number?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            It leaves the chart and the table. Nothing else changes.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleting(null)}>Cancel</Button>
          <Button
            color="error"
            onClick={() => {
              if (deleting) {
                removeReading.mutate(deleting.id, {
                  onSuccess: () => setSaid('Deleted.'),
                })
              }
              setDeleting(null)
            }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={said !== null}
        message={said ?? ''}
        autoHideDuration={4000}
        onClose={() => setSaid(null)}
      />
    </Stack>
  )
}

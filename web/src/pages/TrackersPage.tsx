import { useMemo } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import type { TrackerScopeFilter } from '../api/trackers'
import { TrackerCard } from '../trackers/TrackerCard'
import { useTrackers } from '../trackers/queries'

/**
 * Every tracker a member can see (requirements/0019).
 *
 * <p>No search box, unlike collections: a household has a handful of trackers
 * and their names are on the cards. The thing a collection's search exists for
 * - finding a sentence inside sixty recipes - has no equivalent here, because
 * a tracker's content is numbers.
 *
 * <p>The scope filter lives in the address, so a view is a link (adr/0045).
 */
export function TrackersPage() {
  const [params, setParams] = useSearchParams()
  const scope = (params.get('scope') as TrackerScopeFilter | null) ?? 'all'

  const filters = useMemo(() => ({ scope }), [scope])
  const { data: trackers, isPending, isError } = useTrackers(filters)

  function setScope(next: TrackerScopeFilter) {
    const updated = new URLSearchParams(params)
    if (next === 'all') {
      updated.delete('scope')
    } else {
      updated.set('scope', next)
    }
    setParams(updated, { replace: true })
  }

  return (
    <Stack spacing={2.5}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
      >
        <Typography component="h1" variant="h5">
          Trackers
        </Typography>
        <Button
          component={RouterLink}
          to="/trackers/new"
          variant="contained"
          startIcon={<AddRounded />}
        >
          New tracker
        </Button>
      </Stack>

      <ToggleButtonGroup
        exclusive
        size="small"
        value={scope}
        onChange={(_event, next: TrackerScopeFilter | null) => next && setScope(next)}
        aria-label="Which trackers"
      >
        <ToggleButton value="all">All</ToggleButton>
        <ToggleButton value="owned">Mine</ToggleButton>
        <ToggleButton value="shared">Shared with me</ToggleButton>
      </ToggleButtonGroup>

      {/* Three states, not two. A list drawn from `data ?? []` is empty while
          the request is in flight *and* empty when it failed, and in both
          cases says "no trackers yet" - a confident lie about somebody's own
          data (0012's NavGroup bug). */}
      {isPending && <Typography role="status">Loading trackers…</Typography>}
      {isError && <Alert severity="error">Could not load your trackers.</Alert>}

      {trackers && trackers.length === 0 && (
        <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
          <Typography role="status" color="text.secondary">
            {scope === 'all'
              ? 'No trackers yet.'
              : 'Nothing here. Try another filter.'}
          </Typography>
          {scope === 'all' && (
            <Typography variant="body2" color="text.secondary">
              A tracker is a number you keep: what you weigh, what the groceries cost, how long
              somebody read.
            </Typography>
          )}
        </Stack>
      )}

      {trackers && trackers.length > 0 && (
        <Box
          sx={{
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
          }}
        >
          {trackers.map((tracker) => (
            <TrackerCard key={tracker.id} tracker={tracker} />
          ))}
        </Box>
      )}
    </Stack>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import InputAdornment from '@mui/material/InputAdornment'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import type { ListIcon as ListIconToken, ListScope } from '../api/lists'
import { ICON_TOKENS, iconLabel } from '../lists/listIconTokens'
import { ListCard } from '../lists/ListCard'
import { useLists } from '../lists/queries'

/**
 * Every list this member can reach, searchable and filterable (R26).
 *
 * Filter state lives in the URL, so a filtered view is linkable and the back
 * button works - and that is router state, not layout state. The filtering
 * itself happens on the server, so the page never depends on having fetched
 * everything.
 */
export function AllListsPage() {
  const [params, setParams] = useSearchParams()

  const q = params.get('q') ?? ''
  const scope = (params.get('scope') as ListScope | null) ?? 'all'
  const icon: ListIconToken | '' = (params.get('icon') as ListIconToken | null) ?? ''
  const pinned = params.get('pinned') === 'true'

  // The URL holds what was typed, so the field needs no state of its own and
  // a shared link reproduces the search exactly. Only the value handed to the
  // query is debounced, so typing is one request per pause rather than one per
  // keystroke.
  const [debounced, setDebounced] = useState(q)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(q), 250)
    return () => clearTimeout(timer)
  }, [q])

  const filters = useMemo(
    () => ({ q: debounced, scope, icon, pinned }),
    [debounced, scope, icon, pinned],
  )
  const { data: lists, isPending, isError } = useLists(filters)

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '' || value === 'all' || value === 'false') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    }
    setParams(next, { replace: true })
  }

  const filtering = Boolean(debounced || icon || pinned || scope !== 'all')

  return (
    <Stack spacing={2.5}>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}
      >
        {/* e2e's ui_sign_in waits on this heading; it is a shared contract. */}
        <Typography component="h1" variant="h5">
          Lists
        </Typography>
        <Button
          component={RouterLink}
          to="/lists/new"
          variant="contained"
          startIcon={<AddRounded />}
        >
          New list
        </Button>
      </Stack>

      <Stack spacing={1.5}>
        <TextField
          label="Search lists"
          value={q}
          onChange={(event) => update({ q: event.target.value })}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchRounded fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />

        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={scope}
            onChange={(_event, next: ListScope | null) => update({ scope: next })}
            aria-label="Whose lists"
          >
            <ToggleButton value="all">All</ToggleButton>
            <ToggleButton value="owned">Mine</ToggleButton>
            <ToggleButton value="shared">Shared with me</ToggleButton>
          </ToggleButtonGroup>

          <Chip
            label="Pinned only"
            variant={pinned ? 'filled' : 'outlined'}
            color={pinned ? 'primary' : 'default'}
            onClick={() => update({ pinned: pinned ? 'false' : 'true' })}
          />

          <TextField
            select
            size="small"
            label="Icon"
            value={icon}
            onChange={(event) => update({ icon: event.target.value })}
            sx={{ minWidth: '10rem' }}
          >
            <MenuItem value="">Any icon</MenuItem>
            {ICON_TOKENS.map((token) => (
              <MenuItem key={token} value={token}>
                {iconLabel(token)}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </Stack>

      {isPending && <Typography role="status">Loading your lists…</Typography>}
      {isError && <Alert severity="error">Could not load your lists.</Alert>}

      {lists && lists.length === 0 && (
        <Stack spacing={1.5} sx={{ alignItems: 'flex-start' }}>
          <Typography role="status" color="text.secondary">
            {filtering ? 'No lists match that.' : 'No lists yet.'}
          </Typography>
          {!filtering && (
            <Button component={RouterLink} to="/lists/new" variant="contained">
              Create your first list
            </Button>
          )}
        </Stack>
      )}

      {lists && lists.length > 0 && (
        <Box
          sx={{
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(auto-fill, minmax(18rem, 1fr))' },
          }}
        >
          {lists.map((list) => (
            <ListCard key={list.id} list={list} />
          ))}
        </Box>
      )}

    </Stack>
  )
}

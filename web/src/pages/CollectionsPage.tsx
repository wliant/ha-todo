import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import InputAdornment from '@mui/material/InputAdornment'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import type { CollectionScope } from '../api/dataLists'
import { CollectionCard } from '../data/CollectionCard'
import { EntryRow } from '../data/EntryRow'
import { useCollections, useGlobalEntrySearch } from '../data/queries'

/**
 * Collections, and one search box that reads across all of them (R54, R58).
 *
 * The search answers "where did I write that down", so it searches *entries*
 * and not collection names - the names are right there on the cards. Typing
 * replaces the cards with results, because a member who is searching is not
 * browsing.
 *
 * Search state lives in the URL, so a search is linkable and the back button
 * works. That is router state, not layout state (adr/0015).
 */
export function CollectionsPage() {
  const [params, setParams] = useSearchParams()

  const q = params.get('q') ?? ''
  const scope = (params.get('scope') as CollectionScope | null) ?? 'all'

  const [debounced, setDebounced] = useState(q)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(q), 250)
    return () => clearTimeout(timer)
  }, [q])

  const filters = useMemo(() => ({ scope }), [scope])
  const { data: collections, isPending, isError } = useCollections(filters)

  const searchQuery = useMemo(() => ({ q: debounced }), [debounced])
  const { data: hits, isFetching: searching } = useGlobalEntrySearch(searchQuery)
  const searchingNow = debounced.trim().length > 0

  const [openEntry, setOpenEntry] = useState<string | null>(null)

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '' || value === 'all') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    }
    setParams(next, { replace: true })
  }

  return (
    <Stack spacing={2.5}>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}
      >
        <Typography component="h1" variant="h5">
          Collections
        </Typography>
        <Button
          component={RouterLink}
          to="/collections/new"
          variant="contained"
          startIcon={<AddRounded />}
        >
          New collection
        </Button>
      </Stack>

      <TextField
        label="Search everything"
        value={q}
        onChange={(event) => update({ q: event.target.value })}
        helperText="Looks inside every collection you can see - names, notes and tags."
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

      {searchingNow ? (
        <Stack spacing={1.5}>
          <Divider />
          {searching && !hits && <Typography role="status">Searching…</Typography>}
          {hits && hits.length === 0 && (
            <Typography role="status" color="text.secondary">
              Nothing matches “{debounced}”.
            </Typography>
          )}
          {hits?.map((hit) => (
            <EntryRow
              key={hit.id}
              entry={hit}
              showCollection
              expanded={openEntry === hit.id}
              onToggle={() => setOpenEntry(openEntry === hit.id ? null : hit.id)}
            />
          ))}
        </Stack>
      ) : (
        <>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={scope}
            onChange={(_event, next) => next && update({ scope: next })}
            aria-label="Whose collections"
          >
            <ToggleButton value="all">All</ToggleButton>
            <ToggleButton value="owned">Mine</ToggleButton>
            <ToggleButton value="shared">Shared with me</ToggleButton>
          </ToggleButtonGroup>

          {isError && <Alert severity="error">Those could not be loaded.</Alert>}
          {isPending && <Typography role="status">Loading…</Typography>}
          {collections && collections.length === 0 && (
            <Alert severity="info">
              Nothing here yet. A collection is for what you look up again - recipes,
              restaurants, words.
            </Alert>
          )}
          <Box
            sx={{
              display: 'grid',
              gap: 2,
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(auto-fill, minmax(16rem, 1fr))' },
            }}
          >
            {collections?.map((collection) => (
              <CollectionCard key={collection.id} collection={collection} />
            ))}
          </Box>
        </>
      )}
    </Stack>
  )
}

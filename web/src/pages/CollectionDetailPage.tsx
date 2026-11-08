import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import SettingsRounded from '@mui/icons-material/SettingsRounded'
import { errorMessage } from '../api/http'
import type { EntryDetail } from '../api/dataEntries'
import { fetchEntry } from '../api/dataEntries'
import { EntryForm } from '../data/EntryForm'
import { EntryRow } from '../data/EntryRow'
import {
  useCollection,
  useDeleteEntry,
  useEntries,
  useTagSuggestions,
  useTouchCollection,
} from '../data/queries'

/**
 * One collection: its entries, searched and filtered (R53, R56, R57).
 *
 * The search field and the tag chips both live in the URL, so a filtered view
 * is linkable and the back button works. One expanded row at a time, owned
 * here, so expanding one collapses the other by construction.
 */
export function CollectionDetailPage() {
  const { listId } = useParams<{ listId: string }>()
  const [params, setParams] = useSearchParams()

  const q = params.get('q') ?? ''
  const tags = useMemo(() => params.getAll('tag'), [params])

  const [debounced, setDebounced] = useState(q)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(q), 250)
    return () => clearTimeout(timer)
  }, [q])

  const { data: collection, isError: collectionFailed } = useCollection(listId)
  const query = useMemo(() => ({ q: debounced, tags }), [debounced, tags])
  const { data: entries, isPending, isError } = useEntries(listId, query)
  const { data: suggestions } = useTagSuggestions(listId)
  const deleteEntry = useDeleteEntry()
  const touch = useTouchCollection()

  useEffect(() => {
    if (collection) {
      touch(collection.id)
    }
    // Once per collection, guarded inside the hook: refetch-on-focus must not
    // turn into a write every time the member glances at another tab. This is
    // the only thing that records a visit, and the sidebar's order is the only
    // thing that reads it (0012 R100).
  }, [collection, touch])

  const [openEntry, setOpenEntry] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<EntryDetail | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  function toggleTag(tag: string) {
    const next = new URLSearchParams(params)
    const current = next.getAll('tag')
    next.delete('tag')
    const wanted = current.includes(tag)
      ? current.filter((each) => each !== tag)
      : [...current, tag]
    for (const each of wanted) {
      next.append('tag', each)
    }
    setParams(next, { replace: true })
  }

  function setQuery(value: string) {
    const next = new URLSearchParams(params)
    if (value) {
      next.set('q', value)
    } else {
      next.delete('q')
    }
    setParams(next, { replace: true })
  }

  async function remove(id: string, version: number, name: string) {
    setProblem(null)
    try {
      await deleteEntry.mutateAsync({ id, version })
    } catch (error) {
      setProblem(errorMessage(error, `${name} could not be deleted.`))
    }
  }

  if (collectionFailed) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Typography component="h1" variant="h5">
          Not here
        </Typography>
        <Typography>That collection no longer exists.</Typography>
        <Button component={RouterLink} to="/collections">
          Back to collections
        </Button>
      </Stack>
    )
  }

  return (
    <Stack spacing={2.5}>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}
      >
        <Typography component="h1" variant="h5" sx={{ overflowWrap: 'anywhere' }}>
          {collection?.name ?? 'Collection'}
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button variant="contained" startIcon={<AddRounded />} onClick={() => setAdding(true)}>
            New entry
          </Button>
          {collection?.owned && (
            <IconButton
              component={RouterLink}
              to={`/collections/${listId}/settings`}
              aria-label="Collection settings"
            >
              <SettingsRounded />
            </IconButton>
          )}
        </Stack>
      </Stack>

      {collection?.description && (
        <Typography color="text.secondary">{collection.description}</Typography>
      )}

      <TextField
        label="Search this collection"
        value={q}
        onChange={(event) => setQuery(event.target.value)}
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

      {suggestions && suggestions.length > 0 && (
        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}
               aria-label="Filter by tag">
          {suggestions.map((suggestion) => (
            <Chip
              key={suggestion.tag}
              size="small"
              label={`${suggestion.tag} (${suggestion.count})`}
              // Several tags mean all of them (R56): a filter should give a
              // member less to read, not more.
              color={tags.includes(suggestion.tag) ? 'primary' : 'default'}
              variant={tags.includes(suggestion.tag) ? 'filled' : 'outlined'}
              onClick={() => toggleTag(suggestion.tag)}
            />
          ))}
        </Stack>
      )}

      {problem && <Alert severity="error">{problem}</Alert>}
      {isError && <Alert severity="error">Those could not be loaded.</Alert>}
      {isPending && <Typography role="status">Loading…</Typography>}
      {entries && entries.length === 0 && (
        <Typography role="status" color="text.secondary">
          {q || tags.length > 0 ? 'Nothing matches that.' : 'Nothing in here yet.'}
        </Typography>
      )}

      <Stack spacing={1}>
        {entries?.map((entry) => (
          <EntryRow
            key={entry.id}
            entry={entry}
            expanded={openEntry === entry.id}
            onToggle={() => setOpenEntry(openEntry === entry.id ? null : entry.id)}
            onEdit={() => void fetchEntry(entry.id).then(setEditing)}
            onDelete={() => void remove(entry.id, entry.version, entry.name)}
          />
        ))}
      </Stack>

      {listId && adding && (
        <EntryForm
          listId={listId}
          suggestions={suggestions ?? []}
          open
          onClose={() => setAdding(false)}
        />
      )}
      {listId && editing && (
        <EntryForm
          listId={listId}
          entry={editing}
          suggestions={suggestions ?? []}
          open
          onClose={() => setEditing(null)}
        />
      )}
    </Stack>
  )
}

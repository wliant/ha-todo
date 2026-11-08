import { useState } from 'react'
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import List from '@mui/material/List'
import ListItemAvatar from '@mui/material/ListItemAvatar'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemText from '@mui/material/ListItemText'
import ListSubheader from '@mui/material/ListSubheader'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import type { ListSummary } from '../api/lists'
import { useCreateItem } from '../items/queries'
import { ListIcon } from '../lists/listIcons'
import { useSidebar } from '../lists/queries'
import { useCollections, useCreateEntry } from '../data/queries'
import { captureSharedLink, describeSharedLink, sharedLinkAsEntry } from '../share/sharedLink'

/**
 * Where Android's share sheet lands (R14, R31, R59). The manifest declares a
 * GET share target at this route, so the link arrives as ?title=&text=&url=.
 *
 * The member picks a destination, and since R59 that can be a **collection**
 * as well as a list - restaurants and recipes mostly arrive as links, which is
 * the case this screen was built for. Two groups, under two subheadings,
 * because 0006's first consequence is that the product now has to make the
 * difference legible without a sentence of explanation.
 *
 * Two queries merged here rather than one combined endpoint: two feature
 * modules, two endpoints, joined in the view model.
 *
 * The list the member touched most recently is still preselected - one tap in
 * the common case. Collections have no recency (adr/0029), so they are offered
 * by name and never preselected; that is a documented gap, not an oversight.
 * No "Inbox" is invented: a link that lands somewhere nobody looks is the
 * failure mode that would replace (adr/0020).
 */
export function SharePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  // Parsed once from the URL that opened this screen: parsing is pure, and the
  // parameters cannot change without a new navigation.
  const [captured] = useState(() => captureSharedLink(params))
  const { data: sidebar } = useSidebar()

  const lists: ListSummary[] = sidebar?.lists ?? []
  const { data: collections } = useCollections({ scope: 'all' })

  type Target = { kind: 'list' | 'collection'; id: string }
  const [chosen, setChosen] = useState<Target | null>(null)
  // The first list the sidebar would show - pinned, else the one they use most
  // (0012 R100, R101). Falls through to a collection only when the member has
  // no lists at all.
  const fallback: Target | null = lists[0]
    ? { kind: 'list', id: lists[0].id }
    : collections?.[0]
      ? { kind: 'collection', id: collections[0].id }
      : null
  const target = chosen ?? fallback
  const createItem = useCreateItem(target?.kind === 'list' ? target.id : '')
  const createEntry = useCreateEntry()
  const saving = createItem.isPending || createEntry.isPending
  const failed = createItem.isError || createEntry.isError
  const failure = createItem.error ?? createEntry.error

  function send() {
    if (!target) {
      return
    }
    if (target.kind === 'list') {
      createItem.mutate(
        { description: describeSharedLink(captured!), costValues: [] },
        { onSuccess: () => navigate(`/lists/${target.id}`) },
      )
      return
    }
    createEntry.mutate(
      { listId: target.id, ...sharedLinkAsEntry(captured!) },
      { onSuccess: () => navigate(`/collections/${target.id}`) },
    )
  }

  // The title or the text that came with the share, if either did. The URL is
  // deliberately not used: it is always rendered below as a link the member
  // can actually open, and repeating it as a heading says nothing twice.
  const heading = captured ? (captured.title ?? captured.note) : null

  if (!captured) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Typography component="h1" variant="h5">
          Shared to List
        </Typography>
        <Typography role="status">Nothing came through with that share.</Typography>
        <Link component={RouterLink} to="/lists">
          Back to lists
        </Link>
      </Stack>
    )
  }

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        Shared to List
      </Typography>

      <Paper
        component="article"
        elevation={0}
        sx={{ p: 2, border: 1, borderColor: 'divider' }}
      >
        {heading && (
          <Typography component="h2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
            {heading}
          </Typography>
        )}
        {captured.url && (
          <Link
            href={captured.url}
            target="_blank"
            rel="noreferrer"
            variant="body2"
            sx={{ overflowWrap: 'anywhere' }}
          >
            {captured.url}
          </Link>
        )}
        {captured.note && captured.note !== heading && (
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            {captured.note}
          </Typography>
        )}
        {!heading && !captured.url && (
          <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
            {captured.note}
          </Typography>
        )}
      </Paper>

      {lists.length === 0 && (collections?.length ?? 0) === 0 ? (
        <Stack spacing={1.5} sx={{ alignItems: 'flex-start' }}>
          <Alert severity="info">
            This needs somewhere to go, and you do not have a list or a collection yet.
          </Alert>
          <Button component={RouterLink} to="/lists/new" variant="contained">
            Make a list first
          </Button>
        </Stack>
      ) : (
        <Stack spacing={1.5}>
          <Typography component="h2" variant="h6">
            Put it on
          </Typography>
          <Paper variant="outlined">
            <List disablePadding aria-label="Put it on">
              {lists.length > 0 && (
                <ListSubheader disableSticky>Lists</ListSubheader>
              )}
              {lists.map((list) => (
                <ListItemButton
                  key={list.id}
                  selected={target?.kind === 'list' && target.id === list.id}
                  onClick={() => setChosen({ kind: 'list', id: list.id })}
                >
                  <ListItemAvatar aria-hidden>
                    <Avatar
                      variant="rounded"
                      sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
                    >
                      <ListIcon token={list.icon} />
                    </Avatar>
                  </ListItemAvatar>
                  {/* No secondary line: it would change the accessible name
                      of every list row, and the "Lists" subheading above
                      already says what these are. */}
                  <ListItemText primary={list.name} />
                </ListItemButton>
              ))}

              {(collections?.length ?? 0) > 0 && (
                <ListSubheader disableSticky>Collections</ListSubheader>
              )}
              {collections?.map((collection) => (
                <ListItemButton
                  key={collection.id}
                  selected={target?.kind === 'collection' && target.id === collection.id}
                  onClick={() => setChosen({ kind: 'collection', id: collection.id })}
                >
                  <ListItemAvatar aria-hidden>
                    <Avatar
                      variant="rounded"
                      sx={{ bgcolor: 'secondary.main', color: 'secondary.contrastText' }}
                    >
                      <ListIcon token={collection.icon} />
                    </Avatar>
                  </ListItemAvatar>
                  {/* The unfamiliar kind does get a line, because 0006's
                      first consequence is that the difference has to be
                      legible without a paragraph of explanation. */}
                  <ListItemText primary={collection.name} secondary="Something to keep" />
                </ListItemButton>
              ))}
            </List>
          </Paper>

          {failed && (
            <Alert severity="error">
              {errorMessage(failure, 'Could not add that.')}
            </Alert>
          )}

          <Button
            variant="contained"
            disabled={!target || saving}
            sx={{ alignSelf: 'flex-start' }}
            onClick={send}
          >
            {saving ? 'Adding…' : 'Add it'}
          </Button>
        </Stack>
      )}
    </Stack>
  )
}

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
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { errorMessage } from '../api/http'
import type { ListSummary } from '../api/lists'
import { useCreateItem } from '../items/queries'
import { ListIcon } from '../lists/listIcons'
import { useSidebar } from '../lists/queries'
import { captureSharedLink, describeSharedLink } from '../share/sharedLink'

/**
 * Where Android's share sheet lands (R14, R31). The manifest declares a GET
 * share target at this route, so the link arrives as ?title=&text=&url=.
 *
 * The member picks which list it goes on, with the one they touched most
 * recently already chosen - one tap in the common case. No "Inbox" list is
 * invented: a link that lands somewhere nobody looks is the failure mode that
 * would replace (adr/0020).
 */
export function SharePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  // Parsed once from the URL that opened this screen: parsing is pure, and the
  // parameters cannot change without a new navigation.
  const [captured] = useState(() => captureSharedLink(params))
  const { data: sidebar } = useSidebar()

  const choices: ListSummary[] = [...(sidebar?.recent ?? []), ...(sidebar?.pinned ?? [])]
  const [chosen, setChosen] = useState<string | null>(null)
  const target = chosen ?? choices[0]?.id ?? null
  const createItem = useCreateItem(target ?? '')

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

      {choices.length === 0 ? (
        <Stack spacing={1.5} sx={{ alignItems: 'flex-start' }}>
          <Alert severity="info">
            This needs a list to go on, and you do not have one yet.
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
              {choices.map((list) => (
                <ListItemButton
                  key={list.id}
                  selected={list.id === target}
                  onClick={() => setChosen(list.id)}
                >
                  <ListItemAvatar aria-hidden>
                    <Avatar
                      variant="rounded"
                      sx={{ bgcolor: 'primary.main', color: 'primary.contrastText' }}
                    >
                      <ListIcon token={list.icon} />
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText primary={list.name} />
                </ListItemButton>
              ))}
            </List>
          </Paper>

          {createItem.isError && (
            <Alert severity="error">
              {errorMessage(createItem.error, 'Could not add that to the list.')}
            </Alert>
          )}

          <Button
            variant="contained"
            disabled={!target || createItem.isPending}
            sx={{ alignSelf: 'flex-start' }}
            onClick={() =>
              createItem.mutate(
                {
                  description: describeSharedLink(captured),
                  costValues: [],
                },
                { onSuccess: () => navigate(`/lists/${target}`) },
              )
            }
          >
            {createItem.isPending ? 'Adding…' : 'Add it'}
          </Button>
        </Stack>
      )}
    </Stack>
  )
}

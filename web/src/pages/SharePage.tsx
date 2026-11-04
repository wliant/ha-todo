import { useEffect, useRef, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useCurrentUser } from '../auth/useCurrentUser'
import { captureSharedLink, park } from '../share/parkedLinks'

/**
 * Where Android's share sheet lands (R14). The manifest declares a GET share
 * target at this route, so the shared link arrives as ?title=&text=&url=.
 *
 * Lists and items do not exist yet, so the link is parked on the device and
 * said to be waiting, rather than a created item being implied. When items
 * land, the park() call below becomes the create-item mutation and the member
 * picks a destination - that is the only change this screen needs
 * (adr/0008, adr/0020).
 */
export function SharePage() {
  const [params] = useSearchParams()
  const { data: user } = useCurrentUser()
  // Derived once from the URL that opened this screen: parsing is pure, and
  // the parameters cannot change without a new navigation.
  const [captured] = useState(() => captureSharedLink(params))
  const parked = useRef(false)

  useEffect(() => {
    // Guarded: StrictMode runs effects twice in development, and parking the
    // same share twice would show the member a duplicate.
    if (parked.current || !captured || !user) return
    parked.current = true
    park(user.id, captured)
  }, [captured, user])

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        Shared to List
      </Typography>

      {!captured && (
        <>
          <Typography role="status">Nothing came through with that share.</Typography>
          <Link component={RouterLink} to="/lists">
            Back to lists
          </Link>
        </>
      )}

      {/* Gated on the member, because that is what parking is keyed by: the
          screen must not say "saved" before it has been. */}
      {captured && user && (
        <>
          <Typography role="status">Saved. It is waiting on your lists.</Typography>

          <Paper
            component="article"
            elevation={0}
            sx={{ p: 2, border: 1, borderColor: 'divider' }}
          >
            <Typography component="h2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
              {captured.title ?? captured.url ?? captured.note}
            </Typography>
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
            {captured.note && (
              <Typography color="text.secondary" sx={{ mt: 1 }}>
                {captured.note}
              </Typography>
            )}
          </Paper>

          <Typography color="text.secondary">
            Lists themselves are still being built, so this link is held here until there
            is a list to put it on.
          </Typography>

          <Button component={RouterLink} to="/lists" variant="contained" sx={{ alignSelf: 'flex-start' }}>
            Go to lists
          </Button>
        </>
      )}
    </Stack>
  )
}

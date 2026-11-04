import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import CloseRounded from '@mui/icons-material/CloseRounded'
import { useCurrentUser } from '../auth/useCurrentUser'
import { discard, parkedLinks, type ParkedLink } from '../share/parkedLinks'

/**
 * Placeholder. The list / item domain lands in the next phase; what is real
 * here is the shared links waiting for it (adr/0008).
 */
export function ListsPage() {
  const { data: user } = useCurrentUser()
  const [waiting, setWaiting] = useState<ParkedLink[]>(() =>
    user ? parkedLinks(user.id) : [],
  )

  return (
    <Stack spacing={2.5}>
      <Typography component="h1" variant="h5">
        Lists
      </Typography>

      <Typography role="status" color="text.secondary">
        No lists yet.
      </Typography>

      {waiting.length > 0 && (
        <Stack spacing={1.5} component="section">
          <Typography component="h2" variant="h6">
            Shared links waiting
          </Typography>
          <Alert severity="info">
            Shared into List before there was a list to put them on. They will become
            items once lists exist.
          </Alert>

          <Stack spacing={1}>
            {waiting.map((link) => (
              <Paper
                key={link.id}
                elevation={0}
                sx={{
                  p: 1.5,
                  border: 1,
                  borderColor: 'divider',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 1,
                }}
              >
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                    {link.title ?? link.url ?? link.note}
                  </Typography>
                  {link.url && (
                    <Link
                      href={link.url}
                      target="_blank"
                      rel="noreferrer"
                      variant="body2"
                      sx={{ overflowWrap: 'anywhere' }}
                    >
                      {link.url}
                    </Link>
                  )}
                </Box>
                {/* Labelled rather than captioned: on a phone the link needs
                    the width more than the control does. */}
                <IconButton
                  aria-label={`Discard ${link.title ?? link.url ?? 'link'}`}
                  onClick={() => setWaiting(discard(user!.id, link.id))}
                >
                  <CloseRounded fontSize="small" />
                </IconButton>
              </Paper>
            ))}
          </Stack>
        </Stack>
      )}
    </Stack>
  )
}

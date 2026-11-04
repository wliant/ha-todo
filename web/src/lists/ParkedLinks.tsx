import { useReducer } from 'react'
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
 * Links shared into the app before there was anywhere to put them (adr/0008).
 *
 * Still parked, because items do not exist yet. When they do, this component
 * and src/share/parkedLinks.ts are both deleted and the share screen creates a
 * real item on a chosen list instead (adr/0020).
 */
export function ParkedLinks() {
  const { data: user } = useCurrentUser()
  // Derived during render rather than seeded into state once: the signed-in
  // member is resolved asynchronously, so a lazy initialiser would capture
  // "nobody" on the first render and never look again. The counter is only
  // there to re-read after a discard.
  const [, reread] = useReducer((count: number) => count + 1, 0)
  const waiting: ParkedLink[] = user ? parkedLinks(user.id) : []

  if (!user || waiting.length === 0) {
    return null
  }

  return (
    <Stack spacing={1.5} component="section">
      <Typography component="h2" variant="h6">
        Shared links waiting
      </Typography>
      <Alert severity="info">
        Shared into List before there was a list to put them on. They will become items
        once items exist.
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
            {/* Labelled rather than captioned: on a phone the link needs the
                width more than the control does. */}
            <IconButton
              aria-label={`Discard ${link.title ?? link.url ?? 'link'}`}
              onClick={() => {
                discard(user.id, link.id)
                reread()
              }}
            >
              <CloseRounded fontSize="small" />
            </IconButton>
          </Paper>
        ))}
      </Stack>
    </Stack>
  )
}

import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Collapse from '@mui/material/Collapse'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import CircularProgress from '@mui/material/CircularProgress'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import { Link as RouterLink } from 'react-router-dom'
import type { EntryHit } from '../api/dataEntries'
import { useEntry } from './queries'

/**
 * One entry, collapsed or expanded (R57).
 *
 * Collapsed shows the name, the description and the tags; expanded adds the
 * detail. Which row is open is owned by the parent, so expanding one collapses
 * the other by construction rather than by an effect that has to keep up.
 *
 * The detail is fetched only when the row opens. A collection of recipes would
 * otherwise send every picture in it to draw a list of names.
 */
export function EntryRow({
  entry,
  expanded,
  onToggle,
  onEdit,
  onDelete,
  showCollection = false,
}: {
  entry: EntryHit
  expanded: boolean
  onToggle: () => void
  onEdit?: () => void
  onDelete?: () => void
  showCollection?: boolean
}) {
  const { data: detail, isPending } = useEntry(expanded ? entry.id : undefined)

  return (
    <Paper component="article" variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{entry.name}</Typography>
          {entry.description && (
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {entry.description}
            </Typography>
          )}
          <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap', rowGap: 0.5 }}>
            {showCollection && (
              /* R54: a result from across every collection has to say which
                 one it came from, or the answer is incomplete. */
              <Chip
                size="small"
                component={RouterLink}
                to={`/collections/${entry.dataListId}`}
                clickable
                label={entry.listName}
              />
            )}
            {entry.tags.map((tag) => (
              <Chip key={tag} size="small" variant="outlined" label={tag} />
            ))}
          </Stack>
        </Box>

        {onEdit && (
          <IconButton aria-label={`Edit ${entry.name}`} size="small" onClick={onEdit}>
            <EditRounded fontSize="small" />
          </IconButton>
        )}
        {onDelete && (
          <IconButton aria-label={`Delete ${entry.name}`} size="small" onClick={onDelete}>
            <DeleteOutlineRounded fontSize="small" />
          </IconButton>
        )}
        <IconButton
          aria-label={expanded ? `Hide ${entry.name}` : `Open ${entry.name}`}
          aria-expanded={expanded}
          size="small"
          onClick={onToggle}
          sx={{
            transform: expanded ? 'rotate(180deg)' : 'none',
            transition: 'transform 150ms',
          }}
        >
          <ExpandMoreRounded fontSize="small" />
        </IconButton>
      </Stack>

      <Collapse in={expanded} unmountOnExit>
        <Box sx={{ pt: 1.5 }}>
          {isPending && <CircularProgress size={18} aria-label="Loading" />}
          {detail?.detailHtml ? (
            /* The stored HTML, rendered as-is. It was sanitized on write and
               this deliberately sanitizes nothing: a second implementation of
               that rule drifts from the first (adr/0018). */
            <Box
              sx={{
                '& img': { maxWidth: '100%', height: 'auto', borderRadius: 1 },
                '& ul, & ol': { pl: 3 },
                overflowWrap: 'anywhere',
              }}
              dangerouslySetInnerHTML={{ __html: detail.detailHtml }}
            />
          ) : (
            !isPending && (
              <Typography variant="body2" color="text.secondary">
                Nothing written here yet.
              </Typography>
            )
          )}
        </Box>
      </Collapse>
    </Paper>
  )
}

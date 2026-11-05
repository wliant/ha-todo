import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import type { DashboardItem } from '../api/dashboard'
import { MemberAvatar } from '../app/MemberAvatar'
import { ListIcon } from '../lists/listIcons'

/**
 * An item opened from a grid cell.
 *
 * <p>Driven by `?item=<id>` rather than state, so it is deep-linkable and the
 * back button closes it. Read-only on purpose: the dashboard is for seeing what
 * is coming, and anything a member wants to *do* belongs on the list itself -
 * which is one tap from here.
 */
export function DashboardItemDialog({
  item,
  onClose,
}: {
  item: DashboardItem
  onClose: () => void
}) {
  return (
    <Dialog open fullWidth maxWidth="sm" onClose={onClose}>
      <DialogTitle sx={{ pb: 1 }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Avatar
            aria-hidden
            variant="rounded"
            sx={{
              bgcolor: 'primary.main',
              color: 'primary.contrastText',
              width: 32,
              height: 32,
            }}
          >
            <ListIcon token={item.listIcon} />
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body1" sx={{ overflowWrap: 'anywhere' }}>
              {item.description}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {item.listName}
            </Typography>
          </Box>
        </Stack>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={1.5}>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
            {item.dueAt && (
              <Chip size="small" variant="outlined" label={`Due ${formatDue(item.dueAt)}`} />
            )}
            {item.assignee && (
              <Chip
                size="small"
                avatar={
                  <MemberAvatar
                    displayName={item.assignee.displayName}
                    avatarImageId={item.assignee.avatarImageId}
                    size={24}
                  />
                }
                label={
                  item.selfAssigned
                    ? `${item.assignee.displayName} claimed this`
                    : item.assignee.displayName
                }
                sx={
                  item.selfAssigned
                    ? { bgcolor: 'selfClaimed.main', color: 'selfClaimed.contrastText' }
                    : undefined
                }
              />
            )}
          </Stack>

          {item.detailHtml && (
            <Box
              // The server sanitized this on write and stores only what
              // survived; the client filters nothing of its own (adr/0018).
              dangerouslySetInnerHTML={{ __html: item.detailHtml }}
              sx={{
                color: 'text.secondary',
                fontSize: '0.875rem',
                overflowWrap: 'anywhere',
                '& p': { m: 0, mb: 0.5 },
                '& ul, & ol': { pl: 3, m: 0, mb: 0.5 },
                '& img': { maxWidth: '100%', borderRadius: 1, display: 'block', my: 1 },
              }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button component={RouterLink} to={`/lists/${item.listId}`} variant="contained">
          Open {item.listName}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function formatDue(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

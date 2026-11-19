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
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import EventBusyRounded from '@mui/icons-material/EventBusyRounded'
import { CompleteControl } from '../items/CompleteControl'
import { ListIcon } from '../lists/listIcons'

/**
 * An item opened from a grid cell.
 *
 * <p>Driven by `?item=<id>` rather than state, so it is deep-linkable and the
 * back button closes it.
 *
 * <p>It also adds another one like it (0018 R156) - the one create the agenda
 * can offer, because the list is the one the chore came from and nobody has to
 * be asked which - and changes the chore itself (0020 R173), which is the
 * other half of the same reasoning: the list is already decided, so the
 * list's form is the one to open.
 *
 * <p>It completes the chore (0010 R85), which is what makes ticking something
 * off reachable from the week view, the month view and the undated panel at
 * once rather than three times. Everything *else* a member might want to do to
 * an item still belongs on the list, which knows its own fields and members and
 * is one tap from here.
 */
export function DashboardItemDialog({
  item,
  onClose,
  onComplete,
  onWriteNote,
  onLapse,
  onEdit,
  onDuplicate,
}: {
  item: DashboardItem
  onClose: () => void
  onComplete: () => void
  onWriteNote: () => void
  onLapse: () => void
  onEdit: () => void
  onDuplicate: () => void
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
      <DialogActions sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Button onClick={onClose}>Close</Button>
        <Button component={RouterLink} to={`/lists/${item.listId}`}>
          Open {item.listName}
        </Button>
        {/* The list's own control, imported rather than reimplemented: the
            hold-for-a-note gesture and its tabbable alternative are one
            implementation of adr/0027 or they are two that drift. */}
        <CompleteControl
          description={item.description}
          onComplete={onComplete}
          onWriteNote={onWriteNote}
        />
        {/* Spelled out rather than an icon, because a dialog has the room and
            this is the one surface where the member opened the chore to think
            about it (0015 R138). */}
        <Button
          startIcon={<EventBusyRounded fontSize="small" />}
          onClick={onLapse}
          color="inherit"
        >
          Lapse
        </Button>
        {/* The two form-openers last, after everything that writes: a
            mis-tap on either costs a Cancel rather than a chore (0020 R175). */}
        <Button startIcon={<EditRounded fontSize="small" />} onClick={onEdit} color="inherit">
          Edit
        </Button>
        <Button
          startIcon={<ContentCopyRounded fontSize="small" />}
          onClick={onDuplicate}
          color="inherit"
        >
          Duplicate
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

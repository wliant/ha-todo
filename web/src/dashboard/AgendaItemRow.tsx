import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import type { DashboardItem } from '../api/dashboard'
import { MemberAvatar } from '../app/MemberAvatar'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import EventBusyRounded from '@mui/icons-material/EventBusyRounded'
import { CompleteControl } from '../items/CompleteControl'
import { ListIcon } from '../lists/listIcons'

/**
 * A chore's full row on the agenda: what it is, and the control that ticks it
 * off (0010 R83).
 *
 * <p>Shared by the Day view and the overdue strip (0011 R90), which differ only
 * in their caption - the Day view says what time it is due, the strip says how
 * late it is. One component, so the shape below exists once.
 *
 * <p>The disclosure wraps the description only, with the completion control as
 * its **sibling**: a row-wide button containing a button is invalid nesting and
 * a screen reader reads it as neither. `items/ItemRow` has the same shape for
 * the same reason, and a second copy of this is where that grows back.
 */
export function AgendaItemRow({
  item,
  caption,
  onOpen,
  onComplete,
  onWriteNote,
  onLapse,
}: {
  item: DashboardItem
  /** The second line, under the description. Already includes the list's name. */
  caption: string
  onOpen: (id: string) => void
  onComplete: () => void
  onWriteNote: () => void
  onLapse: () => void
}) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      {/* Wraps on a narrow phone: the control is wide enough that it and a
          long description cannot share one line on every device. */}
      <Stack
        direction="row"
        sx={{ alignItems: 'center', width: '100%', minWidth: 0, flexWrap: 'wrap', gap: 1.5 }}
      >
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
        <ButtonBase
          onClick={() => onOpen(item.id)}
          aria-label={`${item.description}, on ${item.listName}`}
          sx={{
            flex: '1 1 10rem',
            minWidth: 0,
            justifyContent: 'flex-start',
            textAlign: 'left',
            borderRadius: 1,
            px: 0.5,
            mx: -0.5,
          }}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ overflowWrap: 'anywhere' }}>{item.description}</Typography>
            <Typography variant="caption" color="text.secondary">
              {caption}
            </Typography>
          </Box>
        </ButtonBase>
        <Stack direction="row" sx={{ alignItems: 'center', gap: 1, ml: 'auto' }}>
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
              label={item.assignee.displayName}
              sx={
                item.selfAssigned
                  ? { bgcolor: 'selfClaimed.main', color: 'selfClaimed.contrastText' }
                  : undefined
              }
            />
          )}
          <CompleteControl
            description={item.description}
            onComplete={onComplete}
            onWriteNote={onWriteNote}
          />
          {/* A sibling of the complete group rather than a third button in it,
              as on a list: the group is one gesture with one accelerator, and
              a give-up verb inside it is a mis-tap away from the primary one. */}
          <Tooltip title="We did not get to this">
            <IconButton
              size="small"
              aria-label={`Lapse ${item.description}`}
              onClick={onLapse}
            >
              <EventBusyRounded fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      </Stack>
    </Paper>
  )
}

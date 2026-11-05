import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import Collapse from '@mui/material/Collapse'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import EditRounded from '@mui/icons-material/EditRounded'
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import HowToRegRounded from '@mui/icons-material/HowToRegRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import type { Item } from '../api/items'
import type { ListField } from '../api/lists'
import { MemberAvatar } from '../app/MemberAvatar'
import { CompleteControl } from './CompleteControl'

/**
 * One item, collapsed or expanded (R38).
 *
 * <p>Collapsed shows the description and the item's values; expanded adds the
 * detail and the completion note, so a long list on a phone never becomes a
 * wall of pictures. Which row is open is held by the section, not here - one
 * id there makes "at most one open" true by construction rather than by rows
 * coordinating with each other.
 *
 * <p>The checkbox selects (R46); completing is its own control (R48).
 */
export function ItemRow({
  item,
  fields,
  expanded,
  selected,
  onToggleExpand,
  onSelect,
  onComplete,
  onWriteNote,
  onReopen,
  onEdit,
}: {
  item: Item
  fields: ListField[]
  expanded: boolean
  selected: boolean
  onToggleExpand: () => void
  onSelect: (selected: boolean) => void
  onComplete: () => void
  onWriteNote: () => void
  onReopen: () => void
  onEdit: () => void
}) {
  const bodyId = `item-${item.id}-detail`
  const hasBody = Boolean(item.detailHtml) || Boolean(item.completionNoteHtml)

  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Checkbox
          checked={selected}
          onChange={(event) => onSelect(event.target.checked)}
          // "Select", never anything containing "Complete": this used to be the
          // completion control, and a name that still matched would let every
          // old test pass while asserting the wrong thing (adr/0027).
          slotProps={{ input: { 'aria-label': `Select ${item.description}` } }}
          sx={{ mt: -0.5 }}
        />

        <Box sx={{ flex: 1, minWidth: 0 }}>
          {/* The disclosure. The checkbox and the buttons are siblings outside
              it: a row-wide button containing a checkbox is invalid nesting,
              and a screen reader reads it as neither. */}
          <ButtonBase
            onClick={onToggleExpand}
            aria-expanded={expanded}
            aria-controls={bodyId}
            sx={{
              width: '100%',
              justifyContent: 'flex-start',
              textAlign: 'left',
              borderRadius: 1,
              px: 0.5,
              mx: -0.5,
            }}
          >
            <Typography
              sx={{
                flex: 1,
                overflowWrap: 'anywhere',
                textDecoration: item.completed ? 'line-through' : 'none',
                color: item.completed ? 'text.secondary' : 'text.primary',
              }}
            >
              {item.description}
            </Typography>
            {hasBody &&
              (expanded ? (
                <ExpandLessRounded fontSize="small" color="action" />
              ) : (
                <ExpandMoreRounded fontSize="small" color="action" />
              ))}
          </ButtonBase>

          <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
            {item.assignee && (
              <Tooltip
                title={
                  item.selfAssigned
                    ? `${item.assignee.displayName} claimed this`
                    : `Assigned to ${item.assignee.displayName}`
                }
              >
                <Chip
                  size="small"
                  // An icon and the label carry the same fact as the colour, so
                  // a colour-blind member and a test both read it (R47).
                  icon={item.selfAssigned ? <HowToRegRounded fontSize="small" /> : undefined}
                  aria-label={
                    item.selfAssigned
                      ? `${item.assignee.displayName} claimed this`
                      : `Assigned to ${item.assignee.displayName}`
                  }
                  avatar={
                    item.selfAssigned ? undefined : (
                      <MemberAvatar
                        displayName={item.assignee.displayName}
                        avatarImageId={item.assignee.avatarImageId}
                        size={24}
                      />
                    )
                  }
                  label={item.assignee.displayName}
                  sx={
                    item.selfAssigned
                      ? { bgcolor: 'selfClaimed.main', color: 'selfClaimed.contrastText' }
                      : undefined
                  }
                />
              </Tooltip>
            )}
            {item.dueAt && (
              <Chip size="small" variant="outlined" label={`Due ${formatDue(item.dueAt)}`} />
            )}
            {item.costValues.map((value) => {
              const label = describeCost(value.fieldId, value.choiceId, value.amount, fields)
              return label ? (
                <Chip key={value.fieldId} size="small" variant="outlined" label={label} />
              ) : null
            })}
            {item.completed && item.completedBy && (
              <Chip
                size="small"
                color="primary"
                label={
                  item.pointsEarned
                    ? `${item.completedBy.displayName} — ${item.pointsEarned} points`
                    : `Done by ${item.completedBy.displayName}`
                }
              />
            )}
          </Stack>

          <Collapse id={bodyId} in={expanded} unmountOnExit>
            <Box sx={{ mt: 1 }}>
              {item.detailHtml && <RichText html={item.detailHtml} />}
              {item.completed && item.completedAt && (
                <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>
                  {/* R36: the product stored both of these all along and showed
                      neither. */}
                  Finished {formatWhen(item.completedAt)}
                  {item.completedBy ? ` by ${item.completedBy.displayName}` : ''}
                </Typography>
              )}
              {item.completionNoteHtml && (
                <Box sx={{ mt: 0.5 }}>
                  <Typography variant="caption" color="text.secondary" component="div">
                    How it went
                  </Typography>
                  <RichText html={item.completionNoteHtml} />
                </Box>
              )}
            </Box>
          </Collapse>
        </Box>

        <Stack spacing={0.5} sx={{ alignItems: 'flex-end' }}>
          {item.completed ? (
            <Tooltip title="Reopen this, and take its points back">
              <IconButton
                size="small"
                aria-label={`Reopen ${item.description}`}
                onClick={onReopen}
              >
                <UndoRounded fontSize="small" />
              </IconButton>
            </Tooltip>
          ) : (
            <CompleteControl
              description={item.description}
              onComplete={onComplete}
              onWriteNote={onWriteNote}
            />
          )}
          <IconButton
            size="small"
            aria-label={`Edit ${item.description}`}
            onClick={onEdit}
          >
            <EditRounded fontSize="small" />
          </IconButton>
        </Stack>
      </Stack>
    </Paper>
  )
}

/**
 * Stored HTML, rendered as it is.
 *
 * <p>Safe because the server sanitized it on write and stores only what
 * survived; the client deliberately filters nothing, because a second
 * implementation of that rule drifts from the first (adr/0018).
 */
function RichText({ html }: { html: string }) {
  return (
    <Box
      dangerouslySetInnerHTML={{ __html: html }}
      sx={{
        color: 'text.secondary',
        fontSize: '0.875rem',
        overflowWrap: 'anywhere',
        '& p': { m: 0, mb: 0.5 },
        '& ul, & ol': { pl: 3, m: 0, mb: 0.5 },
        '& img': { maxWidth: '100%', borderRadius: 1, display: 'block', my: 1 },
      }}
    />
  )
}

function describeCost(
  fieldId: string,
  choiceId: string | null,
  amount: number | null,
  fields: ListField[],
): string | null {
  const field = fields.find((candidate) => candidate.id === fieldId)
  if (!field) {
    return null
  }
  if (choiceId) {
    const choice = field.choices.find((candidate) => candidate.id === choiceId)
    return choice ? `${field.label}: ${choice.label}` : null
  }
  return amount === null ? null : `${field.label}: ${amount} ${field.costUnitLabel ?? ''}`.trim()
}

function formatDue(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

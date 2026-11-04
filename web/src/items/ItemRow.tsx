import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import EditRounded from '@mui/icons-material/EditRounded'
import type { Item } from '../api/items'
import type { ListField } from '../api/lists'
import { MemberAvatar } from '../app/MemberAvatar'

/** One item, with whatever its list asked for shown beside it. */
export function ItemRow({
  item,
  fields,
  onToggle,
  onEdit,
}: {
  item: Item
  fields: ListField[]
  onToggle: (completed: boolean) => void
  onEdit: () => void
}) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Checkbox
          checked={item.completed}
          onChange={(event) => onToggle(event.target.checked)}
          slotProps={{ input: { 'aria-label': `Complete ${item.description}` } }}
          sx={{ mt: -0.5 }}
        />

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            sx={{
              overflowWrap: 'anywhere',
              textDecoration: item.completed ? 'line-through' : 'none',
              color: item.completed ? 'text.secondary' : 'text.primary',
            }}
          >
            {item.description}
          </Typography>

          {item.detailHtml && (
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {item.detailHtml}
            </Typography>
          )}

          <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
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
              />
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
        </Box>

        <IconButton aria-label={`Edit ${item.description}`} onClick={onEdit}>
          <EditRounded fontSize="small" />
        </IconButton>
      </Stack>
    </Paper>
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

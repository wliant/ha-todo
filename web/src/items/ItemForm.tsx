import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import type { ListField, ListMember } from '../api/lists'
import { Suspense, lazy } from 'react'
import Skeleton from '@mui/material/Skeleton'
import type { ItemCostValue, SaveItem } from '../api/items'

/**
 * Loaded only when a list actually asks for a detail field.
 *
 * The editor drags ProseMirror behind it - well over a hundred kilobytes
 * gzipped, on a product whose users are mostly holding a phone. Most lists
 * never declare the field, and nobody should download an editor for a list
 * that has no use for one.
 */
const DetailEditor = lazy(() =>
  import('./DetailEditor').then((module) => ({ default: module.DetailEditor })),
)

/**
 * The inputs one list asks for, and no others.
 *
 * Built from the list's configuration rather than from a fixed set, so a field
 * the list never declared is not merely hidden - it is never rendered, and the
 * server drops it if sent anyway.
 *
 * <p>Collapsed, it shows only the fields the list marks required (R39): most
 * items on most lists are a line of text, and a form asking six questions for
 * "milk" is why somebody reaches for paper instead. Keyed off `required`
 * rather than a fixed list of kinds, because that is what stops the form
 * becoming unsubmittable with a hidden required field left empty.
 */
export function ItemForm({
  fields,
  members,
  value,
  onChange,
  expanded = true,
  onExpand,
}: {
  fields: ListField[]
  members: ListMember[]
  value: SaveItem
  onChange: (value: SaveItem) => void
  expanded?: boolean
  onExpand?: () => void
}) {
  const detail = fields.find((field) => field.kind === 'DETAIL')
  const assignee = fields.find((field) => field.kind === 'ASSIGNEE')
  const dueAt = fields.find((field) => field.kind === 'DUE_AT')
  const costs = fields.filter((field) => field.kind === 'COST')

  /** Shown while collapsed only if the list insists on an answer. */
  const asked = (field: ListField | undefined) =>
    Boolean(field) && (expanded || field!.required)
  const hidden = fields.filter(
    (field) => field.kind !== 'DESCRIPTION' && !field.required,
  ).length

  function setCost(fieldId: string, next: Partial<ItemCostValue>) {
    const others = value.costValues.filter((cost) => cost.fieldId !== fieldId)
    const current = value.costValues.find((cost) => cost.fieldId === fieldId)
    onChange({
      ...value,
      costValues: [
        ...others,
        { fieldId, choiceId: null, amount: null, ...current, ...next },
      ],
    })
  }

  function costFor(fieldId: string): ItemCostValue | undefined {
    return value.costValues.find((cost) => cost.fieldId === fieldId)
  }

  return (
    <Stack spacing={2}>
      <TextField
        label="Description"
        required
        autoFocus
        value={value.description}
        onChange={(event) => onChange({ ...value, description: event.target.value })}
        slotProps={{ htmlInput: { maxLength: 300 } }}
      />

      {asked(detail) && detail && (
        <Suspense fallback={<Skeleton variant="rounded" height={180} />}>
          <DetailEditor
            value={value.detailHtml ?? ''}
            required={detail.required}
            onChange={(html) => onChange({ ...value, detailHtml: html })}
          />
        </Suspense>
      )}

      {asked(assignee) && assignee && (
        <TextField
          select
          label="Assignee"
          required={assignee.required}
          value={value.assigneeId ?? ''}
          onChange={(event) => onChange({ ...value, assigneeId: event.target.value || null })}
        >
          {/* Only the people who can see this list (R24). The server checks
              it too - this is the convenience, not the control. */}
          {!assignee.required && <MenuItem value="">Nobody</MenuItem>}
          {members.map((member) => (
            <MenuItem key={member.id} value={member.id}>
              {member.displayName}
            </MenuItem>
          ))}
        </TextField>
      )}

      {asked(dueAt) && dueAt && (
        <TextField
          label="Due"
          type="datetime-local"
          required={dueAt.required}
          value={toLocalInput(value.dueAt)}
          onChange={(event) =>
            onChange({ ...value, dueAt: fromLocalInput(event.target.value) })
          }
          slotProps={{ inputLabel: { shrink: true } }}
        />
      )}

      {costs
        .filter((cost) => expanded || cost.required)
        .map((cost) =>
        cost.costMode === 'FIXED' ? (
          <TextField
            key={cost.id}
            select
            label={cost.label ?? 'Cost'}
            required={cost.required}
            value={costFor(cost.id!)?.choiceId ?? ''}
            onChange={(event) =>
              setCost(cost.id!, { choiceId: event.target.value || null, amount: null })
            }
          >
            {!cost.required && <MenuItem value="">Not set</MenuItem>}
            {cost.choices.map((choice) => (
              <MenuItem key={choice.id} value={choice.id!}>
                {choice.points > 0
                  ? `${choice.label} — ${choice.points} points`
                  : choice.label}
              </MenuItem>
            ))}
          </TextField>
        ) : (
          <TextField
            key={cost.id}
            type="number"
            label={`${cost.label} (${cost.costUnitLabel})`}
            required={cost.required}
            value={costFor(cost.id!)?.amount ?? ''}
            onChange={(event) =>
              setCost(cost.id!, {
                amount: event.target.value === '' ? null : Number(event.target.value),
                choiceId: null,
              })
            }
            slotProps={{ htmlInput: { min: 0, step: 'any' } }}
            helperText={
              cost.pointsPerUnit
                ? `${cost.pointsPerUnit} points per ${cost.costUnitLabel}`
                : undefined
            }
          />
        ),
      )}

      {!expanded && hidden > 0 && (
        <Button
          size="small"
          sx={{ alignSelf: 'flex-start' }}
          aria-expanded={false}
          endIcon={<ExpandMoreRounded />}
          onClick={onExpand}
        >
          More fields
        </Button>
      )}

      {expanded && fields.length === 1 && (
        <Alert severity="info">
          This list only asks for a description. Add fields in its settings.
        </Alert>
      )}
    </Stack>
  )
}

/** datetime-local speaks local time with no zone; the API speaks instants. */
function toLocalInput(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function fromLocalInput(local: string): string | null {
  return local ? new Date(local).toISOString() : null
}

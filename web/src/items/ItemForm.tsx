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
import { AssigneeField } from './AssigneeField'
import { DueAtField } from './DueAtField'

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
 * <p>Collapsed, it shows what the list marks required (R39) **plus the
 * assignee and the due date whenever the list declares them** (0017 R148).
 * R39's reasoning stands - a form asking six questions for "milk" is why
 * somebody reaches for paper - but `required` was a proxy for the wrong
 * property. What is worth hiding is a field expensive to answer: a detail
 * editor, a cost to think about. These two cost a tap, and every planning
 * surface in the product reads them, so an item added without them is
 * invisible to the agenda (adr/0050).
 *
 * <p>Still keyed off `required` for everything else, because that is what
 * stops the form becoming unsubmittable with a hidden required field left
 * empty.
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

  /**
   * Whether the disclosure has anything left to reveal (R151).
   *
   * <p>This used to be "declared and not required", which happened to be the
   * same question. It is not any more, and the failure is silent in both
   * directions: the assignee is shown collapsed, so counting it offers a
   * toggle that reveals nothing, while the due date is shown as *chips* only,
   * so its picker, time box and steppers are behind the toggle even when the
   * list insists on a date.
   */
  const hidden =
    fields.filter(
      (field) =>
        field.kind !== 'DESCRIPTION' && field.kind !== 'ASSIGNEE' &&
        field.kind !== 'DUE_AT' && !field.required,
    ).length + (dueAt ? 1 : 0)

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

      {/* Declared is enough, required or not (R148): both of these cost one
          tap, and both are what the agenda, the overdue strip and the Mine
          toggle read. */}
      {assignee && (
        <AssigneeField
          members={members}
          required={assignee.required}
          value={value.assigneeId}
          onChange={(assigneeId) => onChange({ ...value, assigneeId })}
        />
      )}

      {dueAt && (
        <DueAtField
          value={value.dueAt}
          required={dueAt.required}
          onChange={(dueAt) => onChange({ ...value, dueAt })}
          compact={!expanded}
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

import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import ListItemText from '@mui/material/ListItemText'
import MenuItem from '@mui/material/MenuItem'
import Radio from '@mui/material/Radio'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import ArrowDownwardRounded from '@mui/icons-material/ArrowDownwardRounded'
import ArrowUpwardRounded from '@mui/icons-material/ArrowUpwardRounded'
import { MenuChip } from '../app/MenuChip'
import type { ListField, ListMember } from '../api/lists'
import {
  DEFAULT_FILTER,
  STATUS_ORDER,
  UNASSIGNED,
  countActive,
  emptyCostFilter,
  isCostFilterActive,
  isStatusActive,
  sortLabel,
  sortOptions,
  toggleIn,
  toggleStatus,
  type CostFilter,
  type DueFilter,
  type ItemFilter,
  type StatusValue,
} from './filters'

/**
 * What each status is called on screen. Beside the order rather than inside
 * `filters.ts`, which is the module the tests and the codec share and has no
 * business holding display strings.
 */
const STATUS_LABELS: Record<StatusValue, string> = {
  open: 'To do',
  done: 'Done',
  lapsed: 'Lapsed',
}

/**
 * How a list is narrowed and ordered (0013 R107-R122).
 *
 * <p>One row of chips, each opening a menu. Not a panel of expanded inputs:
 * this sits directly above a list a member scrolls on a 360px screen, and a
 * permanently open filter form would push the items off it (R122). The row
 * scrolls sideways rather than wrapping into three.
 *
 * <p>Every control is built from what the list declares (R120), so nothing here
 * offers a filter the items cannot answer. The component is stateless but for
 * which menu is open - the filter itself lives in the address (adr/0045).
 */
export function FilterBar({
  filter,
  fields,
  members,
  onChange,
}: {
  filter: ItemFilter
  fields: ListField[]
  members: ListMember[]
  onChange: (next: ItemFilter) => void
}) {
  const active = countActive(filter)
  const costFields = fields.filter((field) => field.kind === 'COST' && field.id)
  const hasAssignee = fields.some((field) => field.kind === 'ASSIGNEE')

  return (
    <Stack
      direction="row"
      spacing={1}
      role="group"
      aria-label="Filter and sort"
      sx={{
        alignItems: 'center',
        // Sideways rather than wrapping: three rows of chips above a list is
        // most of a phone screen.
        overflowX: 'auto',
        py: 0.5,
        // The scrollbar must not clip the chips' focus rings.
        '&::-webkit-scrollbar': { height: 0 },
      }}
    >
      <MenuChip label={`Status: ${statusLabel(filter)}`} active={isStatusActive(filter)}>
        {(close) => (
          <>
            {STATUS_ORDER.map((value) => (
              <MenuItem
                key={value}
                onClick={() => onChange({ ...filter, status: toggleStatus(filter.status, value) })}
              >
                <Checkbox checked={filter.status.includes(value)} size="small" />
                <ListItemText primary={STATUS_LABELS[value]} />
              </MenuItem>
            ))}
            <Divider />
            <MenuItem
              onClick={() => {
                onChange({ ...filter, status: DEFAULT_FILTER.status })
                close()
              }}
            >
              <ListItemText primary="Just to do" />
            </MenuItem>
          </>
        )}
      </MenuChip>

      {hasAssignee && (
        <MenuChip
          label={`Assignee: ${assigneeLabel(filter, members)}`}
          active={filter.assigneeIds.length > 0}
        >
          {() => (
            <>
              <MenuItem
                onClick={() =>
                  onChange({ ...filter, assigneeIds: toggleIn(filter.assigneeIds, UNASSIGNED) })
                }
              >
                <Checkbox checked={filter.assigneeIds.includes(UNASSIGNED)} size="small" />
                <ListItemText primary="Unassigned" />
              </MenuItem>
              <Divider />
              {members.map((member) => (
                <MenuItem
                  key={member.id}
                  onClick={() =>
                    onChange({ ...filter, assigneeIds: toggleIn(filter.assigneeIds, member.id) })
                  }
                >
                  <Checkbox checked={filter.assigneeIds.includes(member.id)} size="small" />
                  <ListItemText primary={member.displayName} />
                </MenuItem>
              ))}
            </>
          )}
        </MenuChip>
      )}

      <MenuChip label={`Due: ${dueLabel(filter.due)}`} active={filter.due !== null}>
        {(close) => (
          <DueMenu
            due={filter.due}
            onPick={(due) => {
              onChange({ ...filter, due })
              close()
            }}
            onRange={(due) => onChange({ ...filter, due })}
          />
        )}
      </MenuChip>

      {costFields.map((field) => {
        const cost = filter.costs[field.id!] ?? emptyCostFilter()
        return (
          <MenuChip
            key={field.id}
            label={`${field.label}: ${costLabel(field, cost)}`}
            active={isCostFilterActive(cost)}
          >
            {() => (
              <CostMenu
                field={field}
                cost={cost}
                onChange={(next) =>
                  onChange({ ...filter, costs: { ...filter.costs, [field.id!]: next } })
                }
              />
            )}
          </MenuChip>
        )
      })}

      <MenuChip label={`Sort: ${sortLabel(filter.sort, fields)}`} active={false}>
        {(close) => (
          <>
            {sortOptions(fields).map((option) => (
              <MenuItem
                key={option.key}
                selected={filter.sort === option.key}
                onClick={() => {
                  onChange({ ...filter, sort: option.key })
                  close()
                }}
              >
                <Radio checked={filter.sort === option.key} size="small" />
                <ListItemText primary={option.label} />
              </MenuItem>
            ))}
          </>
        )}
      </MenuChip>

      <Tooltip title={filter.descending ? 'Sorted last first' : 'Sorted first first'}>
        <IconButton
          size="small"
          aria-label={filter.descending ? 'Sort ascending' : 'Sort descending'}
          onClick={() => onChange({ ...filter, descending: !filter.descending })}
        >
          {filter.descending ? (
            <ArrowDownwardRounded fontSize="small" />
          ) : (
            <ArrowUpwardRounded fontSize="small" />
          )}
        </IconButton>
      </Tooltip>

      {/* The count and Clear are part of the bar, so a list showing less than
          it holds can never look like a list that holds that little (R107). */}
      {active > 0 && (
        <>
          <Typography role="status" variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
            {active === 1 ? '1 filter' : `${active} filters`}
          </Typography>
          <Button size="small" onClick={() => onChange(DEFAULT_FILTER)}>
            Clear
          </Button>
        </>
      )}
    </Stack>
  )
}

function DueMenu({
  due,
  onPick,
  onRange,
}: {
  due: DueFilter | null
  onPick: (due: DueFilter | null) => void
  onRange: (due: DueFilter) => void
}) {
  const range = due?.kind === 'range' ? due : null
  return (
    <>
      <MenuItem selected={due === null} onClick={() => onPick(null)}>
        <ListItemText primary="Any time" />
      </MenuItem>
      {(
        [
          ['overdue', 'Overdue'],
          ['today', 'Today'],
          ['week', 'Next 7 days'],
          ['none', 'No date'],
        ] as const
      ).map(([kind, label]) => (
        <MenuItem key={kind} selected={due?.kind === kind} onClick={() => onPick({ kind })}>
          <Radio checked={due?.kind === kind} size="small" />
          <ListItemText primary={label} />
        </MenuItem>
      ))}
      <Divider />
      {/* Inside the menu rather than behind another control: a range is two
          days, and both of them being visible is what makes it checkable. */}
      <Box sx={{ px: 2, py: 1 }} onKeyDown={(event) => event.stopPropagation()}>
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            type="date"
            label="From"
            value={range?.from ?? ''}
            slotProps={{ inputLabel: { shrink: true } }}
            onChange={(event) =>
              onRange({ kind: 'range', from: event.target.value, to: range?.to ?? event.target.value })
            }
          />
          <TextField
            size="small"
            type="date"
            label="To"
            value={range?.to ?? ''}
            slotProps={{ inputLabel: { shrink: true } }}
            onChange={(event) =>
              onRange({ kind: 'range', from: range?.from ?? event.target.value, to: event.target.value })
            }
          />
        </Stack>
      </Box>
    </>
  )
}

function CostMenu({
  field,
  cost,
  onChange,
}: {
  field: ListField
  cost: CostFilter
  onChange: (next: CostFilter) => void
}) {
  return (
    <>
      {field.costMode === 'FIXED' ? (
        field.choices.map((choice) => (
          <MenuItem
            key={choice.id}
            onClick={() =>
              onChange({ ...cost, choiceIds: toggleIn(cost.choiceIds, choice.id!) })
            }
          >
            <Checkbox checked={cost.choiceIds.includes(choice.id!)} size="small" />
            <ListItemText primary={choice.label} />
          </MenuItem>
        ))
      ) : (
        <Box sx={{ px: 2, py: 1 }} onKeyDown={(event) => event.stopPropagation()}>
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              type="number"
              label="At least"
              value={cost.min ?? ''}
              onChange={(event) => onChange({ ...cost, min: asNumber(event.target.value) })}
            />
            <TextField
              size="small"
              type="number"
              label={`At most${field.costUnitLabel ? ` (${field.costUnitLabel})` : ''}`}
              value={cost.max ?? ''}
              onChange={(event) => onChange({ ...cost, max: asNumber(event.target.value) })}
            />
          </Stack>
        </Box>
      )}
      <Divider />
      {/* R111: a blank is not a small amount, so the items that said nothing
          are in only when they are asked for. */}
      <MenuItem onClick={() => onChange({ ...cost, unanswered: !cost.unanswered })}>
        <Checkbox checked={cost.unanswered} size="small" />
        <ListItemText primary="Not answered" />
      </MenuItem>
    </>
  )
}

function asNumber(raw: string): number | null {
  if (raw.trim() === '') {
    return null
  }
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

/**
 * The chip's own label.
 *
 * <p>Names the selection when it is short enough to read and counts it
 * otherwise. The old version said "To do & done" for anything that was not
 * exactly one value, which with three statuses would have been a label that
 * is simply wrong about what is on screen.
 */
function statusLabel(filter: ItemFilter): string {
  if (filter.status.length === 0 || filter.status.length === STATUS_ORDER.length) {
    return 'Any'
  }
  return filter.status.map((value) => STATUS_LABELS[value]).join(' & ')
}

function assigneeLabel(filter: ItemFilter, members: ListMember[]): string {
  if (filter.assigneeIds.length === 0) {
    return 'Anyone'
  }
  if (filter.assigneeIds.length > 2) {
    return `${filter.assigneeIds.length} people`
  }
  return filter.assigneeIds
    .map((id) =>
      id === UNASSIGNED
        ? 'Unassigned'
        : (members.find((member) => member.id === id)?.displayName ?? 'Someone'),
    )
    .join(', ')
}

function dueLabel(due: DueFilter | null): string {
  if (due === null) {
    return 'Any time'
  }
  if (due.kind === 'range') {
    return `${due.from} to ${due.to}`
  }
  return { overdue: 'Overdue', today: 'Today', week: 'Next 7 days', none: 'No date' }[due.kind]
}

function costLabel(field: ListField, cost: CostFilter): string {
  if (!isCostFilterActive(cost)) {
    return 'Any'
  }
  const parts: string[] = []
  if (field.costMode === 'FIXED') {
    const labels = cost.choiceIds.map(
      (id) => field.choices.find((choice) => choice.id === id)?.label ?? '?',
    )
    if (labels.length > 2) {
      parts.push(`${labels.length} chosen`)
    } else {
      parts.push(...labels)
    }
  } else if (cost.min !== null || cost.max !== null) {
    parts.push(`${cost.min ?? ''}-${cost.max ?? ''}`)
  }
  if (cost.unanswered) {
    parts.push('blank')
  }
  return parts.join(', ')
}

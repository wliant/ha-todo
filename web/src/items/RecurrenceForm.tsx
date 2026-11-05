import Alert from '@mui/material/Alert'
import FormControlLabel from '@mui/material/FormControlLabel'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import type { ListField, ListMember } from '../api/lists'
import type { ItemCostValue } from '../api/items'
import {
  WEEKDAYS,
  type Frequency,
  type SaveRecurrenceRule,
  type Weekday,
} from '../api/recurrence'

/**
 * A standing instruction: what to make, and when (R41).
 *
 * <p>The schedule is three shapes and an interval, which is the whole
 * household vocabulary. The form shows only the parameters the chosen shape
 * actually uses, because a day-of-month input beside a weekly rule is an
 * invitation to set something that will be ignored.
 */
export function RecurrenceForm({
  fields,
  members,
  value,
  onChange,
}: {
  fields: ListField[]
  members: ListMember[]
  value: SaveRecurrenceRule
  onChange: (value: SaveRecurrenceRule) => void
}) {
  const assignee = fields.find((field) => field.kind === 'ASSIGNEE')
  const dueAt = fields.find((field) => field.kind === 'DUE_AT')
  const costs = fields.filter((field) => field.kind === 'COST')

  function setCost(fieldId: string, next: Partial<ItemCostValue>) {
    const others = value.costValues.filter((cost) => cost.fieldId !== fieldId)
    const current = value.costValues.find((cost) => cost.fieldId === fieldId)
    onChange({
      ...value,
      costValues: [...others, { fieldId, choiceId: null, amount: null, ...current, ...next }],
    })
  }

  function costFor(fieldId: string): ItemCostValue | undefined {
    return value.costValues.find((cost) => cost.fieldId === fieldId)
  }

  function toggleDay(day: Weekday) {
    const days = value.daysOfWeek.includes(day)
      ? value.daysOfWeek.filter((candidate) => candidate !== day)
      : [...value.daysOfWeek, day]
    onChange({ ...value, daysOfWeek: days })
  }

  return (
    <Stack spacing={2}>
      <TextField
        label="What recurs"
        required
        autoFocus
        value={value.description}
        onChange={(event) => onChange({ ...value, description: event.target.value })}
        slotProps={{ htmlInput: { maxLength: 300 } }}
      />

      <TextField
        select
        label="How often"
        value={value.frequency}
        onChange={(event) =>
          onChange({ ...value, frequency: event.target.value as Frequency })
        }
      >
        <MenuItem value="DAILY">Every day</MenuItem>
        <MenuItem value="WEEKLY">Chosen days of the week</MenuItem>
        <MenuItem value="MONTHLY">A day of the month</MenuItem>
      </TextField>

      <TextField
        type="number"
        label={intervalLabel(value.frequency)}
        value={value.intervalCount}
        onChange={(event) =>
          onChange({ ...value, intervalCount: Math.max(1, Number(event.target.value) || 1) })
        }
        slotProps={{ htmlInput: { min: 1, max: 52 } }}
        helperText="1 means every one. 2 means every other."
      />

      {value.frequency === 'WEEKLY' && (
        <Stack spacing={0.5}>
          <Typography variant="caption" color="text.secondary">
            Which days
          </Typography>
          <ToggleButtonGroup
            size="small"
            value={value.daysOfWeek}
            aria-label="Which days"
            sx={{ flexWrap: 'wrap' }}
          >
            {WEEKDAYS.map((day) => (
              <ToggleButton
                key={day.value}
                value={day.value}
                aria-label={day.value}
                selected={value.daysOfWeek.includes(day.value)}
                onChange={() => toggleDay(day.value)}
              >
                {day.short}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Stack>
      )}

      {value.frequency === 'MONTHLY' && (
        <TextField
          type="number"
          label="Day of the month"
          required
          value={value.dayOfMonth ?? ''}
          onChange={(event) =>
            onChange({ ...value, dayOfMonth: event.target.value === '' ? null : Number(event.target.value) })
          }
          slotProps={{ htmlInput: { min: 1, max: 31 } }}
          helperText="The 31st falls on the last day of a shorter month."
        />
      )}

      {dueAt && (
        <TextField
          label="Due at"
          type="time"
          value={(value.dueTime ?? '').slice(0, 5)}
          onChange={(event) =>
            onChange({ ...value, dueTime: event.target.value || null })
          }
          slotProps={{ inputLabel: { shrink: true } }}
          helperText="Leave blank for no due time."
        />
      )}

      <TextField
        type="number"
        label="Add it this many days early"
        value={value.leadDays}
        onChange={(event) =>
          onChange({ ...value, leadDays: Math.max(0, Number(event.target.value) || 0) })
        }
        slotProps={{ htmlInput: { min: 0, max: 30 } }}
        helperText="So Sunday's chore is already on the list on Friday."
      />

      {assignee && (
        <TextField
          select
          label="Assignee"
          value={value.assigneeId ?? ''}
          onChange={(event) => onChange({ ...value, assigneeId: event.target.value || null })}
        >
          <MenuItem value="">Nobody</MenuItem>
          {members.map((member) => (
            <MenuItem key={member.id} value={member.id}>
              {member.displayName}
            </MenuItem>
          ))}
        </TextField>
      )}

      {costs.map((cost) =>
        cost.costMode === 'FIXED' ? (
          <TextField
            key={cost.id}
            select
            label={cost.label ?? 'Cost'}
            value={costFor(cost.id!)?.choiceId ?? ''}
            onChange={(event) =>
              setCost(cost.id!, { choiceId: event.target.value || null, amount: null })
            }
          >
            <MenuItem value="">Not set</MenuItem>
            {cost.choices.map((choice) => (
              <MenuItem key={choice.id} value={choice.id!}>
                {choice.label}
              </MenuItem>
            ))}
          </TextField>
        ) : (
          <TextField
            key={cost.id}
            type="number"
            label={`${cost.label} (${cost.costUnitLabel})`}
            value={costFor(cost.id!)?.amount ?? ''}
            onChange={(event) =>
              setCost(cost.id!, {
                amount: event.target.value === '' ? null : Number(event.target.value),
                choiceId: null,
              })
            }
            slotProps={{ htmlInput: { min: 0, step: 'any' } }}
          />
        ),
      )}

      <TextField
        label="Stops after"
        type="date"
        value={value.endsOn ?? ''}
        onChange={(event) => onChange({ ...value, endsOn: event.target.value || null })}
        slotProps={{ inputLabel: { shrink: true } }}
        helperText="Leave blank to keep going."
      />

      <FormControlLabel
        control={
          <Switch
            checked={value.paused}
            onChange={(event) => onChange({ ...value, paused: event.target.checked })}
          />
        }
        label="Paused"
      />

      {value.frequency === 'WEEKLY' && value.daysOfWeek.length === 0 && (
        <Alert severity="info">Pick at least one day of the week.</Alert>
      )}
    </Stack>
  )
}

function intervalLabel(frequency: Frequency): string {
  switch (frequency) {
    case 'DAILY':
      return 'Every how many days'
    case 'WEEKLY':
      return 'Every how many weeks'
    case 'MONTHLY':
      return 'Every how many months'
  }
}

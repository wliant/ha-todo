import Alert from '@mui/material/Alert'
import FormControlLabel from '@mui/material/FormControlLabel'
import FormLabel from '@mui/material/FormLabel'
import InputAdornment from '@mui/material/InputAdornment'
import MenuItem from '@mui/material/MenuItem'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type {
  ListIcon as ListIconToken,
  Member,
} from '../api/lists'
import type { Rollup, TargetDirection, TrackerPeriod, TrackerScope } from '../api/trackers'
import { IconPicker } from '../lists/IconPicker'
import { AudiencePicker } from '../lists/AudiencePicker'
import { describeRollup, describeScope } from './shape'

export interface TrackerFormValue {
  name: string
  description: string
  icon: ListIconToken
  unit: string
  period: TrackerPeriod
  scope: TrackerScope
  rollup: Rollup
  decimals: number
  /** Blank means no target; the direction travels with it or neither is sent. */
  target: string
  targetDirection: TargetDirection
  memberIds: string[]
}

const PERIODS: { value: TrackerPeriod; label: string; hint: string }[] = [
  { value: 'DAY', label: 'Daily', hint: 'A number for a day — weight, minutes read' },
  { value: 'WEEK', label: 'Weekly', hint: 'A number for a week, beginning Monday' },
  { value: 'MONTH', label: 'Monthly', hint: 'A number for a month — what the groceries cost' },
]

/**
 * Declaring what a tracker is (requirements/0019 R159-R161).
 *
 * <p>Shared by creating and editing, so the two cannot drift into describing
 * the same thing differently - the reason `ListForm` is shared.
 *
 * <p>`reconfigurable` is the server's answer to R161, not a count this form
 * interprets: once a tracker holds a number, its period and scope are shown
 * **read-only with the reason**, rather than as controls that would be refused.
 */
export function TrackerForm({
  value,
  onChange,
  members,
  ownerName,
  reconfigurable = true,
}: {
  value: TrackerFormValue
  onChange: (value: TrackerFormValue) => void
  members: Member[]
  ownerName: string
  reconfigurable?: boolean
}) {
  function set<K extends keyof TrackerFormValue>(key: K, next: TrackerFormValue[K]) {
    onChange({ ...value, [key]: next })
  }

  return (
    <Stack spacing={2.5}>
      <TextField
        label="Name"
        required
        value={value.name}
        onChange={(event) => set('name', event.target.value)}
        slotProps={{ htmlInput: { maxLength: 80 } }}
      />

      <TextField
        label="Description"
        multiline
        minRows={2}
        value={value.description}
        onChange={(event) => set('description', event.target.value)}
        slotProps={{ htmlInput: { maxLength: 500 } }}
        helperText="Optional."
      />

      <IconPicker value={value.icon} onChange={(icon) => set('icon', icon)} />

      <TextField
        label="Unit"
        required
        value={value.unit}
        onChange={(event) => set('unit', event.target.value)}
        slotProps={{ htmlInput: { maxLength: 16 } }}
        helperText="kg, SGD, minutes — a label, never converted to anything else."
      />

      {!reconfigurable && (
        <Alert severity="info">
          This tracker already holds numbers, so how often it is recorded and whose numbers it
          holds are fixed. Changing either would strand what is already stored.
        </Alert>
      )}

      <Stack spacing={1}>
        <FormLabel component="legend">How often</FormLabel>
        {reconfigurable ? (
          <RadioGroup
            value={value.period}
            onChange={(event) => set('period', event.target.value as TrackerPeriod)}
          >
            {PERIODS.map((period) => (
              <FormControlLabel
                key={period.value}
                value={period.value}
                control={<Radio />}
                label={
                  <Stack>
                    <Typography variant="body2">{period.label}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {period.hint}
                    </Typography>
                  </Stack>
                }
              />
            ))}
          </RadioGroup>
        ) : (
          <Typography role="status">
            {PERIODS.find((period) => period.value === value.period)?.label}
          </Typography>
        )}
      </Stack>

      <Stack spacing={1}>
        <FormLabel component="legend">Whose numbers</FormLabel>
        {reconfigurable ? (
          <RadioGroup
            value={value.scope}
            onChange={(event) => set('scope', event.target.value as TrackerScope)}
          >
            {(['PER_MEMBER', 'SHARED'] as TrackerScope[]).map((scope) => (
              <FormControlLabel
                key={scope}
                value={scope}
                control={<Radio />}
                label={
                  <Stack>
                    <Typography variant="body2">
                      {scope === 'PER_MEMBER' ? 'One each' : "The household's"}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {describeScope(scope)}
                    </Typography>
                  </Stack>
                }
              />
            ))}
          </RadioGroup>
        ) : (
          <Typography role="status">{describeScope(value.scope)}</Typography>
        )}
      </Stack>

      {/* Phrased as what happens to two numbers in one bucket, because that is
          the question being answered - not as the name of an aggregate. */}
      <TextField
        select
        label="If there are several in one period"
        value={value.rollup}
        onChange={(event) => set('rollup', event.target.value as Rollup)}
      >
        {(['LATEST', 'SUM', 'AVERAGE'] as Rollup[]).map((rollup) => (
          <MenuItem key={rollup} value={rollup}>
            {describeRollup(rollup, value.period)}
          </MenuItem>
        ))}
      </TextField>

      <TextField
        select
        label="Decimal places"
        value={String(value.decimals)}
        onChange={(event) => set('decimals', Number(event.target.value))}
        helperText="How the numbers are printed. What is stored keeps its precision."
      >
        {[0, 1, 2, 3].map((decimals) => (
          <MenuItem key={decimals} value={String(decimals)}>
            {decimals}
          </MenuItem>
        ))}
      </TextField>

      <Stack spacing={1}>
        <FormControlLabel
          control={
            <Switch
              checked={value.target !== ''}
              onChange={(event) => set('target', event.target.checked ? '0' : '')}
            />
          }
          label="Aim for a number"
        />
        {value.target !== '' && (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
            <TextField
              select
              label="Stay"
              value={value.targetDirection}
              onChange={(event) =>
                set('targetDirection', event.target.value as TargetDirection)
              }
              sx={{ minWidth: '10rem' }}
            >
              <MenuItem value="AT_MOST">at or below</MenuItem>
              <MenuItem value="AT_LEAST">at or above</MenuItem>
            </TextField>
            <TextField
              label="Target"
              type="number"
              value={value.target}
              onChange={(event) => set('target', event.target.value)}
              slotProps={{
                htmlInput: { step: 'any', inputMode: 'decimal' },
                input: {
                  endAdornment: (
                    <InputAdornment position="end">{value.unit || 'units'}</InputAdornment>
                  ),
                },
              }}
            />
          </Stack>
        )}
      </Stack>

      <AudiencePicker
        members={members}
        ownerName={ownerName}
        selected={value.memberIds}
        onChange={(memberIds) => set('memberIds', memberIds)}
        noun="tracker"
      />
    </Stack>
  )
}

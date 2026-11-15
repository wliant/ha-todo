import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import FormLabel from '@mui/material/FormLabel'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import AddRounded from '@mui/icons-material/AddRounded'
import RemoveRounded from '@mui/icons-material/RemoveRounded'
import { useState } from 'react'
import { addDays, civilIn, dayKey, instantOf, now } from '../dashboard/calendar'
import { useHouseholdZone } from '../app/useHouseholdZone'

/**
 * The time a date carries unless the member says otherwise (R95).
 *
 * <p>End of day, not midnight: midnight belongs to the day *before* the one a
 * member meant, so a chore dated Thursday would sit on Wednesday's agenda row
 * and read as overdue by Thursday lunchtime.
 */
const DEFAULT_TIME = '23:59'

/**
 * A due date a member can set in one tap (R94-R97).
 *
 * <p>The ISO instant stays the single source of truth: this takes one and emits
 * one, and everything on screen is derived from it. An external change - an
 * item loaded for edit, a form reset after save - therefore cannot leave the
 * control showing something the form is not holding.
 *
 * <p>**Every date here is the household's** (R96, adr/0036). The old bare
 * `datetime-local` spoke the device's zone, so "Thursday 09:00" typed on a
 * phone set to London was an instant the agenda filed under Wednesday. That is
 * what `instantOf` below exists to prevent, and it is the only place a zone is
 * applied.
 */
export function DueAtField({
  value,
  required,
  onChange,
  compact = false,
}: {
  value: string | null | undefined
  required: boolean
  onChange: (value: string | null) => void
  /**
   * The chips and nothing else (0017 R149), which is what the add form shows
   * while collapsed. A render decision about this control rather than a second
   * control: the emit path, `instantOf` and `DEFAULT_TIME` are shared, so a
   * date set compactly is the same end-of-day instant in the household's zone
   * as one set from the picker (adr/0050).
   */
  compact?: boolean
}) {
  const zone = useHouseholdZone()

  /**
   * The time a member typed before picking a date, which has nowhere else to
   * live - with no date there is no instant to hold it. The only local state
   * here, deliberately.
   */
  const [pendingTime, setPendingTime] = useState(DEFAULT_TIME)

  const civil = value ? civilIn(new Date(value), zone) : null
  const date = civil ? dayKey(civil) : ''
  const time = civil ? clockOf(civil) : pendingTime

  function emit(dateText: string, timeText: string) {
    if (!dateText) {
      onChange(null)
      return
    }
    const [year, month, day] = dateText.split('-').map(Number)
    const [hours, minutes] = timeText.split(':').map(Number)
    if (!year || !month || !day || Number.isNaN(hours) || Number.isNaN(minutes)) {
      return
    }
    // instantOf, never `new Date(...)` on a local string: this is what makes
    // the day the member picked the household's day (R96).
    onChange(instantOf(new Date(year, month - 1, day, hours, minutes), zone).toISOString())
  }

  /** Today and tomorrow in the household's zone, not the device's. */
  function shortcut(offset: number) {
    emit(dayKey(addDays(now(zone), offset)), time)
  }

  function step(direction: 1 | -1) {
    if (!civil) {
      return
    }
    emit(dayKey(addDays(civil, direction)), time)
  }

  const today = dayKey(now(zone))
  const tomorrow = dayKey(addDays(now(zone), 1))

  return (
    <Box>
      <FormLabel component="legend" required={required} sx={{ fontSize: '0.75rem' }}>
        Due
      </FormLabel>

      <Stack direction="row" spacing={1} sx={{ mt: 0.5, mb: 1, flexWrap: 'wrap', gap: 1 }}>
        {/* Also the readout: a chip renders selected when the date matches, so
            a member can see at a glance that "tomorrow" is what they set. */}
        <Chip
          label="Today"
          size="small"
          color={date === today ? 'primary' : 'default'}
          variant={date === today ? 'filled' : 'outlined'}
          onClick={() => shortcut(0)}
        />
        <Chip
          label="Tomorrow"
          size="small"
          color={date === tomorrow ? 'primary' : 'default'}
          variant={date === tomorrow ? 'filled' : 'outlined'}
          onClick={() => shortcut(1)}
        />
        {/* Compact mode must never hold a date it does not show. No path
            reaches this today - the add form cannot be collapsed once it has
            been expanded - so it is one readout chip rather than a mechanism,
            and carries no onClick because a tap here has nothing to do. */}
        {compact && date && date !== today && date !== tomorrow && (
          <Chip label={date} size="small" color="primary" variant="filled" />
        )}
        {/* R97. Absent when the list insists on a date, rather than present and
            producing an unsubmittable form. */}
        {!required && date && (
          <Chip label="Clear" size="small" variant="outlined" onClick={() => onChange(null)} />
        )}
      </Stack>

      {/* The picker, the steppers and the time box are what "More fields" is
          for (R149). Not rendered rather than hidden: a control the member
          cannot see must not be one they can tab into. */}
      {!compact && (
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          {/* Disabled with no date set: a stepper with nothing to step would have
              to invent a starting point, and nobody would guess which. */}
          <IconButton
            aria-label="A day earlier"
            size="small"
            disabled={!date}
            onClick={() => step(-1)}
          >
            <RemoveRounded fontSize="small" />
          </IconButton>

          <TextField
            label="Date"
            type="date"
            size="small"
            required={required}
            value={date}
            onChange={(event) => emit(event.target.value, time)}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ flex: 1, minWidth: '8.5rem' }}
          />

          <IconButton aria-label="A day later" size="small" disabled={!date} onClick={() => step(1)}>
            <AddRounded fontSize="small" />
          </IconButton>

          <TextField
            label="Time"
            type="time"
            size="small"
            value={time}
            onChange={(event) => {
              const next = event.target.value
              if (!next) {
                return
              }
              setPendingTime(next)
              if (date) {
                emit(date, next)
              }
            }}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: '7.5rem' }}
          />
        </Stack>
      )}
    </Box>
  )
}

/** A civil date's wall clock as `HH:mm`, for a `type="time"` input. */
function clockOf(civil: Date): string {
  const hours = `${civil.getHours()}`.padStart(2, '0')
  const minutes = `${civil.getMinutes()}`.padStart(2, '0')
  return `${hours}:${minutes}`
}

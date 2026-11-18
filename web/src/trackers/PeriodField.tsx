import Chip from '@mui/material/Chip'
import FormLabel from '@mui/material/FormLabel'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import RemoveRounded from '@mui/icons-material/RemoveRounded'
import { useMemo } from 'react'
import type { TrackerPeriod } from '../api/trackers'
import { useHouseholdZone } from '../app/useHouseholdZone'
import { now } from '../dashboard/calendar'
import { civilFromKey, describePeriod, periodKey, periodNoun, stepPeriod } from './period'

/**
 * Which bucket a number belongs to (requirements/0019 R162).
 *
 * <p>Asks for exactly the grain the tracker declared: a date for a daily
 * tracker, a week for a weekly one, a month for a monthly one. A month picker
 * on a tracker that buckets by month is the difference between "which day of
 * November?" - a question with no answer - and "November".
 *
 * <p>The value is a canonical `YYYY-MM-DD` bucket key throughout. Nothing here
 * holds a `Date`, and the only clock reading happens inside a `useMemo`, never
 * during render - the purity rule `items/filters.ts` follows, and the reason
 * `new Date()` in a render body is what the lint rule exists to catch.
 */
export function PeriodField({
  period,
  value,
  onChange,
}: {
  period: TrackerPeriod
  /** A canonical bucket key. */
  value: string
  onChange: (value: string) => void
}) {
  const zone = useHouseholdZone()
  // Inside the memo: a `Civil` computed during render changes identity every
  // time, and today only moves at midnight.
  const today = useMemo(() => periodKey(now(zone), period), [zone, period])
  const previous = useMemo(
    () => periodKey(stepPeriod(now(zone), period, -1), period),
    [zone, period],
  )

  const bucket = civilFromKey(value)
  const noun = periodNoun(period)

  function step(by: number) {
    onChange(periodKey(stepPeriod(bucket, period, by), period))
  }

  return (
    <Stack spacing={1}>
      <FormLabel component="legend">Which {noun}</FormLabel>
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Chip
          label={period === 'DAY' ? 'Today' : `This ${noun}`}
          onClick={() => onChange(today)}
          color={value === today ? 'primary' : 'default'}
          variant={value === today ? 'filled' : 'outlined'}
          // The fill colour is not the only carrier of this fact: a row of
          // toggles whose state lives in a colour is unreadable to anything
          // not looking at it (0017 R150).
          aria-pressed={value === today}
        />
        <Chip
          label={period === 'DAY' ? 'Yesterday' : `Last ${noun}`}
          onClick={() => onChange(previous)}
          color={value === previous ? 'primary' : 'default'}
          variant={value === previous ? 'filled' : 'outlined'}
          aria-pressed={value === previous}
        />
      </Stack>

      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <IconButton
          size="small"
          aria-label={`Previous ${noun}`}
          onClick={() => step(-1)}
        >
          <RemoveRounded fontSize="small" />
        </IconButton>

        {period === 'DAY' ? (
          <TextField
            size="small"
            type="date"
            label="Date"
            value={value}
            // The input's value is already a civil date string, so it is
            // passed through rather than parsed - there is no instant here for
            // a zone to be applied to (adr/0052).
            onChange={(event) => event.target.value && onChange(event.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        ) : period === 'MONTH' ? (
          <TextField
            size="small"
            type="month"
            label="Month"
            value={value.slice(0, 7)}
            onChange={(event) =>
              event.target.value && onChange(`${event.target.value}-01`)
            }
            slotProps={{ inputLabel: { shrink: true } }}
          />
        ) : (
          // A week has no native control worth using, so it is named rather
          // than picked - the steppers are how it moves.
          <Typography role="status" sx={{ minWidth: '8rem', textAlign: 'center' }}>
            {describePeriod(bucket, period)}
          </Typography>
        )}

        <IconButton size="small" aria-label={`Next ${noun}`} onClick={() => step(1)}>
          <AddRounded fontSize="small" />
        </IconButton>
      </Stack>

      {period !== 'WEEK' && (
        <Typography variant="caption" color="text.secondary">
          {describePeriod(bucket, period)}
        </Typography>
      )}
    </Stack>
  )
}

import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import type { RangeKey } from './range'
import { RANGES } from './range'

export function RangeChips({
  value,
  onChange,
}: {
  value: RangeKey
  onChange: (range: RangeKey) => void
}) {
  return (
    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }} role="group"
           aria-label="How far back">
      {RANGES.map((range) => (
        <Chip
          key={range.key}
          label={range.label}
          onClick={() => onChange(range.key)}
          color={value === range.key ? 'primary' : 'default'}
          variant={value === range.key ? 'filled' : 'outlined'}
          // The fill is not the only carrier of which one is on.
          aria-pressed={value === range.key}
        />
      ))}
    </Stack>
  )
}

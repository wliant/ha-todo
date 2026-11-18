import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import TrendingDownRounded from '@mui/icons-material/TrendingDownRounded'
import TrendingFlatRounded from '@mui/icons-material/TrendingFlatRounded'
import TrendingUpRounded from '@mui/icons-material/TrendingUpRounded'
import type { TrackerSeries } from '../api/trackers'
import { againstTarget, formatAmount, headline } from './series'

/**
 * The figure at the top of a tracker (requirements/0019 R167, R168).
 *
 * <p>Three facts, in the order a member wants them: what it is now, which way
 * it moved, and how far that is from the target. The target line is drawn on
 * the chart; this is the sentence version of it, because "2.4 above" is the
 * part somebody reads out loud.
 */
export function TrackerHeadline({ series }: { series: TrackerSeries }) {
  const { latest, latestLabel, previous } = headline(series)
  const target = againstTarget(latest, series)

  if (latest === null) {
    return (
      <Typography role="status" color="text.secondary">
        Nothing recorded yet.
      </Typography>
    )
  }

  const movement = previous === null ? null : latest - previous
  const Trend =
    movement === null || movement === 0
      ? TrendingFlatRounded
      : movement > 0
        ? TrendingUpRounded
        : TrendingDownRounded

  return (
    <Stack spacing={0.5}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
        <Typography variant="h4" component="p" sx={{ fontVariantNumeric: 'tabular-nums' }}>
          {formatAmount(latest, series.decimals)}
        </Typography>
        <Typography variant="h6" component="span" color="text.secondary">
          {series.unit}
        </Typography>
        {latestLabel && (
          <Typography variant="body2" color="text.secondary">
            {latestLabel}
          </Typography>
        )}
      </Stack>

      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        {movement !== null && (
          <Chip
            size="small"
            variant="outlined"
            // The icon and the number carry the same fact, so a colour-blind
            // member and a test both read it.
            icon={<Trend fontSize="small" />}
            label={
              movement === 0
                ? 'No change'
                : `${movement > 0 ? '+' : '−'}${formatAmount(Math.abs(movement), series.decimals)} ${series.unit}`
            }
          />
        )}
        {target && (
          <Chip
            size="small"
            color={target.met ? 'success' : 'default'}
            variant={target.met ? 'filled' : 'outlined'}
            label={
              target.distance === 0
                ? 'On target'
                : `${formatAmount(target.distance, series.decimals)} ${series.unit} ${
                    target.met ? 'inside' : 'off'
                  } target`
            }
          />
        )}
      </Stack>
    </Stack>
  )
}

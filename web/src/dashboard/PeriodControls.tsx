import IconButton from '@mui/material/IconButton'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import ChevronLeftRounded from '@mui/icons-material/ChevronLeftRounded'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import { now, type Civil, type Zone } from './calendar'
import { isCurrentPeriod, returnLabel, step, type Range } from './period'

/**
 * Moving between periods (requirements/0008 R74, R75).
 *
 * <p>One control for all three ranges rather than a copy inside each view: it
 * is the same gesture whichever range is chosen, and three copies would drift
 * the moment one of them gained a label the others did not.
 *
 * <p>Everything about *layout* reads the anchor; everything about *now* reads
 * the clock. That split is why the return control knows it has nothing to do
 * (R75) without the views having to tell it.
 *
 * <p>It says nothing about which period is on screen, deliberately: every view
 * already heads itself with that, and two copies of "April 2027" stacked on top
 * of each other is what the first version looked like.
 */
export function PeriodControls({
  range,
  anchor,
  zone,
  onMove,
}: {
  range: Range
  anchor: Civil
  zone: Zone
  onMove: (to: Civil) => void
}) {
  const today = now(zone)
  const atPresent = isCurrentPeriod(range, anchor, today)

  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
      {/* The way back first, then the steps: the order every calendar
          application uses, so nobody has to look for it. */}
      <Button
        size="small"
        variant="outlined"
        onClick={() => onMove(today)}
        // Unavailable rather than hidden: a control that comes and goes is one
        // a member has to look for. Disabled, it still says where it goes.
        disabled={atPresent}
      >
        {returnLabel(range)}
      </Button>
      <IconButton
        aria-label={`Previous ${range}`}
        onClick={() => onMove(step(range, anchor, -1))}
        size="small"
      >
        <ChevronLeftRounded />
      </IconButton>
      <IconButton
        aria-label={`Next ${range}`}
        onClick={() => onMove(step(range, anchor, 1))}
        size="small"
      >
        <ChevronRightRounded />
      </IconButton>
    </Stack>
  )
}

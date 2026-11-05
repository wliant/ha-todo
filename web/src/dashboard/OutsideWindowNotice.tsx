import Alert from '@mui/material/Alert'
import type { EventWindow } from '../api/dashboard'
import { dayKey, type Civil } from './calendar'

/**
 * Says when the member has stepped past what the product has read
 * (requirements/0008 R77).
 *
 * <p>Deliberately separate from {@link StaleFeedsNotice}: that one says a
 * calendar broke, this one says nobody looked that far. They are different
 * promises and merging them would blur both.
 *
 * <p>The failure this prevents is quiet: an empty February looks exactly like a
 * February with nothing on. Only the product knows which it is, so only the
 * product can say.
 */
export function OutsideWindowNotice({
  from,
  to,
  window,
}: {
  /** The range on screen, inclusive. */
  from: Civil
  to: Civil
  window: EventWindow | undefined
}) {
  if (!window) {
    return null
  }
  const first = dayKey(from)
  const last = dayKey(to)
  const wholly = first > window.to || last < window.from
  const partly = first < window.from || last > window.to

  if (!wholly && !partly) {
    return null
  }

  return (
    <Alert severity="info" role="status">
      {wholly
        ? `No calendar events have been read this far ${first > window.to ? 'ahead' : 'back'}, so none are shown. Events are kept from ${readable(window.from)} to ${readable(window.to)}.`
        : `Part of this range is beyond the calendar events that have been read, so some may be missing. Events are kept from ${readable(window.from)} to ${readable(window.to)}.`}
    </Alert>
  )
}

/** A plain `YYYY-MM-DD` read as a date, never as an instant. */
function readable(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

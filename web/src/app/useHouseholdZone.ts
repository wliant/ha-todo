import { useCurrentUser } from '../auth/useCurrentUser'
import type { Zone } from '../dashboard/calendar'

/**
 * Last resort only, for the render between mount and the session resolving.
 *
 * <p>Deliberately a constant and **not** `Intl.DateTimeFormat().resolvedOptions().timeZone`:
 * falling back to the device would make a traveller's first paint disagree with
 * their second, which is the one bug adr/0036 exists to prevent.
 */
const BEFORE_THE_SESSION_IS_KNOWN = 'Asia/Singapore'

/**
 * The zone every date in the UI is read in.
 *
 * <p>A hook rather than a prop threaded down, because the alternative is a
 * component reaching for the device's clock out of habit.
 */
export function useHouseholdZone(): Zone {
  const { data } = useCurrentUser()
  return data?.timezone ?? BEFORE_THE_SESSION_IS_KNOWN
}

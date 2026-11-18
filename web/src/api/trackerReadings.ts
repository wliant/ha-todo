import { api } from './http'

/**
 * The numbers themselves (requirements/0019 R162).
 *
 * <p>Nothing here carries an instant. A bucket is a household date, written
 * `YYYY-MM-DD`, from the column through to this type - so there is no second
 * zone for the browser to resolve it against, and the `new Date(localString)`
 * trap that `DueAtField` exists to avoid cannot occur here at all (adr/0052).
 */
export interface Reading {
  id: string
  trackerId: string
  /** Null for a shared tracker: the series is the household's. */
  memberId: string | null
  memberDisplayName: string | null
  memberAvatarImageId: string | null
  periodStart: string
  amount: number
  note: string | null
  /** Who typed it. On a shared tracker this is the only record of authorship. */
  recordedByDisplayName: string | null
  recordedAt: string
  /**
   * Whether *this* caller may correct it (R165).
   *
   * <p>Computed by the server from one predicate. The client renders the flag
   * and never recomputes it from the scope plus the signed-in member: a second
   * copy is how somebody gets a control that 403s, or - failing nowhere - no
   * control on a number they may fix.
   */
  editable: boolean
  version: number
}

export interface SaveReading {
  version?: number
  /** The bucket, `YYYY-MM-DD`. The server canonicalises it to the period's start. */
  periodStart: string
  amount: number
  note?: string | null
}

export async function fetchReadings(trackerId: string): Promise<Reading[]> {
  const { data } = await api.get<Reading[]>(`/trackers/${trackerId}/readings`)
  return data
}

export async function recordReading(
  trackerId: string,
  reading: SaveReading,
): Promise<Reading> {
  const { data } = await api.post<Reading>(`/trackers/${trackerId}/readings`, reading)
  return data
}

export async function updateReading(id: string, reading: SaveReading): Promise<Reading> {
  const { data } = await api.patch<Reading>(`/tracker-readings/${id}`, reading)
  return data
}

export async function deleteReading(id: string): Promise<void> {
  await api.delete(`/tracker-readings/${id}`)
}

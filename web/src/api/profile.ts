import { api } from './http'

export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM'

export interface Preferences {
  theme: ThemePreference
}

export async function fetchPreferences(): Promise<Preferences> {
  const { data } = await api.get<Preferences>('/me/preferences')
  return data
}

export async function updatePreferences(theme: ThemePreference): Promise<Preferences> {
  const { data } = await api.patch<Preferences>('/me/preferences', { theme })
  return data
}

/**
 * Sets or clears the caller's own password. Null clears it, which is how "no
 * password required" is turned on. No current password is asked for - see
 * docs/adr/0017.
 */
export async function setOwnPassword(password: string | null): Promise<void> {
  await api.put('/me/password', { password })
}

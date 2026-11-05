import { useEffect } from 'react'
import { useColorScheme } from '@mui/material/styles'
import { useQuery } from '@tanstack/react-query'
import { fetchPreferences } from '../api/profile'
import { useCurrentUser } from '../auth/useCurrentUser'
import { preferencesKey, toMuiMode } from './useThemePreference'

/**
 * Brings the stored preference over MUI's localStorage seed once the session is
 * known. Renders nothing.
 *
 * The seed is what avoids a flash of the wrong palette on load; this is what
 * makes the preference follow the member to a browser that has no seed yet, and
 * what corrects a browser whose seed belongs to whoever signed in last.
 */
export function ColorSchemeSync() {
  const { data: user } = useCurrentUser()
  const { setMode } = useColorScheme()

  const { data } = useQuery({
    queryKey: preferencesKey,
    queryFn: fetchPreferences,
    enabled: Boolean(user),
    staleTime: 60_000,
  })

  const stored = data?.theme
  useEffect(() => {
    if (stored) {
      setMode(toMuiMode(stored))
    }
  }, [stored, setMode])

  return null
}

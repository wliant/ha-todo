import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useColorScheme } from '@mui/material/styles'
import { fetchPreferences, updatePreferences, type ThemePreference } from '../api/profile'

export const preferencesKey = ['me', 'preferences'] as const

/**
 * The member's colour scheme, with the server row as the truth.
 *
 * MUI keeps its own copy in localStorage, which is what paints the right
 * colours on the very first frame - before /api/me/preferences has resolved.
 * Without that mirror a dark-mode member sees a light flash on every reload,
 * which reads as the app being broken. So both are written, and the server wins
 * on load.
 */
export function useThemePreference() {
  const queryClient = useQueryClient()
  const { mode, setMode } = useColorScheme()

  const query = useQuery({
    queryKey: preferencesKey,
    queryFn: fetchPreferences,
    staleTime: 60_000,
  })

  const mutation = useMutation({
    mutationFn: (theme: ThemePreference) => updatePreferences(theme),
    // Optimistic, because the control this drives is a radio: a member tapping
    // one expects it to select under their finger, not after a round trip.
    onMutate: (theme) => {
      const previous = queryClient.getQueryData<{ theme: ThemePreference }>(preferencesKey)
      queryClient.setQueryData(preferencesKey, { theme })
      return previous
    },
    onError: (_error, _theme, previous) => {
      if (previous) {
        queryClient.setQueryData(preferencesKey, previous)
      }
      // The colours already changed, so put them back too.
      setMode(toMuiMode(previous?.theme ?? 'SYSTEM'))
    },
    onSuccess: (preferences) => {
      queryClient.setQueryData(preferencesKey, preferences)
    },
  })

  return {
    /** Undefined until the stored preference is known. */
    stored: query.data?.theme,
    /** What MUI is actually painting, including its localStorage seed. */
    applied: mode,
    isPending: mutation.isPending,
    error: mutation.error,
    choose(theme: ThemePreference) {
      // Apply locally first so the change is instant and survives a reload even
      // if the request is slow or fails.
      setMode(toMuiMode(theme))
      mutation.mutate(theme)
    },
  }
}

export function toMuiMode(theme: ThemePreference): 'light' | 'dark' | 'system' {
  return theme === 'LIGHT' ? 'light' : theme === 'DARK' ? 'dark' : 'system'
}

export function fromMuiMode(mode: string | undefined): ThemePreference {
  return mode === 'light' ? 'LIGHT' : mode === 'dark' ? 'DARK' : 'SYSTEM'
}

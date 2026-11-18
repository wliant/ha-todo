import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useColorScheme } from '@mui/material/styles'
import {
  fetchPreferences,
  updatePreferences,
  type Preferences,
  type ThemePreference,
} from '../api/profile'
import { listKeys } from '../lists/queries'
import { collectionKeys } from '../data/queries'
import { trackerKeys } from '../trackers/queries'

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
      const previous = queryClient.getQueryData<Preferences>(preferencesKey)
      // Merged, never replaced. This row carries more than the theme now, and
      // writing `{ theme }` over it leaves every other preference undefined
      // until the server answers - which reads as the sidebar briefly
      // forgetting how many lists to show.
      queryClient.setQueryData<Preferences>(preferencesKey, (current) =>
        current ? { ...current, theme } : current,
      )
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


/**
 * How many rows each sidebar group shows (0012 R102).
 *
 * <p>Its own hook beside the theme rather than a second return value on it:
 * they share a cache entry and an endpoint, and nothing else. The theme is
 * resent with every patch because the server requires it; the size is omitted
 * from a theme change for the same reason, so neither control can clobber the
 * other.
 */
export function useSidebarListCount() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: preferencesKey,
    queryFn: fetchPreferences,
    staleTime: 60_000,
  })

  const mutation = useMutation({
    mutationFn: (count: number) =>
      updatePreferences(query.data?.theme ?? 'SYSTEM', count),
    onSuccess: (preferences) => {
      queryClient.setQueryData(preferencesKey, preferences)
      // The server applies the cap, so the groups have to be refetched rather
      // than re-sliced on the client. All three keys, by their own
      // definitions: the sidebar trees are siblings and no prefix reaches
      // another, so a kind missing from this list keeps its old cap until the
      // page is reloaded.
      void queryClient.invalidateQueries({ queryKey: listKeys.sidebar })
      void queryClient.invalidateQueries({ queryKey: collectionKeys.sidebar })
      void queryClient.invalidateQueries({ queryKey: trackerKeys.sidebar })
    },
  })

  return {
    /** Undefined until the stored preference is known. */
    count: query.data?.sidebarListCount,
    isPending: mutation.isPending,
    error: mutation.error,
    choose: (count: number) => mutation.mutate(count),
  }
}

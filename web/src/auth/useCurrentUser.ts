import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchCurrentUser,
  fetchLoginUsers,
  login,
  logout,
  type CurrentUser,
  type LoginUser,
} from '../api/client'

export const currentUserKey = ['currentUser'] as const
export const loginUsersKey = ['loginUsers'] as const

/** `null` means "definitely signed out"; `undefined` means "not known yet". */
export function useCurrentUser() {
  return useQuery<CurrentUser | null>({
    queryKey: currentUserKey,
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: 30_000,
  })
}

/**
 * The roster the sign-in screen is drawn from. Not cached for long: the admin
 * may have just added or removed someone.
 */
export function useLoginUsers() {
  return useQuery<LoginUser[]>({
    queryKey: loginUsersKey,
    queryFn: fetchLoginUsers,
    retry: false,
    staleTime: 0,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password?: string }) =>
      login(username, password),
    onSuccess: (user) => {
      queryClient.setQueryData(currentUserKey, user)
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(currentUserKey, null)
      // Anything cached was scoped to the member who just left.
      queryClient.clear()
    },
  })
}

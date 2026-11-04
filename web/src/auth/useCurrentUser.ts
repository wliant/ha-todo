import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCurrentUser, login, logout, type CurrentUser } from '../api/client'

export const currentUserKey = ['currentUser'] as const

/** `null` means "definitely signed out"; `undefined` means "not known yet". */
export function useCurrentUser() {
  return useQuery<CurrentUser | null>({
    queryKey: currentUserKey,
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: 30_000,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      login(email, password),
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

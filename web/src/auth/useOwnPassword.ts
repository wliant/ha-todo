import { useMutation, useQueryClient } from '@tanstack/react-query'
import { setOwnPassword } from '../api/profile'
import { loginUsersKey } from './useCurrentUser'

/**
 * Sets or clears the caller's own password. Null clears the requirement, which
 * is what puts them back to one-tap sign-in.
 *
 * Invalidates the roster because whether a password is needed changes what the
 * sign-in screen does next - though it never says so out loud (adr/0009).
 */
export function useOwnPassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (password: string | null) => setOwnPassword(password),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: loginUsersKey })
    },
  })
}

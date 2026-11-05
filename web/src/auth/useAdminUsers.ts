import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createUser,
  deleteUser,
  fetchAdminUsers,
  setUserPassword,
  type AdminUser,
  type NewUser,
} from '../api/admin'
import { loginUsersKey } from './useCurrentUser'

export const adminUsersKey = ['adminUsers'] as const

export function useAdminUsers() {
  return useQuery<AdminUser[]>({
    queryKey: adminUsersKey,
    queryFn: fetchAdminUsers,
    retry: false,
  })
}

/**
 * Both mutations invalidate the sign-in roster as well as the admin list, so
 * the login screen can never offer a user the admin just removed.
 */
function useRosterInvalidation() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: adminUsersKey })
    void queryClient.invalidateQueries({ queryKey: loginUsersKey })
  }
}

export function useCreateUser() {
  const invalidate = useRosterInvalidation()
  return useMutation({
    mutationFn: (user: NewUser) => createUser(user),
    onSuccess: invalidate,
  })
}

export function useDeleteUser() {
  const invalidate = useRosterInvalidation()
  return useMutation({
    mutationFn: (id: string) => deleteUser(id),
    onSuccess: invalidate,
  })
}

/**
 * The reset path: a member can set their own password and lose it, so the admin
 * clears it. Invalidates the roster too because whether a password is required
 * changes what the sign-in screen does next.
 */
export function useSetUserPassword() {
  const invalidate = useRosterInvalidation()
  return useMutation({
    mutationFn: ({ id, password }: { id: string; password: string | null }) =>
      setUserPassword(id, password),
    onSuccess: invalidate,
  })
}

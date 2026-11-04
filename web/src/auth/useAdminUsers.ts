import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createUser, deleteUser, fetchAdminUsers, type AdminUser, type NewUser } from '../api/client'
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

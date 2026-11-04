import { api, type UserRole } from './http'

/** The admin's view of a user. Admin-only, so it may describe them fully. */
export interface AdminUser {
  id: string
  username: string
  displayName: string
  role: UserRole
  passwordRequired: boolean
  enabled: boolean
  createdAt: string
}

export interface NewUser {
  username: string
  displayName: string
  /** Omitted or blank means this user signs in by being picked. */
  password?: string
}

export async function fetchAdminUsers(): Promise<AdminUser[]> {
  const { data } = await api.get<AdminUser[]>('/admin/users')
  return data
}

export async function createUser(user: NewUser): Promise<AdminUser> {
  const { data } = await api.post<AdminUser>('/admin/users', user)
  return data
}

export async function deleteUser(id: string): Promise<void> {
  await api.delete(`/admin/users/${id}`)
}

/**
 * The recovery path for a member who set a password and forgot it. Null clears
 * the requirement entirely, putting them back to one-tap sign-in.
 */
export async function setUserPassword(id: string, password: string | null): Promise<void> {
  await api.put(`/admin/users/${id}/password`, { password })
}

import axios from 'axios'

/**
 * Same-origin client. axios already echoes the XSRF-TOKEN cookie back in the
 * X-XSRF-TOKEN header by default, which is exactly what the backend's
 * CookieCsrfTokenRepository expects.
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

export type UserRole = 'ADMIN' | 'MEMBER'

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  role: UserRole
}

/** One entry on the pick-a-user sign-in screen. */
export interface LoginUser {
  id: string
  username: string
  displayName: string
  role: UserRole
  passwordRequired: boolean
}

export interface AdminUser extends LoginUser {
  enabled: boolean
  createdAt: string
}

export interface NewUser {
  username: string
  displayName: string
  /** Omitted or blank means this user signs in by being picked. */
  password?: string
}

/** Resolving the token server-side is what sets the XSRF-TOKEN cookie. */
export async function primeCsrfToken(): Promise<void> {
  await api.get('/auth/csrf')
}

export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    const { data } = await api.get<CurrentUser>('/me')
    return data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      return null
    }
    throw error
  }
}

/** The sign-in roster. Public by necessity - the screen is a list of people. */
export async function fetchLoginUsers(): Promise<LoginUser[]> {
  const { data } = await api.get<LoginUser[]>('/auth/users')
  return data
}

export async function login(username: string, password?: string): Promise<CurrentUser> {
  await primeCsrfToken()
  const { data } = await api.post<CurrentUser>('/auth/login', { username, password })
  return data
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout')
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

/** The server's own wording when it has some, so conflicts read sensibly. */
export function errorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: string } | undefined
    if (body?.error) {
      return body.error
    }
  }
  return fallback
}

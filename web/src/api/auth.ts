import axios from 'axios'
import { api, type UserRole } from './http'

export type { UserRole }

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  role: UserRole
}

/**
 * One entry on the pick-a-user sign-in screen. Says only that this person
 * exists: the roster is readable by anyone who can reach the deployment, so it
 * carries no role and no password expectation (docs/adr/0009).
 */
export interface LoginUser {
  id: string
  username: string
  displayName: string
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

/**
 * The sign-in roster. Public by necessity - the screen is a list of people -
 * and therefore says nothing about any of them beyond their name.
 */
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

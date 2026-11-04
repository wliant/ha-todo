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

export interface CurrentUser {
  id: string
  email: string
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

export async function login(email: string, password: string): Promise<CurrentUser> {
  await primeCsrfToken()
  const { data } = await api.post<CurrentUser>('/auth/login', { email, password })
  return data
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}

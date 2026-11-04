import axios from 'axios'

/**
 * Same-origin client. axios already echoes the XSRF-TOKEN cookie back in the
 * X-XSRF-TOKEN header by default, which is exactly what the backend's
 * CookieCsrfTokenRepository expects.
 *
 * Only relative /api paths, in every environment: nginx proxies them to the
 * backend, so cookies, SameSite and CSRF behave identically everywhere and CORS
 * is never needed.
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

export type UserRole = 'ADMIN' | 'MEMBER'

/** A refused sign-in: with no password sent, it means one is required. */
export function isUnauthorized(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401
}

/**
 * No such list - or none this caller may know about. The two are deliberately
 * the same answer, so this cannot distinguish them either (docs/adr/0010).
 */
export function isNotFound(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404
}

/** A stale write. The caller's view was out of date; the server's state wins. */
export function isConflict(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 409
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

/**
 * Where an image's bytes live. Relative, like everything else: the object store
 * is not reachable from a browser, and a presigned URL would outlive the grant
 * that justified it (docs/adr/0013).
 */
export function imageUrl(id: string | null | undefined): string | undefined {
  return id ? `/api/images/${id}` : undefined
}

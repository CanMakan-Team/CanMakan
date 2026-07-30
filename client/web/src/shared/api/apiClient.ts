import { ApiError } from './apiErrors'
import type { AuthenticatedSession } from './types'

export const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL?.trim() || 'http://localhost:8080'
export const useMockApi =
  (import.meta.env.VITE_USE_MOCK_API ?? 'true').toLowerCase() !== 'false'

const sessionKey = 'canmakan.session'

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const storedSession = localStorage.getItem(sessionKey)
  const session = storedSession
    ? (JSON.parse(storedSession) as AuthenticatedSession)
    : null
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  if (session?.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  try {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...options,
      headers,
    })
    if (response.status === 401) {
      localStorage.removeItem(sessionKey)
      window.dispatchEvent(new Event('canmakan:unauthorised'))
      throw new ApiError('Your session has expired. Please sign in again.', 401)
    }
    if (response.status === 403) {
      window.location.assign('/access-denied')
      throw new ApiError('You do not have permission to perform this action.', 403)
    }
    if (!response.ok) {
      throw new ApiError('The service could not complete the request.', response.status)
    }
    return response.status === 204
      ? (undefined as T)
      : ((await response.json()) as T)
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(
      'The service is currently unreachable. Your page has been kept open.',
    )
  }
}

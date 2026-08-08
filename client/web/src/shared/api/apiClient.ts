import { ApiError } from './apiErrors'
import type { AuthenticatedSession } from './types'

export const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL?.trim() || 'http://localhost:8080'
export const useMockApi =
  (import.meta.env.VITE_USE_MOCK_API ?? 'false').toLowerCase() === 'true'

const sessionKey = 'canmakan.session'

/** API request
 * 
 * @param path: API path
 * @param options: Request options
 * @returns Promise of response body
 * @throws ApiError if the response is not ok
 * 
 * @author Amelia
 */
export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {

  // Get stored session
  // Stored session is a JSON string in localStorage
  // Parse the JSON string into an AuthenticatedSession object
  const storedSession = localStorage.getItem(sessionKey)
  const session = storedSession
    ? (JSON.parse(storedSession) as AuthenticatedSession)
    : null

  // Set the headers for the request:
  // - Accept: application/json
  // - Content-Type: application/json
  // - Authorization: Bearer <access token>
  // - X-User-Id: <user id>
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  if (session?.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  // Pre-JWT identity for UC8 family APIs; drop when UC19 JWT is the sole source.
  if (session?.userId != null) {
    headers.set('X-User-Id', String(session.userId))
  }

  try {
    // Make the request
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...options,
      headers,
    })

    // If the response is 401, handle the credential challenge
    // 1. If the path is not a credential challenge, remove the session and dispatch an unauthorised event
    // 2. If the path is a credential challenge, throw an ApiError with the error message
    if (response.status === 401) {
      const isCredentialChallenge =
        path.startsWith('/api/auth/login') || path.startsWith('/api/auth/register')
      if (!isCredentialChallenge) {
        localStorage.removeItem(sessionKey)
        window.dispatchEvent(new Event('canmakan:unauthorised'))
      }
      throw new ApiError(
        (await readErrorMessage(response)) ||
          (isCredentialChallenge
            ? 'Invalid email or password.'
            : 'Your session has expired. Please sign in again.'),
        401,
      )
    }

    // If the response is 403, redirect to the access denied page
    // and throw an ApiError with the error message
    if (response.status === 403) {
      window.location.assign('/access-denied')
      throw new ApiError(
        (await readErrorMessage(response)) ||
          'You do not have permission to perform this action.',
        403,
      )
    }

    // If the response is not ok, throw an ApiError with the error message
    if (!response.ok) {
      throw new ApiError(
        (await readErrorMessage(response)) ||
          'The service could not complete the request.',
        response.status,
      )
    }

    // If the response is 204, return undefined
    // Otherwise, return the response body as a T
    return response.status === 204
      ? (undefined as T)
      : ((await response.json()) as T)
  } catch (error) {
    // If the error is an ApiError, throw it
    if (error instanceof ApiError) throw error
    // Otherwise, throw a new ApiError with the error message
    throw new ApiError(
      'The service is currently unreachable. Your page has been kept open.',
    )
  }
}

/** Read error message from response
 * 
 * @param response: Response object
 * @returns Promise of string or undefined
 */
async function readErrorMessage(response: Response): Promise<string | undefined> {
  try {
    const body = (await response.clone().json()) as { message?: string }
    return body.message?.trim() || undefined
  } catch {
    return undefined
  }
}

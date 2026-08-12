import { ApiError } from './apiErrors'

export const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL?.trim() || 'http://localhost:8080'
export const useMockApi =
  (import.meta.env.VITE_USE_MOCK_API ?? 'false').toLowerCase() === 'true'

export interface ApiRequestOptions extends RequestInit {
  authentication?: 'auto' | 'none'
  retryAuthentication?: boolean
  sessionMutation?: boolean
}

interface ApiAuthBridge {
  getAccessToken: () => string | null
  refreshAccessToken: () => Promise<string | null>
  invalidate: () => void
}

let authBridge: ApiAuthBridge | null = null
const safeRetryMethods = new Set(['GET', 'HEAD', 'OPTIONS'])
const sessionMutationHeader = 'X-CanMakan-Session-Request'

/** Installs the auth feature's single session coordinator into the shared client. */
export function configureApiAuthBridge(bridge: ApiAuthBridge) {
  authBridge = bridge
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const {
    authentication = 'auto',
    retryAuthentication = true,
    sessionMutation = false,
    ...requestInit
  } = options
  const accessToken =
    authentication === 'auto' ? authBridge?.getAccessToken() : null

  const response = await executeRequest(
    path,
    requestInit,
    accessToken,
    sessionMutation,
  )
  if (response.status === 401) {
    const credentialChallenge =
      path === '/api/auth/login' || path === '/api/auth/register'

    if (
      !credentialChallenge &&
      authentication === 'auto' &&
      retryAuthentication &&
      safeRetryMethods.has((requestInit.method ?? 'GET').toUpperCase()) &&
      accessToken &&
      authBridge
    ) {
      const currentToken = authBridge.getAccessToken()
      const replacementToken =
        currentToken && currentToken !== accessToken
          ? currentToken
          : await authBridge.refreshAccessToken()
      if (replacementToken) {
        return apiRequest<T>(path, {
          ...requestInit,
          authentication: 'auto',
          retryAuthentication: false,
          sessionMutation,
        })
      }
    }

    if (!credentialChallenge && authentication === 'auto') {
      authBridge?.invalidate()
    }
    throw new ApiError(
      (await readErrorMessage(response)) ||
        (credentialChallenge
          ? 'Invalid email or password.'
          : 'Your session has expired. Please sign in again.'),
      401,
    )
  }

  if (response.status === 403) {
    throw new ApiError(
      (await readErrorMessage(response)) ||
        'You do not have permission to perform this action.',
      403,
    )
  }

  if (!response.ok) {
    throw new ApiError(
      (await readErrorMessage(response)) ||
        'The service could not complete the request.',
      response.status,
    )
  }

  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T)
}

async function executeRequest(
  path: string,
  options: RequestInit,
  accessToken: string | null | undefined,
  sessionMutation: boolean,
): Promise<Response> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  headers.set('ngrok-skip-browser-warning', 'true')
  if (options.body) headers.set('Content-Type', 'application/json')
  if (sessionMutation) headers.set(sessionMutationHeader, '1')
  else headers.delete(sessionMutationHeader)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  else headers.delete('Authorization')

  try {
    return await fetch(`${apiBaseUrl}${path}`, {
      ...options,
      headers,
      credentials: 'include',
    })
  } catch {
    throw new ApiError(
      'The service is currently unreachable. Your page has been kept open.',
    )
  }
}

async function readErrorMessage(response: Response): Promise<string | undefined> {
  try {
    const body = (await response.clone().json()) as { message?: string }
    return body.message?.trim() || undefined
  } catch {
    return undefined
  }
}

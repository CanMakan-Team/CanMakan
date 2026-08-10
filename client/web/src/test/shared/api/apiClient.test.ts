import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../../shared/api/apiClient'
import { ApiError } from '../../../shared/api/apiErrors'
import { jsonResponse, SESSION_KEY } from '../../testUtils'

/** Test suite for apiRequest.
 * 
 * @author Amelia
 */

describe('apiRequest', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('sends Authorization Bearer from the persisted session', async () => {
    localStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        accessToken: 'stored-token',
        userId: 14,
        displayName: 'person',
        roles: ['ROLE_FAMILY_ADMIN'],
        portal: 'FAMILY',
        prototype: false,
      }),
    )
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { familyId: 1 }))

    await apiRequest('/api/families/me')

    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/families/me',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer stored-token')
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('ngrok-skip-browser-warning')).toBe('true')
  })

  it('preserves caller headers and sets the shared JSON content type for a request body', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { updated: true }))

    await apiRequest('/api/example', {
      method: 'POST',
      headers: { 'X-Request-Id': 'request-123' },
      body: JSON.stringify({ active: false }),
    })

    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-Request-Id')).toBe('request-123')
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('ngrok-skip-browser-warning')).toBe('true')
  })

  it('clears session and dispatches unauthorised on non-login 401', async () => {
    localStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken: 'stale' }))
    const unauthorised = vi.fn()
    window.addEventListener('canmakan:unauthorised', unauthorised)
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Authentication required.' }),
    )

    await expect(apiRequest('/api/families/me')).rejects.toMatchObject({
      message: 'Authentication required.',
      status: 401,
    } satisfies Partial<ApiError>)

    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
    expect(unauthorised).toHaveBeenCalledTimes(1)
    window.removeEventListener('canmakan:unauthorised', unauthorised)
  })

  it('keeps session on login 401 credential challenge', async () => {
    localStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken: 'keep' }))
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Invalid credentials or account unavailable.' }),
    )

    await expect(apiRequest('/api/auth/login', { method: 'POST' })).rejects.toMatchObject({
      status: 401,
    })

    expect(localStorage.getItem(SESSION_KEY)).not.toBeNull()
  })

  it('returns undefined for 204 responses', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(204))

    await expect(apiRequest('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('maps network failure to a reachable-page error', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(apiRequest('/api/families/me')).rejects.toThrow(
      'The service is currently unreachable. Your page has been kept open.',
    )
  })
})

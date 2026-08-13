import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  apiRequest,
  configureApiAuthBridge,
} from '../../../shared/api/apiClient'
import { ApiError } from '../../../shared/api/apiErrors'
import { jsonResponse, SESSION_KEY } from '../../testUtils'

describe('apiRequest', () => {
  let accessToken: string | null
  let refreshAccessToken: ReturnType<typeof vi.fn<() => Promise<string | null>>>
  let invalidate: ReturnType<typeof vi.fn<() => void>>

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    accessToken = null
    refreshAccessToken = vi.fn(async () => null)
    invalidate = vi.fn(() => {
      accessToken = null
    })
    configureApiAuthBridge({
      getAccessToken: () => accessToken,
      refreshAccessToken,
      invalidate,
    })
  })

  it('sends the in-memory access credential and includes refresh cookies', async () => {
    accessToken = 'memory-token'
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { familyId: 1 }))

    await apiRequest('/api/families/me')

    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/families/me',
      expect.objectContaining({
        credentials: 'include',
        headers: expect.any(Headers),
      }),
    )
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer memory-token')
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('ngrok-skip-browser-warning')).toBe('true')
  })

  it('does not trust a token left in localStorage', async () => {
    localStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken: 'stored-token' }))
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { ok: true }))

    await apiRequest('/api/example')

    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.has('Authorization')).toBe(false)
  })

  it('preserves caller headers and sets JSON content type for a body', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { updated: true }))

    await apiRequest('/api/example', {
      method: 'POST',
      headers: { 'X-Request-Id': 'request-123' },
      body: JSON.stringify({ active: false }),
    })

    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-Request-Id')).toBe('request-123')
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('refreshes once and retries a protected request after 401', async () => {
    accessToken = 'expired-token'
    refreshAccessToken.mockImplementation(async () => {
      accessToken = 'rotated-token'
      return accessToken
    })
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse(401, { message: 'Expired.' }))
      .mockResolvedValueOnce(jsonResponse(200, { familyId: 1 }))

    await expect(apiRequest('/api/families/me')).resolves.toEqual({ familyId: 1 })

    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledTimes(2)
    const retryHeaders = vi.mocked(fetch).mock.calls[1][1]?.headers as Headers
    expect(retryHeaders.get('Authorization')).toBe('Bearer rotated-token')
    expect(invalidate).not.toHaveBeenCalled()
  })

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'])(
    'never refreshes or replays a %s mutation after 401',
    async (method) => {
      accessToken = 'expired-token'
      vi.mocked(fetch).mockResolvedValue(
        jsonResponse(401, { message: 'Authentication required.' }),
      )

      await expect(
        apiRequest('/api/families/me/profiles/4', {
          method,
          body: method === 'DELETE' ? undefined : JSON.stringify({ active: false }),
        }),
      ).rejects.toMatchObject({ status: 401 })

      expect(fetch).toHaveBeenCalledTimes(1)
      expect(refreshAccessToken).not.toHaveBeenCalled()
      expect(invalidate).toHaveBeenCalledTimes(1)
    },
  )

  it('limits a safe request to one authentication recovery attempt', async () => {
    accessToken = 'expired-token'
    refreshAccessToken.mockImplementation(async () => {
      accessToken = 'rotated-token'
      return accessToken
    })
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Authentication required.' }),
    )

    await expect(apiRequest('/api/families/me')).rejects.toMatchObject({
      status: 401,
    })

    expect(fetch).toHaveBeenCalledTimes(2)
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
  })

  it('reuses an already-rotated token for a late 401 without refreshing again', async () => {
    accessToken = 'expired-token'
    vi.mocked(fetch).mockImplementationOnce(async () => {
      accessToken = 'already-rotated-token'
      return jsonResponse(401, { message: 'Expired.' })
    })
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, { familyId: 1 }))

    await expect(apiRequest('/api/families/me')).resolves.toEqual({ familyId: 1 })

    expect(refreshAccessToken).not.toHaveBeenCalled()
    const retryHeaders = vi.mocked(fetch).mock.calls[1][1]?.headers as Headers
    expect(retryHeaders.get('Authorization')).toBe(
      'Bearer already-rotated-token',
    )
  })

  it('invalidates the in-memory session when refresh cannot recover a 401', async () => {
    accessToken = 'expired-token'
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Authentication required.' }),
    )

    await expect(apiRequest('/api/families/me')).rejects.toMatchObject({
      status: 401,
    } satisfies Partial<ApiError>)
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(invalidate).toHaveBeenCalledTimes(1)
  })

  it('does not refresh or invalidate for a credential challenge', async () => {
    accessToken = 'existing-token'
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Invalid credentials or account unavailable.' }),
    )

    await expect(
      apiRequest('/api/auth/login', {
        method: 'POST',
        authentication: 'none',
      }),
    ).rejects.toMatchObject({ status: 401 })
    expect(refreshAccessToken).not.toHaveBeenCalled()
    expect(invalidate).not.toHaveBeenCalled()
  })

  it('returns undefined for 204 responses', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(204))
    await expect(
      apiRequest('/api/auth/logout', {
        method: 'POST',
        authentication: 'none',
      }),
    ).resolves.toBeUndefined()
  })

  it('maps network failure to a reachable-page error', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(apiRequest('/api/families/me')).rejects.toThrow(
      'The service is currently unreachable. Your page has been kept open.',
    )
  })
})

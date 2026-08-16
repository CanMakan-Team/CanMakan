import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authService } from '../../../features/auth/authService'
import { apiBaseUrl } from '../../../shared/api/apiClient'
import { jsonResponse } from '../../testUtils'

/** Test suite for authService.
 * 
 * @author Amelia
 */

describe('authService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('maps JWT USER login to the platform USER role without inferring family membership', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        accessToken: 'jwt-access',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: {
          userId: 14,
          email: 'person@example.com',
          role: 'USER',
          active: true,
        },
      }),
    )

    const session = await authService.loginWithCredentials({
      email: 'person@example.com',
      password: 'Password1!',
      portal: 'FAMILY',
    })

    expect(session).toEqual({
      accessToken: 'jwt-access',
      userId: 14,
      email: 'person@example.com',
      active: true,
      displayName: 'person',
      roles: ['ROLE_APP_USER'],
      portal: 'FAMILY',
      prototype: false,
    })
    expect(fetch).toHaveBeenCalledWith(
      `${apiBaseUrl}/api/auth/login`,
      expect.objectContaining({ method: 'POST' }),
    )
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-CanMakan-Session-Request')).toBe('1')
  })

  it('maps JWT ADMIN login to system portal role', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        accessToken: 'admin-jwt',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: {
          userId: 1,
          email: 'admin@example.com',
          role: 'ADMIN',
          active: true,
        },
      }),
    )

    const session = await authService.loginWithCredentials({
      email: 'admin@example.com',
      password: 'Password1!',
      portal: 'SYSTEM',
    })

    expect(session.roles).toEqual(['ROLE_SYSTEM_ADMIN'])
    expect(session.portal).toBe('SYSTEM')
  })

  it('posts registration body and returns safe account fields', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(201, {
        userId: 14,
        email: 'person@example.com',
        active: true,
      }),
    )

    const response = await authService.register({
      email: 'person@example.com',
      password: 'Password1!',
    })

    expect(response.userId).toBe(14)
    expect(response).toEqual({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    const init = vi.mocked(fetch).mock.calls[0][1]
    expect(JSON.parse(String(init?.body))).toEqual({
      email: 'person@example.com',
      password: 'Password1!',
    })
  })

  it('sends session intent on refresh and never enters recursive recovery', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Authentication required.' }),
    )

    await expect(authService.refreshSession()).rejects.toMatchObject({
      status: 401,
    })

    expect(fetch).toHaveBeenCalledTimes(1)
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-CanMakan-Session-Request')).toBe('1')
  })

  it('does not recursively refresh /me after an authentication failure', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(401, { message: 'Authentication required.' }),
    )

    await expect(authService.getCurrentUser()).rejects.toMatchObject({
      status: 401,
    })

    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('propagates logout API failures for the session coordinator to handle', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('offline'))

    await expect(authService.logout()).rejects.toThrow('currently unreachable')
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-CanMakan-Session-Request')).toBe('1')
  })

  it('deletes the signed-in account with bearer and session intent', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(204))

    await authService.deleteOwnAccount()

    expect(fetch).toHaveBeenCalledWith(
      `${apiBaseUrl}/api/auth/account`,
      expect.objectContaining({ method: 'DELETE' }),
    )
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('X-CanMakan-Session-Request')).toBe('1')
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authService } from '../../../features/auth/authService'
import { jsonResponse } from '../../testUtils'

/** Test suite for authService.
 * 
 * @author Amelia
 */

describe('authService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('maps JWT USER login to family portal roles and display name', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        accessToken: 'jwt-access',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { userId: 14, email: 'person@example.com', role: 'USER' },
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
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/auth/login',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('maps JWT ADMIN login to system portal role', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        accessToken: 'admin-jwt',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { userId: 1, email: 'admin@example.com', role: 'ADMIN' },
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

  it('swallows logout API failures so local clear remains authoritative', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('offline'))

    await expect(authService.logout()).resolves.toBeUndefined()
  })
})

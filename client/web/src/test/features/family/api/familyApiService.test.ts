import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  familyApiService,
  familyEndpoints,
} from '../../../../features/family/api/familyApiService'
import { configureApiAuthBridge } from '../../../../shared/api/apiClient'
import { jsonResponse } from '../../../testUtils'

/** Test suite for familyApiService.
 * 
 * @author Amelia
 */

describe('familyApiService live UC8 calls', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    configureApiAuthBridge({
      getAccessToken: () => 'jwt',
      refreshAccessToken: async () => null,
      invalidate: () => undefined,
    })
  })

  it('GETs /api/families/me with bearer token', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        familyId: 3,
        familyName: 'Wong Family',
        memberRole: 'PRIMARY_ADMIN',
        selfProfileId: 77,
        createdByUserId: 14,
      }),
    )

    const me = await familyApiService.getMyFamily()

    expect(me.familyId).toBe(3)
    expect(fetch).toHaveBeenCalledWith(
      `http://localhost:8080${familyEndpoints.me}`,
      expect.any(Object),
    )
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt')
  })

  it('POSTs create family with familyName body', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(201, {
        familyId: 9,
        familyName: 'Wong Family',
        memberRole: 'PRIMARY_ADMIN',
        selfProfileId: 77,
        createdByUserId: 14,
      }),
    )

    await familyApiService.createFamily('Wong Family')

    const init = vi.mocked(fetch).mock.calls[0][1]
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080${familyEndpoints.families}`,
    )
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ familyName: 'Wong Family' })
  })

  it('PATCHes profile active with bearer token when mock is off', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, { id: 88, profileName: 'Child', active: false }),
    )

    await familyApiService.setProfileActive(88, false)

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080${familyEndpoints.profiles}/88`,
    )
    const init = vi.mocked(fetch).mock.calls[0][1]
    expect(init?.method).toBe('PATCH')
    const headers = init?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer jwt')
    expect(JSON.parse(String(init?.body))).toEqual({ active: false })
  })
})

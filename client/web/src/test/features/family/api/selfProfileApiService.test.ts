import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  selfProfileApiService,
  selfProfileEndpoints,
} from '../../../../features/family/api/selfProfileApiService'
import {
  apiBaseUrl,
  configureApiAuthBridge,
} from '../../../../shared/api/apiClient'
import { jsonResponse } from '../../../testUtils'

/** Test suite for selfProfileApiService (UC1 self dietary profile lifecycle). */

describe('selfProfileApiService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    configureApiAuthBridge({
      getAccessToken: () => 'jwt',
      refreshAccessToken: async () => null,
      invalidate: () => undefined,
    })
  })

  it('GETs the restriction catalog', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, [
        { id: 1, code: 'HALAL', displayName: 'Halal', category: 'RELIGIOUS' },
      ]),
    )

    const catalog = await selfProfileApiService.getCatalog()

    expect(catalog).toEqual([
      { id: 1, code: 'HALAL', displayName: 'Halal', category: 'RELIGIOUS' },
    ])
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      `${apiBaseUrl}${selfProfileEndpoints.catalog}`,
    )
  })

  it('POSTs a new SELF profile with the profile name and restrictions', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(201, {
        profileId: 77,
        profileName: 'Person Name',
        relationship: 'SELF',
        active: true,
        restrictions: { 2: 'STRICT_AVOID' },
      }),
    )

    const response = await selfProfileApiService.createSelfProfile('Person Name', {
      2: 'STRICT_AVOID',
    })

    expect(response.profileId).toBe(77)
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      `${apiBaseUrl}${selfProfileEndpoints.create}`,
    )
    const init = vi.mocked(fetch).mock.calls[0][1]
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      profileName: 'Person Name',
      restrictions: { 2: 'STRICT_AVOID' },
    })
  })

  it('GETs the caller\'s existing SELF profile', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        profileId: 55,
        profileName: 'Existing Name',
        relationship: 'SELF',
        active: true,
        restrictions: { 2: 'STRICT_AVOID' },
      }),
    )

    const response = await selfProfileApiService.getSelfProfile()

    expect(response.profileId).toBe(55)
    expect(response.profileName).toBe('Existing Name')
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      `${apiBaseUrl}${selfProfileEndpoints.me}`,
    )
    const init = vi.mocked(fetch).mock.calls[0][1]
    expect(init?.method ?? 'GET').toBe('GET')
  })

  it('surfaces a 404 as an ApiError when no SELF profile exists yet', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(404, { message: 'No SELF profile exists for this account yet.' }),
    )

    await expect(selfProfileApiService.getSelfProfile()).rejects.toMatchObject({
      status: 404,
      message: 'No SELF profile exists for this account yet.',
    })
  })

  it('PUTs the updated profile name and restrictions to the existing SELF profile', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        profileId: 55,
        profileName: 'Updated Name',
        relationship: 'SELF',
        active: true,
        restrictions: { 3: 'INTOLERANCE' },
      }),
    )

    const response = await selfProfileApiService.updateSelfProfile('Updated Name', {
      3: 'INTOLERANCE',
    })

    expect(response.profileName).toBe('Updated Name')
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
      `${apiBaseUrl}${selfProfileEndpoints.me}`,
    )
    const init = vi.mocked(fetch).mock.calls[0][1]
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(String(init?.body))).toEqual({
      profileName: 'Updated Name',
      restrictions: { 3: 'INTOLERANCE' },
    })
  })
})

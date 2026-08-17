import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  adminEndpoints,
  adminService,
} from '../../../features/admin/adminService'
import { apiBaseUrl, configureApiAuthBridge } from '../../../shared/api/apiClient'
import { jsonResponse } from '../../testUtils'

const account = {
  userId: 21,
  email: 'user21@example.test',
  role: 'USER' as const,
  active: true,
  updatedAt: '2026-08-10T09:30:00',
}

const statusResponse = {
  ...account,
  active: false,
  updatedAt: '2026-08-10T09:35:00',
  changed: true,
}

function lastRequest() {
  const [url, init] = vi.mocked(fetch).mock.calls.at(-1) ?? []
  return { url: String(url), init }
}

describe('adminService UC13 live account calls', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    configureApiAuthBridge({
      getAccessToken: () => 'admin-access-token',
      refreshAccessToken: async () => null,
      invalidate: () => undefined,
    })
  })

  it('GETs /api/admin/users with no filters', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, [account]))

    await adminService.getUsers()

    expect(lastRequest().url).toBe(
      `${apiBaseUrl}${adminEndpoints.users}`,
    )
    expect(lastRequest().init?.method).toBeUndefined()
  })

  it('trims and safely encodes the email query', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers({ query: '  alice+admin@example.test  ' })

    expect(lastRequest().url).toBe(
      `${apiBaseUrl}${adminEndpoints.users}?query=alice%2Badmin%40example.test`,
    )
  })

  it('sends the USER role filter', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers({ role: 'USER' })

    expect(lastRequest().url.endsWith('/api/admin/users?role=USER')).toBe(true)
  })

  it('sends the ADMIN role filter', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers({ role: 'ADMIN' })

    expect(lastRequest().url.endsWith('/api/admin/users?role=ADMIN')).toBe(true)
  })

  it('sends active=true', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers({ active: true })

    expect(lastRequest().url.endsWith('/api/admin/users?active=true')).toBe(true)
  })

  it('preserves active=false instead of omitting it', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers({ active: false })

    expect(lastRequest().url.endsWith('/api/admin/users?active=false')).toBe(true)
  })

  it('combines query, role and status filters', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers({
      query: '  Ann Lee  ',
      role: 'ADMIN',
      active: false,
    })

    expect(
      lastRequest().url.endsWith(
        '/api/admin/users?query=Ann+Lee&role=ADMIN&active=false',
      ),
    ).toBe(true)
  })

  it('PATCHes the frozen account status endpoint', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, statusResponse))

    await adminService.updateAccountStatus(21, {
      active: false,
      reason: 'Repeated misuse',
    })

    expect(lastRequest().url).toBe(`${apiBaseUrl}/api/admin/users/21/status`)
    expect(lastRequest().init?.method).toBe('PATCH')
  })

  it('sends only active and reason in the status body', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, statusResponse))

    await adminService.updateAccountStatus(21, {
      active: false,
      reason: 'Repeated misuse',
    })

    expect(JSON.parse(String(lastRequest().init?.body))).toEqual({
      active: false,
      reason: 'Repeated misuse',
    })
  })

  it('does not send role, actor identity or X-User-Id', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, statusResponse))

    await adminService.updateAccountStatus(21, {
      active: false,
      reason: 'Repeated misuse',
    })

    const body = JSON.parse(String(lastRequest().init?.body)) as Record<string, unknown>
    const headers = lastRequest().init?.headers as Headers
    expect(body).not.toHaveProperty('role')
    expect(body).not.toHaveProperty('actorUserId')
    expect(headers.has('X-User-Id')).toBe(false)
  })

  it('has no admin audit-read behavior', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, []))

    await adminService.getUsers()

    expect(adminService).not.toHaveProperty('getAuditEntries')
    expect(vi.mocked(fetch).mock.calls.flat().join(' ')).not.toContain(
      '/api/admin/audit',
    )
  })
})

const scanFeedbackListResponse = {
  summary: { totalFeedback: 2, negativePercentage: 50, feedbackPerDay: 0.07, negativeFeedbackPerDay: 0.03 },
  items: [],
  pageInfo: { page: 0, pageSize: 30, totalItems: 2, totalPages: 1 },
}

describe('adminService UC20 scan feedback calls', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    configureApiAuthBridge({
      getAccessToken: () => 'admin-access-token',
      refreshAccessToken: async () => null,
      invalidate: () => undefined,
    })
  })

  it('GETs /api/admin/scan-feedback with no filters', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, scanFeedbackListResponse))

    await adminService.getScanFeedback()

    expect(lastRequest().url).toBe(`${apiBaseUrl}${adminEndpoints.scanFeedback}`)
  })

  it('trims the keyword and combines every filter into the query string', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, scanFeedbackListResponse))

    await adminService.getScanFeedback({
      keyword: '  biryani  ',
      restrictionCode: 'HALAL',
      periodDays: 14,
      isPositive: false,
      resolved: true,
      page: 1,
      pageSize: 30,
    })

    expect(lastRequest().url).toBe(
      `${apiBaseUrl}${adminEndpoints.scanFeedback}?keyword=biryani&restrictionCode=HALAL&periodDays=14&isPositive=false&resolved=true&page=1&pageSize=30`,
    )
  })

  it('omits a blank keyword instead of sending an empty parameter', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, scanFeedbackListResponse))

    await adminService.getScanFeedback({ keyword: '   ' })

    expect(lastRequest().url).toBe(`${apiBaseUrl}${adminEndpoints.scanFeedback}`)
  })

  it('sends page=0 explicitly rather than treating it as absent', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, scanFeedbackListResponse))

    await adminService.getScanFeedback({ page: 0, pageSize: 30 })

    expect(lastRequest().url).toBe(
      `${apiBaseUrl}${adminEndpoints.scanFeedback}?page=0&pageSize=30`,
    )
  })

  it('PATCHes the resolved endpoint with the new value', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { id: 5, resolved: true }))

    await adminService.updateScanFeedbackResolved(5, true)

    expect(lastRequest().url).toBe(`${apiBaseUrl}${adminEndpoints.scanFeedback}/5/resolved`)
    expect(lastRequest().init?.method).toBe('PATCH')
    expect(JSON.parse(String(lastRequest().init?.body))).toEqual({ resolved: true })
  })
})

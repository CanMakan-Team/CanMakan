import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SystemHealth } from '../../../features/admin/api/systemHealthApiService'

const apiClientModule = '../../../shared/api/apiClient'

afterEach(() => {
  vi.doUnmock(apiClientModule)
  vi.resetModules()
})

describe('systemHealthApiService transport selection', () => {
  it('returns the deterministic mock snapshot when mock mode is enabled', async () => {
    const apiRequest = vi.fn()
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: true }))

    const { systemHealthApiService } = await import('../../../features/admin/api/systemHealthApiService')
    const response = await systemHealthApiService.getSystemHealth(24)

    expect(apiRequest).not.toHaveBeenCalled()
    expect(response.overallStatus).toBe('UP')
    expect(response.components).toHaveLength(3)
    expect(response.ai.latencyTrend).toHaveLength(12)
    expect(response.ai.slowestCalls.length).toBeGreaterThan(0)
    expect(response.auditTrail.length).toBeGreaterThan(0)
  })

  it('calls the backend endpoint with the selected window when mock mode is disabled', async () => {
    const liveResponse: SystemHealth = {
      generatedAt: '2026-08-16T10:00:00Z',
      overallStatus: 'UP',
      components: [{ name: 'db', status: 'UP' }],
      ai: {
        tier3RatePct: 10,
        averageLatencyMs: 500,
        maxLatencyMs: 1800,
        totalCalls: 60,
        latencyTrend: Array.from({ length: 12 }, () => 500),
        slowestCalls: [{ scanId: 1, tier: 'TIER_3_LLM', latencyMs: 1800 }],
      },
      auditTrail: [
        { timestamp: '2026-08-16T09:00:00Z', admin: 'admin1@canmakan.com', action: 'SUSPEND', target: 'user 42', ipAddress: '203.0.113.9' },
      ],
      scanQuality: { incompleteDataPct: 6, safePct: 66, warningPct: 28, unsafePct: 6, totalScans: 320 },
    }
    const apiRequest = vi.fn().mockResolvedValue(liveResponse)
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: false }))

    const { systemHealthApiService } = await import('../../../features/admin/api/systemHealthApiService')
    const response = await systemHealthApiService.getSystemHealth(168)

    expect(apiRequest).toHaveBeenCalledWith('/api/admin/system-health?hours=168')
    expect(response).toBe(liveResponse)
  })
})

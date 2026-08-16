import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SystemHealthPage } from '../../../features/admin/SystemHealthPage'
import {
  systemHealthApiService,
  type SystemHealth,
} from '../../../features/admin/systemHealthApiService'

vi.mock('../../../features/admin/systemHealthApiService', () => ({
  systemHealthApiService: {
    getSystemHealth: vi.fn(),
  },
}))

const responseFixture: SystemHealth = {
  generatedAt: '2026-08-16T10:00:00Z',
  overallStatus: 'UP',
  components: [
    { name: 'db', status: 'UP' },
    { name: 'diskSpace', status: 'UP' },
    { name: 'application', status: 'UP' },
  ],
  ai: {
    tier3RatePct: 12,
    averageLatencyMs: 640,
    maxLatencyMs: 2108,
    totalCalls: 148,
    latencyTrend: Array.from({ length: 12 }, () => 600),
    slowestCalls: [{ scanId: 8421, tier: 'TIER_3_LLM', latencyMs: 2108 }],
  },
  auditTrail: [
    { timestamp: '2026-08-16T09:38:12Z', admin: 'admin1@canmakan.com', action: 'SUSPEND', target: 'user 42', ipAddress: '203.0.113.9' },
  ],
  scanQuality: { incompleteDataPct: 6, safePct: 66, warningPct: 28, unsafePct: 6, totalScans: 320 },
}

describe('SystemHealthPage', () => {
  beforeEach(() => {
    vi.mocked(systemHealthApiService.getSystemHealth).mockReset()
    vi.mocked(systemHealthApiService.getSystemHealth).mockResolvedValue(responseFixture)
  })

  it('loads the snapshot, renders the four sections, and refetches when the window changes', async () => {
    const user = userEvent.setup()
    render(<SystemHealthPage />)

    expect(screen.getByText('Loading system health…')).toBeInTheDocument()
    await screen.findByRole('heading', { name: 'Component health' })
    expect(screen.getByRole('heading', { name: 'AI execution monitoring' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Admin activity audit' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Scan data quality' })).toBeInTheDocument()

    expect(systemHealthApiService.getSystemHealth).toHaveBeenCalledWith(24)
    expect(screen.getByText('Status: UP')).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Reporting window' }), '168')
    await waitFor(() => expect(systemHealthApiService.getSystemHealth).toHaveBeenLastCalledWith(168))
  })

  it('shows an error and retries when the first request fails', async () => {
    const user = userEvent.setup()
    vi.mocked(systemHealthApiService.getSystemHealth)
      .mockRejectedValueOnce(new Error('Synthetic outage'))
      .mockResolvedValue(responseFixture)

    render(<SystemHealthPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The system health snapshot could not be loaded. Please try again.',
    )

    await user.click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByRole('heading', { name: 'Component health' })
  })
})

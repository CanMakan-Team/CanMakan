import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SystemHealthPage } from '../../../features/admin/pages/SystemHealthPage'
import {
  systemHealthApiService,
  type SystemHealth,
} from '../../../features/admin/api/systemHealthApiService'

vi.mock('../../../features/admin/api/systemHealthApiService', () => ({
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
    expect(screen.getByText('db')).toBeInTheDocument()
    expect(screen.getByText('diskSpace')).toBeInTheDocument()
    expect(screen.getByText('application')).toBeInTheDocument()
    expect(screen.getAllByText('UP').length).toBeGreaterThan(0)

    expect(screen.getByRole('columnheader', { name: 'Timestamp' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'User' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Action' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Target' })).toBeInTheDocument()
    expect(screen.getByRole('rowheader', { name: 'admin1@canmakan.com' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Admin activity audit' }).querySelector('.health-audit-action')).toHaveTextContent('SUSPEND')
    expect(screen.getByText(/user 42/)).toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByLabelText('Admin user')).toBeInTheDocument()
    expect(screen.getByLabelText('Event type')).toBeInTheDocument()

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

  it('filters the audit trail by admin user and event type', async () => {
    const user = userEvent.setup()
    vi.mocked(systemHealthApiService.getSystemHealth).mockResolvedValue({
      ...responseFixture,
      auditTrail: [
        { timestamp: '2026-08-16T09:38:12Z', admin: 'admin1@canmakan.com', action: 'SUSPEND', target: 'user 42', ipAddress: '203.0.113.9' },
        { timestamp: '2026-08-16T09:40:00Z', admin: 'admin2@canmakan.com', action: 'VIEW', target: 'consumer-trends export', ipAddress: '203.0.113.7' },
      ],
    })

    render(<SystemHealthPage />)
    await screen.findByRole('rowheader', { name: 'admin1@canmakan.com' })
    expect(screen.getByRole('rowheader', { name: 'admin2@canmakan.com' })).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Admin user'), 'admin1@canmakan.com')
    expect(screen.getByRole('rowheader', { name: 'admin1@canmakan.com' })).toBeInTheDocument()
    expect(screen.queryByRole('rowheader', { name: 'admin2@canmakan.com' })).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Admin user'), 'ALL')
    await user.selectOptions(screen.getByLabelText('Event type'), 'VIEW')
    expect(screen.getByRole('rowheader', { name: 'admin2@canmakan.com' })).toBeInTheDocument()
    expect(screen.queryByRole('rowheader', { name: 'admin1@canmakan.com' })).not.toBeInTheDocument()
  })

  it('shows a bounded empty state for the latency trend when there are no AI calls', async () => {
    vi.mocked(systemHealthApiService.getSystemHealth).mockResolvedValue({
      ...responseFixture,
      ai: {
        ...responseFixture.ai,
        totalCalls: 0,
        latencyTrend: [],
        slowestCalls: [],
      },
    })

    render(<SystemHealthPage />)
    await screen.findByRole('heading', { name: 'AI execution monitoring' })
    expect(screen.queryByRole('img', { name: 'AI call latency trend.' })).not.toBeInTheDocument()
    expect(screen.getAllByText('No AI calls in this window.').length).toBeGreaterThan(0)
  })

  it('labels the latency trend with compact x and y axes', async () => {
    render(<SystemHealthPage />)

    const chart = await screen.findByRole('img', { name: 'AI call latency trend.' })
    expect(chart).toHaveTextContent('600ms')
    expect(chart).toHaveTextContent('0ms')
    expect(chart).toHaveTextContent('15 Aug')
    expect(chart).toHaveTextContent('16 Aug')
    expect(chart.textContent).not.toContain('Start')
    expect(chart.textContent).not.toContain('Now')
  })

  it('explains component UP badges and AI metric cards on hover', async () => {
    const user = userEvent.setup()
    render(<SystemHealthPage />)

    await screen.findByRole('heading', { name: 'Component health' })
    const dbCard = screen.getByText('db').closest('.health-component-card')
    expect(dbCard).not.toBeNull()
    await user.hover(dbCard!.querySelector('.status-badge') as HTMLElement)
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'UP means the database accepted a connectivity check and is reachable.',
    )
    await user.unhover(dbCard!.querySelector('.status-badge') as HTMLElement)

    await user.hover(screen.getByText('Avg latency'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Average time to complete an AI call in this reporting window.',
    )
  })
})

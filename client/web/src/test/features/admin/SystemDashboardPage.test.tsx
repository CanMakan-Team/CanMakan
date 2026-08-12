import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { SystemDashboardPage } from '../../../features/admin/SystemDashboardPage'
import { adminService } from '../../../features/admin/adminService'
import { consumerTrendsApiService } from '../../../features/analytics/consumerTrendsApiService'

vi.mock('../../../features/admin/adminService', () => ({
  adminService: {
    getUsers: vi.fn(),
  },
}))

vi.mock('../../../features/analytics/consumerTrendsApiService', () => ({
  consumerTrendsApiService: {
    getConsumerTrends: vi.fn(),
  },
}))

describe('SystemDashboardPage UC13 account summary', () => {
  beforeEach(() => {
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockReset()
    vi.mocked(adminService.getUsers).mockReset()
  })

  it('uses the current UC7 summary and counts suspended accounts', async () => {
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockResolvedValue({
      period: {
        from: '2026-08-01',
        to: '2026-08-10',
        timezone: 'Asia/Singapore',
      },
      summary: {
        totalScans: 17,
        safeCount: 10,
        warningCount: 5,
        unsafeCount: 2,
      },
      dailyTrend: [],
      topFlaggedIngredients: [],
      dataQuality: {
        partial: false,
        skippedMalformedFindings: 0,
      },
      generatedAt: '2026-08-10T09:30:00Z',
    })
    vi.mocked(adminService.getUsers).mockResolvedValue([
      {
        userId: 21,
        email: 'active@example.test',
        role: 'USER',
        active: true,
        updatedAt: '2026-08-10T09:30:00',
      },
      {
        userId: 22,
        email: 'suspended@example.test',
        role: 'USER',
        active: false,
        updatedAt: '2026-08-10T09:30:00',
      },
      {
        userId: 23,
        email: 'admin@example.test',
        role: 'ADMIN',
        active: false,
        updatedAt: '2026-08-10T09:30:00',
      },
    ])

    render(
      <MemoryRouter>
        <SystemDashboardPage />
      </MemoryRouter>,
    )

    const aggregateLabel = await screen.findByText('Aggregate assessments')
    expect(aggregateLabel.parentElement).toHaveTextContent('17')
    const label = await screen.findByText('Suspended accounts')
    expect(label.parentElement).toHaveTextContent('2')
    expect(screen.queryByText('Pending access')).not.toBeInTheDocument()
  })
})

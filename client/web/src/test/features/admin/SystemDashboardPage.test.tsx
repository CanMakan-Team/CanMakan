import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { SystemDashboardPage } from '../../../features/admin/SystemDashboardPage'
import { adminService } from '../../../features/admin/adminService'
import { consumerTrendsApiService } from '../../../features/analytics/consumerTrendsApiService'
import { systemHealthApiService } from '../../../features/admin/systemHealthApiService'
import { usageStatisticsApiService } from '../../../features/analytics/usageStatisticsApiService'

vi.mock('../../../features/admin/adminService', () => ({
  adminService: {
    getUsers: vi.fn(),
    getScanFeedback: vi.fn(),
  },
}))

vi.mock('../../../features/analytics/consumerTrendsApiService', () => ({
  consumerTrendsApiService: {
    getConsumerTrends: vi.fn(),
  },
}))

vi.mock('../../../features/admin/systemHealthApiService', () => ({
  systemHealthApiService: {
    getSystemHealth: vi.fn(),
  },
}))

vi.mock('../../../features/analytics/usageStatisticsApiService', () => ({
  usageStatisticsApiService: {
    getUsageStatistics: vi.fn(),
  },
}))

describe('SystemDashboardPage', () => {
  beforeEach(() => {
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockReset()
    vi.mocked(adminService.getUsers).mockReset()
    vi.mocked(adminService.getScanFeedback).mockReset()
    vi.mocked(systemHealthApiService.getSystemHealth).mockReset()
    vi.mocked(usageStatisticsApiService.getUsageStatistics).mockReset()
  })

  it('shows exception queues, health, and usage shortcuts', async () => {
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
        uniqueProducts: 8,
        averageScansPerDay: 1.7,
        peakScanDay: { date: '2026-08-09', scanCount: 4 },
      },
      appliedFilters: { category: null },
      dailyTrend: [
        {
          date: '2026-08-08',
          totalCount: 3,
          safeCount: 2,
          warningCount: 1,
          unsafeCount: 0,
        },
        {
          date: '2026-08-09',
          totalCount: 4,
          safeCount: 2,
          warningCount: 1,
          unsafeCount: 1,
        },
        {
          date: '2026-08-10',
          totalCount: 10,
          safeCount: 6,
          warningCount: 3,
          unsafeCount: 1,
        },
      ],
      mostScannedProducts: [],
      categoryOverview: [],
      topRestrictions: [],
      topFlaggedIngredients: [
        { ingredientName: 'Peanut', flaggedCount: 6 },
      ],
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
    vi.mocked(adminService.getScanFeedback).mockResolvedValue({
      summary: {
        totalFeedback: 4,
        negativePercentage: 50,
        feedbackPerDay: 0.13,
        negativeFeedbackPerDay: 0.07,
      },
      items: [],
      pageInfo: {
        page: 0,
        pageSize: 1,
        totalItems: 4,
        totalPages: 4,
      },
    })
    vi.mocked(systemHealthApiService.getSystemHealth).mockResolvedValue({
      generatedAt: '2026-08-16T10:00:00Z',
      overallStatus: 'UP',
      components: [{ name: 'db', status: 'UP' }],
      ai: {
        tier3RatePct: 12,
        averageLatencyMs: 640,
        maxLatencyMs: 2108,
        totalCalls: 148,
        latencyTrend: [],
        slowestCalls: [],
      },
      auditTrail: [],
      scanQuality: {
        incompleteDataPct: 6,
        safePct: 66,
        warningPct: 28,
        unsafePct: 6,
        totalScans: 320,
      },
    })

    vi.mocked(usageStatisticsApiService.getUsageStatistics).mockResolvedValue({
      periodDays: 7,
      generatedAt: '2026-08-16T10:00:00Z',
      kpis: {
        newSignups: 42,
        dailyActiveUsers: 124,
        stickinessPct: 38,
        averageSessionSeconds: 252,
      },
      acquisition: {
        dailyNewRegistrations: [],
        activationFunnel: [],
      },
      activity: {
        dailyActiveUsers: 124,
        weeklyActiveUsers: 311,
        monthlyActiveUsers: 920,
        stickinessPct: 38,
        newUsersPct: 37,
        returningUsersPct: 63,
      },
      retention: {
        day1Pct: 54,
        day7Pct: 31,
        day30Pct: 19,
        resurrectedUsers: 7,
        churnPct: 9,
        inactive30d: 42,
        totalUsers: 500,
      },
      engagement: {
        averageSessionSeconds: 252,
        sessionsPerUser: 2.7,
        activeDaysPerWeek: 3.4,
        heatmap: [],
      },
    })

    render(
      <MemoryRouter>
        <SystemDashboardPage />
      </MemoryRouter>,
    )

    const statusLabel = await screen.findByText('System status')
    expect(statusLabel.parentElement).toHaveTextContent('UP')
    expect(statusLabel.closest('a')).toHaveAttribute('href', '/system/health')

    const dauLabel = screen.getByText('Daily active users')
    expect(dauLabel.parentElement).toHaveTextContent('124')
    expect(dauLabel.closest('a')).toHaveAttribute('href', '/system/usage')
    expect(usageStatisticsApiService.getUsageStatistics).toHaveBeenCalledWith(7)

    const suspendedLabel = screen.getByText('Suspended accounts')
    expect(suspendedLabel.parentElement).toHaveTextContent('2')
    expect(suspendedLabel.closest('a')).toHaveAttribute(
      'href',
      '/system/users?status=SUSPENDED',
    )
    const totalLabel = screen.getByText('Total accounts')
    expect(totalLabel.parentElement).toHaveTextContent('3')

    const feedbackLabel = screen.getByText('Open scan feedback')
    expect(feedbackLabel.parentElement).toHaveTextContent('4')
    expect(feedbackLabel.closest('a')).toHaveAttribute(
      'href',
      '/system/feedback?resolved=UNRESOLVED',
    )
    expect(systemHealthApiService.getSystemHealth).toHaveBeenCalledWith(24)
    expect(screen.getByText(/Top flagged ingredient/i)).toHaveTextContent('Peanut')
    expect(screen.getByText('USER')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.queryByText('admin@example.test')).not.toBeInTheDocument()
    expect(screen.queryByText('Incomplete scan data')).not.toBeInTheDocument()
  })
})

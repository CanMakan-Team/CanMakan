import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { UsageStatisticsPage } from '../../../features/analytics/UsageStatisticsPage'
import {
  usageStatisticsApiService,
  type UsageStatistics,
} from '../../../features/analytics/usageStatisticsApiService'

vi.mock('../../../features/analytics/usageStatisticsApiService', () => ({
  usageStatisticsApiService: {
    getUsageStatistics: vi.fn(),
  },
}))

const responseFixture: UsageStatistics = {
  periodDays: 7,
  generatedAt: '2026-08-16T10:00:00Z',
  kpis: {
    newSignups: 42,
    dailyActiveUsers: 124,
    stickinessPct: 38,
    averageSessionSeconds: 252,
  },
  acquisition: {
    dailyNewRegistrations: [8, 9, 7, 6, 10, 11, 12],
    activationFunnel: [
      { label: 'Registered', percent: 100 },
      { label: 'Profile set up', percent: 82 },
      { label: 'First scan', percent: 64 },
      { label: 'Repeat scan', percent: 41 },
    ],
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
    heatmap: Array.from({ length: 7 }, () => Array.from({ length: 12 }, () => 0.5)),
  },
}

describe('UsageStatisticsPage', () => {
  beforeEach(() => {
    vi.mocked(usageStatisticsApiService.getUsageStatistics).mockReset()
    vi.mocked(usageStatisticsApiService.getUsageStatistics).mockResolvedValue(responseFixture)
  })

  it('loads usage statistics, renders analytics sections, and refetches when period changes', async () => {
    const user = userEvent.setup()
    render(<UsageStatisticsPage />)

    expect(screen.getByText('Loading usage statistics…')).toBeInTheDocument()
    await screen.findByRole('heading', { name: 'Acquisition & conversion' })
    expect(screen.getByRole('heading', { name: 'Activity & stickiness' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Retention & churn' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Engagement & sessions' })).toBeInTheDocument()

    expect(usageStatisticsApiService.getUsageStatistics).toHaveBeenCalledWith(7)
    expect(screen.getByText('New sign-ups')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /Daily new registrations over 7 days/ })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'New 37%, returning 63%' })).toBeInTheDocument()
    await user.hover(screen.getByText('New sign-ups'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('New accounts created during the selected reporting period.')
    await user.hover(screen.getByText('Stickiness (DAU/MAU)'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(/DAU ÷ MAU/)
    await user.hover(screen.getByText('D1 retention'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('scanned again at least one day after signing up')
    await user.hover(screen.getByText('Sessions / user'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Average number of sessions per active user')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Reporting period' }), '30')
    await waitFor(() => expect(usageStatisticsApiService.getUsageStatistics).toHaveBeenLastCalledWith(30))
  })

  it('exports csv when data is loaded and retries when the first request fails', async () => {
    const user = userEvent.setup()
    const firstError = new Error('Synthetic outage')
    vi.mocked(usageStatisticsApiService.getUsageStatistics)
      .mockRejectedValueOnce(firstError)
      .mockResolvedValue(responseFixture)

    const createObjectURL = vi.fn().mockReturnValue('blob:usage-stats')
    const revokeObjectURL = vi.fn()
    const previousCreateObjectURL = URL.createObjectURL
    const previousRevokeObjectURL = URL.revokeObjectURL
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: createObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: revokeObjectURL })
    const linkClick = vi.fn()
    const originalCreateElement = document.createElement.bind(document)
    const createElementSpy = vi.spyOn(document, 'createElement')
      .mockImplementation((tagName: string) => {
        if (tagName === 'a') {
          return {
            click: linkClick,
            set href(value: string) { void value },
            set download(value: string) { void value },
          } as unknown as HTMLAnchorElement
        }
        return originalCreateElement(tagName)
      })

    render(<UsageStatisticsPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The usage statistics could not be loaded. Please try again.',
    )

    await user.click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByRole('heading', { name: 'Acquisition & conversion' })

    await user.click(screen.getByRole('button', { name: 'Export CSV' }))
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(linkClick).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:usage-stats')

    createElementSpy.mockRestore()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: previousCreateObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: previousRevokeObjectURL })
  })
})

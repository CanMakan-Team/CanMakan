import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsumerTrendsPage } from '../../../features/analytics/ConsumerTrendsPage'
import { consumerTrendsApiService } from '../../../features/analytics/consumerTrendsApiService'
import { buildPeriodQuery } from '../../../features/analytics/consumerTrendsDateRange'
import { downloadConsumerTrendsReport } from '../../../features/analytics/consumerTrendsReport'
import type {
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
} from '../../../features/analytics/consumerTrendsTypes'

vi.mock('../../../features/analytics/consumerTrendsApiService', () => ({
  consumerTrendsApiService: {
    getConsumerTrends: vi.fn(),
  },
}))

vi.mock('../../../features/analytics/consumerTrendsReport', () => ({
  downloadConsumerTrendsReport: vi.fn(),
}))

const dailyTrend = Array.from({ length: 7 }, (_, index) => ({
  date: `2026-08-${String(index + 1).padStart(2, '0')}`,
  totalCount: index === 2 ? 0 : index + 2,
  safeCount: index === 2 ? 0 : index + 1,
  warningCount: index === 2 ? 0 : 1,
  unsafeCount: 0,
}))

const populatedResponse: ConsumerTrendsResponse = {
  period: { from: '2026-08-01', to: '2026-08-07', timezone: 'Asia/Singapore' },
  appliedFilters: { category: null },
  summary: {
    totalScans: 30,
    safeCount: 24,
    warningCount: 6,
    unsafeCount: 0,
    uniqueProducts: 7,
    averageScansPerDay: 4.29,
    peakScanDay: { date: '2026-08-07', scanCount: 8 },
  },
  dailyTrend,
  mostScannedProducts: [
    { rank: 1, productName: 'Product one', scanCount: 10, percentage: 33.33 },
    { rank: 2, productName: 'Product two', scanCount: 9, percentage: 30 },
    { rank: 3, productName: 'Product three', scanCount: 8, percentage: 26.67 },
    { rank: 4, productName: 'Product four', scanCount: 7, percentage: 23.33 },
    { rank: 5, productName: 'Product five', scanCount: 6, percentage: 20 },
    { rank: 6, productName: 'Product six with a deliberately long accessible name', scanCount: 5, percentage: 16.67 },
    { rank: 7, productName: 'Product seven', scanCount: 4, percentage: 13.33 },
  ],
  categoryOverview: [
    { category: 'Snacks', scanCount: 18, percentage: 60 },
    { category: 'Uncategorised', scanCount: 12, percentage: 40 },
  ],
  topRestrictions: [
    { restrictionCode: 'PEANUT_ALLERGY', flaggedCount: 5 },
  ],
  topFlaggedIngredients: [
    { ingredientName: 'Peanut', flaggedCount: 5 },
  ],
  dataQuality: { partial: false, skippedMalformedFindings: 0 },
  generatedAt: '2026-08-07T12:00:00+08:00',
}

function daysInQuery(query: ConsumerTrendsQuery): number {
  const from = Date.parse(`${query.from}T00:00:00Z`)
  const to = Date.parse(`${query.to}T00:00:00Z`)
  return Math.round((to - from) / 86_400_000) + 1
}

describe('ConsumerTrendsPage', () => {
  beforeEach(() => {
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockReset()
    vi.mocked(downloadConsumerTrendsReport).mockReset()
    vi.mocked(downloadConsumerTrendsReport).mockResolvedValue()
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockImplementation(
      async (query = {}) => ({
        ...populatedResponse,
        period: {
          ...populatedResponse.period,
          from: query.from ?? populatedResponse.period.from,
          to: query.to ?? populatedResponse.period.to,
        },
        appliedFilters: { category: query.category ?? null },
      }),
    )
  })

  it('builds inclusive Singapore calendar ranges independently of the UTC day', () => {
    expect(buildPeriodQuery(30, undefined, new Date('2026-06-18T10:00:00Z'))).toEqual({
      from: '2026-05-20',
      to: '2026-06-18',
      category: undefined,
    })

    const singaporeBoundary = new Date('2026-02-09T16:01:00Z')
    expect(buildPeriodQuery(7, 'Snacks', singaporeBoundary)).toEqual({
      from: '2026-02-04',
      to: '2026-02-10',
      category: 'Snacks',
    })
    expect(buildPeriodQuery(30, undefined, singaporeBoundary)).toMatchObject({
      from: '2026-01-12',
      to: '2026-02-10',
    })
    expect(buildPeriodQuery(90, undefined, singaporeBoundary)).toMatchObject({
      from: '2025-11-13',
      to: '2026-02-10',
    })
  })

  it('loads 30 days initially and renders the complete accessible UC7 dashboard', async () => {
    render(<ConsumerTrendsPage />)

    await screen.findByRole('heading', { name: 'Daily Scan Activity' })
    expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenCalledTimes(1)
    const initialQuery = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls[0][0]
    expect(daysInQuery(initialQuery ?? {})).toBe(30)

    expect(screen.getByText(
      'Aggregated scan activity and dietary-concern insights. Scan activity indicates consumer interest, not actual sales.',
    )).toBeInTheDocument()
    expect(screen.getByText('Total Scans')).toBeInTheDocument()
    expect(screen.getByText('Unique Products Scanned')).toBeInTheDocument()
    expect(screen.getByText('Average Scans per Day')).toBeInTheDocument()
    expect(screen.getByText('Peak Scan Day')).toBeInTheDocument()
    expect(screen.getByText('4.29')).toBeInTheDocument()
    expect(screen.getByText('8 scans')).toBeInTheDocument()

    expect(screen.getByRole('img', { name: /Line chart of total scans/ })).toBeInTheDocument()
    expect(document.querySelectorAll('.chart-point')).toHaveLength(7)
    expect(document.querySelector('.chart-point')?.getAttribute('aria-label')).toContain(
      '2 total scans, 1 safe, 1 warning, 0 unsafe',
    )
    expect(screen.getByText('View daily values')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Most Scanned Products' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Scan Activity by Category' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Most Frequently Triggered Dietary Restrictions' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Top Flagged Ingredients' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Scan Verdict Mix' })).toBeInTheDocument()
    expect(screen.getByText('PEANUT_ALLERGY')).toBeInTheDocument()
    expect(screen.getByText('Counts show scan-triggered dietary-concern signals, not population prevalence.')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: '80% safe, 20% warning, 0% unsafe' })).toBeInTheDocument()
    const outcomeTable = screen.getByRole('table', { name: 'Exact scan verdict counts and percentages' })
    expect(within(outcomeTable).getByRole('rowheader', { name: 'UNSAFE' })).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/)
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeEnabled()
    expect(screen.getByText(/Raw scans and personal information are excluded/)).toBeInTheDocument()
  })

  it('paginates five products on a common scale and resets pagination for filter changes', async () => {
    const user = userEvent.setup()
    render(<ConsumerTrendsPage />)
    await screen.findByText('Product one')

    const productPanel = screen.getByRole('heading', { name: 'Most Scanned Products' }).closest('section')
    expect(productPanel).not.toBeNull()
    expect(within(productPanel as HTMLElement).getAllByRole('listitem')).toHaveLength(5)
    expect(screen.getByText('1–5 of 7')).toBeInTheDocument()
    expect(screen.getByTestId('product-bar-1')).toHaveStyle({ width: '100%' })
    expect(screen.queryByText(/Product six with/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByText('6–7 of 7')).toBeInTheDocument()
    expect(screen.getByText(/Product six with/)).toBeInTheDocument()
    expect(screen.getByTestId('product-bar-6')).toHaveStyle({ width: '50%' })
    expect(within(productPanel as HTMLElement).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Product Category' }), 'Snacks')
    await waitFor(() => expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: 'Snacks' }),
    ))
    expect(screen.getByText('1–5 of 7')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Snacks.*18 scans/i })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Next' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Period' }), '7')
    await waitFor(() => {
      const lastQuery = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls.at(-1)?.[0]
      expect(daysInQuery(lastQuery ?? {})).toBe(7)
    })
    expect(screen.getByText('1–5 of 7')).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Period' }), '90')
    await waitFor(() => {
      const lastQuery = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls.at(-1)?.[0]
      expect(daysInQuery(lastQuery ?? {})).toBe(90)
    })
  })

  it('supports category-chart filtering, refresh, zero activity, and partial-data messaging', async () => {
    const user = userEvent.setup()
    const emptyResponse: ConsumerTrendsResponse = {
      ...populatedResponse,
      summary: {
        totalScans: 0,
        safeCount: 0,
        warningCount: 0,
        unsafeCount: 0,
        uniqueProducts: 0,
        averageScansPerDay: 0,
        peakScanDay: null,
      },
      dailyTrend: dailyTrend.map((point) => ({
        ...point,
        totalCount: 0,
        safeCount: 0,
        warningCount: 0,
      })),
      mostScannedProducts: [],
      topRestrictions: [],
      topFlaggedIngredients: [],
      dataQuality: { partial: true, skippedMalformedFindings: 2 },
    }
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockResolvedValue(emptyResponse)

    render(<ConsumerTrendsPage />)
    await screen.findByText('No scan activity in this period')

    expect(screen.getByText(/2 scan finding records could not be read/)).toBeInTheDocument()
    expect(screen.getByText('No activity')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /Line chart of total scans/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeDisabled()

    const categoryPanel = screen.getByRole('heading', { name: 'Scan Activity by Category' }).closest('section')
    expect(categoryPanel).not.toBeNull()
    await user.click(within(categoryPanel as HTMLElement).getByRole('button', { name: /Snacks.*18 scans/i }))
    await waitFor(() => expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: 'Snacks' }),
    ))

    const callsBeforeRefresh = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls.length
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenCalledTimes(callsBeforeRefresh + 1))
  })

  it('shows loading and error states, then retries through the existing control', async () => {
    const user = userEvent.setup()
    let rejectRequest: (reason: Error) => void = () => undefined
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockImplementationOnce(
      () => new Promise((_, reject) => {
        rejectRequest = reject
      }),
    )

    render(<ConsumerTrendsPage />)
    expect(screen.getByText('Loading consumer trends…')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeDisabled()
    await waitFor(() => expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenCalledTimes(1))

    await act(async () => rejectRequest(new Error('Synthetic analytics outage')))
    expect(await screen.findByRole('alert')).toHaveTextContent('Synthetic analytics outage')

    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockResolvedValue(populatedResponse)
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Daily Scan Activity' })).toBeInTheDocument()
  })

  it('safely normalises absent aggregate collections and rejects missing core metrics', async () => {
    const queryPeriodResponse = (query: ConsumerTrendsQuery = {}) => ({
      ...populatedResponse,
      period: {
        ...populatedResponse.period,
        from: query.from ?? populatedResponse.period.from,
        to: query.to ?? populatedResponse.period.to,
      },
    })
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockImplementationOnce(async (query) => ({
      ...queryPeriodResponse(query),
      dailyTrend: undefined,
      mostScannedProducts: undefined,
      categoryOverview: undefined,
      topRestrictions: undefined,
      topFlaggedIngredients: undefined,
    }) as unknown as ConsumerTrendsResponse)

    const { unmount } = render(<ConsumerTrendsPage />)
    expect(await screen.findByRole('heading', { name: 'Daily Scan Activity' })).toBeInTheDocument()
    expect(screen.getByText('No products were resolved for this period.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeEnabled()
    unmount()

    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockImplementationOnce(async (query) => ({
      ...queryPeriodResponse(query),
      summary: { ...populatedResponse.summary, averageScansPerDay: undefined },
    }) as unknown as ConsumerTrendsResponse)
    render(<ConsumerTrendsPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The consumer trends data is incomplete. Please refresh and try again.',
    )
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeDisabled()
  })

  it('disables export as soon as the selected filter no longer matches the loaded response', async () => {
    const user = userEvent.setup()
    render(<ConsumerTrendsPage />)
    await screen.findByRole('heading', { name: 'Daily Scan Activity' })

    let finishFilterLoad: (response: ConsumerTrendsResponse) => void = () => undefined
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockImplementationOnce(
      () => new Promise((resolve) => {
        finishFilterLoad = resolve
      }),
    )
    await user.selectOptions(screen.getByRole('combobox', { name: 'Product Category' }), 'Snacks')
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeDisabled()
    expect(downloadConsumerTrendsReport).not.toHaveBeenCalled()

    const latestQuery = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls.at(-1)?.[0]
    await act(async () => finishFilterLoad({
      ...populatedResponse,
      period: {
        ...populatedResponse.period,
        from: latestQuery?.from ?? populatedResponse.period.from,
        to: latestQuery?.to ?? populatedResponse.period.to,
      },
      appliedFilters: { category: 'Snacks' },
    }))
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeEnabled()
  })

  it('generates a report from the loaded filtered response without another analytics request', async () => {
    const user = userEvent.setup()
    render(<ConsumerTrendsPage />)
    await screen.findByRole('heading', { name: 'Daily Scan Activity' })

    await user.selectOptions(screen.getByRole('combobox', { name: 'Product Category' }), 'Snacks')
    await waitFor(() => expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: 'Snacks' }),
    ))
    const requestCount = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls.length

    await user.click(screen.getByRole('button', { name: 'Generate CSV Report' }))

    expect(downloadConsumerTrendsReport).toHaveBeenCalledTimes(1)
    expect(downloadConsumerTrendsReport).toHaveBeenCalledWith(expect.objectContaining({
      period: expect.objectContaining({ timezone: 'Asia/Singapore' }),
      appliedFilters: { category: 'Snacks' },
    }))
    expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenCalledTimes(requestCount)
    expect(await screen.findByRole('status')).toHaveTextContent('Consumer trends report downloaded.')
  })

  it('prevents duplicate export while generation is in progress', async () => {
    const user = userEvent.setup()
    let finishDownload: () => void = () => undefined
    vi.mocked(downloadConsumerTrendsReport).mockImplementationOnce(
      () => new Promise<void>((resolve) => {
        finishDownload = resolve
      }),
    )
    render(<ConsumerTrendsPage />)
    await screen.findByRole('heading', { name: 'Daily Scan Activity' })

    await user.click(screen.getByRole('button', { name: 'Generate CSV Report' }))
    const generatingButton = screen.getByRole('button', { name: 'Generating…' })
    expect(generatingButton).toBeDisabled()
    await user.click(generatingButton)
    expect(downloadConsumerTrendsReport).toHaveBeenCalledTimes(1)

    await act(async () => finishDownload())
    expect(await screen.findByRole('status')).toHaveTextContent('downloaded')
  })

  it('shows a safe actionable error when report download fails', async () => {
    const user = userEvent.setup()
    vi.mocked(downloadConsumerTrendsReport).mockRejectedValueOnce(
      new Error('Internal browser details with Bearer secret'),
    )
    render(<ConsumerTrendsPage />)
    await screen.findByRole('heading', { name: 'Daily Scan Activity' })

    await user.click(screen.getByRole('button', { name: 'Generate CSV Report' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The report could not be downloaded. No file was saved. Please try again.')
    expect(alert).not.toHaveTextContent('Bearer secret')
    expect(screen.getByRole('button', { name: 'Generate CSV Report' })).toBeEnabled()
  })
})

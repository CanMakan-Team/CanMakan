import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsumerTrendsPage } from '../../../features/analytics/pages/ConsumerTrendsPage'
import { consumerTrendsApiService } from '../../../features/analytics/api/consumerTrendsApiService'
import { addCalendarDays, buildPeriodQuery } from '../../../features/analytics/lib/consumerTrendsDateRange'
import { prepareConsumerTrendsResponse } from '../../../features/analytics/lib/consumerTrendsNormalize'
import { downloadConsumerTrendsReport } from '../../../features/analytics/lib/consumerTrendsReport'
import { ApiError } from '../../../shared/api/apiErrors'
import type {
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
} from '../../../features/analytics/api/consumerTrendsTypes'

vi.mock('../../../features/analytics/api/consumerTrendsApiService', () => ({
  consumerTrendsApiService: {
    getConsumerTrends: vi.fn(),
  },
}))

vi.mock('../../../features/analytics/lib/consumerTrendsReport', () => ({
  downloadConsumerTrendsReport: vi.fn(),
}))

const dailyTrend = Array.from({ length: 7 }, (_, index) => ({
  date: `2026-08-${String(index + 1).padStart(2, '0')}`,
  totalCount: index === 2 ? 0 : index + 2,
  safeCount: index === 2 ? 0 : index + 1,
  warningCount: index === 2 ? 0 : 1,
  unsafeCount: 0,
}))

const productNames = [
  'Product one',
  'Product two',
  'Product three',
  'Product four',
  'Product five',
  'Product six with a deliberately long accessible name',
  'Product seven',
  'Product eight',
  'Product nine',
  'Product ten',
  'Product eleven',
  'Product twelve',
]

const populatedResponse: ConsumerTrendsResponse = {
  period: { from: '2026-08-01', to: '2026-08-07', timezone: 'Asia/Singapore' },
  appliedFilters: { category: null },
  summary: {
    totalScans: 30,
    safeCount: 24,
    warningCount: 6,
    unsafeCount: 0,
    uniqueProducts: 12,
    averageScansPerDay: 4.29,
    peakScanDay: { date: '2026-08-07', scanCount: 8 },
  },
  dailyTrend,
  mostScannedProducts: productNames.map((productName, index) => ({
    rank: index + 1,
    productName,
    scanCount: index < 7 ? 10 - index : Math.max(1, 4 - (index - 7)),
    percentage: index < 7 ? Number(((10 - index) * 3.333).toFixed(2)) : 6.67,
  })),
  categoryOverview: [
    { category: 'Snacks', scanCount: 18, percentage: 60 },
    { category: 'Uncategorised', scanCount: 12, percentage: 40 },
    ...Array.from({ length: 10 }, (_, index) => ({
      category: `Category ${index + 3}`,
      scanCount: 10 - index,
      percentage: 2,
    })),
  ],
  topRestrictions: [
    { restrictionCode: 'PEANUT_ALLERGY', flaggedCount: 12 },
    ...Array.from({ length: 11 }, (_, index) => ({
      restrictionCode: `RESTRICTION_${index + 2}`,
      flaggedCount: 11 - index,
    })),
  ],
  topFlaggedIngredients: [
    { ingredientName: 'Peanut', flaggedCount: 12 },
    ...Array.from({ length: 11 }, (_, index) => ({
      ingredientName: `Ingredient ${index + 2}`,
      flaggedCount: 11 - index,
    })),
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
    const user = userEvent.setup()
    render(<ConsumerTrendsPage />)

    await screen.findByRole('heading', { name: 'Daily Scan Activity' })
    expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenCalledTimes(1)
    const initialQuery = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls[0][0]
    expect(daysInQuery(initialQuery ?? {})).toBe(30)
    expect(initialQuery?.limit).toBe(20)

    expect(screen.getByText(
      'Aggregated scan activity and dietary-concern insights. Scan activity indicates consumer interest, not actual sales.',
    )).toBeInTheDocument()
    expect(screen.getByText('Total Scans')).toBeInTheDocument()
    expect(screen.getByText('Unique Products Scanned')).toBeInTheDocument()
    expect(screen.getByText('Average Scans per Day')).toBeInTheDocument()
    expect(screen.getByText('Peak Scan Day')).toBeInTheDocument()
    expect(screen.getByText('4.29')).toBeInTheDocument()
    expect(screen.getByText('8 scans')).toBeInTheDocument()

    await user.hover(screen.getByText('Total Scans'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('All product scans recorded in the selected period')
    await user.unhover(screen.getByText('Total Scans'))

    expect(screen.getByRole('img', { name: /Line chart of total scans/ })).toBeInTheDocument()
    expect(document.querySelector('.chart-area')).toBeInTheDocument()
    expect(document.querySelectorAll('.chart-point')).toHaveLength(6)
    expect(screen.getByText('View daily values')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Daily scan counts for the selected period' })).toBeInTheDocument()
    expect(screen.getByText('Product interest')).toBeInTheDocument()
    expect(screen.getByText('Category mix')).toBeInTheDocument()
    expect(screen.getByText('Dietary concerns')).toBeInTheDocument()
    expect(screen.getByText('Ingredient flags')).toBeInTheDocument()
    expect(screen.getByText('Scan outcomes')).toBeInTheDocument()
    const productPanel = screen.getByRole('heading', { name: 'Most Scanned Products' }).closest('section') as HTMLElement
    expect(within(productPanel).getAllByRole('listitem')).toHaveLength(10)
    expect(productPanel.querySelector('.analytics-panel-heading-meta span')?.textContent).toMatch(/–/)
    const restrictionPanel = screen.getByRole('heading', { name: 'Most Frequently Triggered Dietary Restrictions' }).closest('section') as HTMLElement
    expect(within(restrictionPanel).getAllByRole('listitem')).toHaveLength(10)
    expect(screen.getByRole('heading', { name: 'Scan Activity by Category' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Most Frequently Triggered Dietary Restrictions' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Top Flagged Ingredients' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Scan Verdict Mix' })).toBeInTheDocument()
    expect(screen.getByText('PEANUT_ALLERGY')).toBeInTheDocument()
    expect(screen.getByText('Counts show scan-triggered dietary-concern signals, not population prevalence.')).toBeInTheDocument()
    const outcomePanel = screen.getByText('Scan outcomes').closest('details')
    expect(outcomePanel).not.toBeNull()
    expect(outcomePanel).not.toHaveAttribute('open')
    await user.click(screen.getByRole('heading', { name: 'Scan Verdict Mix' }))
    expect(outcomePanel).toHaveAttribute('open')
    expect(screen.getByRole('img', { name: '80% safe, 20% warning, 0% unsafe' })).toBeInTheDocument()
    const outcomeTable = screen.getByRole('table', { name: 'Exact scan verdict counts and percentages' })
    expect(within(outcomeTable).getByRole('rowheader', { name: 'UNSAFE' })).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/)
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeEnabled()
    expect(screen.getByText(/Raw scans and personal information are excluded/)).toBeInTheDocument()
  })

  it('marks only days with scans and shows a tooltip on hover', async () => {
    const user = userEvent.setup()
    const longTrend = Array.from({ length: 90 }, (_, index) => ({
      date: addCalendarDays('2026-05-21', index),
      totalCount: index === 2 || index === 50 ? 0 : index + 1,
      safeCount: index === 2 || index === 50 ? 0 : index + 1,
      warningCount: 0,
      unsafeCount: 0,
    }))
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockResolvedValue({
      ...populatedResponse,
      period: { from: '2026-05-21', to: '2026-08-18', timezone: 'Asia/Singapore' },
      dailyTrend: longTrend,
    })

    render(<ConsumerTrendsPage />)
    await screen.findByRole('heading', { name: 'Daily Scan Activity' })

    expect(document.querySelectorAll('.chart-point')).toHaveLength(88)
    expect(document.querySelector('.chart-line')?.getAttribute('points')?.split(' ')).toHaveLength(90)

    await user.hover(document.querySelector('.chart-point-hit') as Element)
    const tooltip = await screen.findByRole('tooltip')
    expect(tooltip).toHaveTextContent(/scans/)
    expect(tooltip).toHaveTextContent('safe')

    await user.click(screen.getByText('View daily values'))
    const dailyTable = screen.getByRole('table', { name: 'Daily scan counts for the selected period' })
    expect(within(dailyTable).getAllByRole('row')).toHaveLength(11)
    const dailyPanel = screen.getByRole('heading', { name: 'Daily Scan Activity' }).closest('section') as HTMLElement
    expect(within(dailyPanel).getByText('1–10 of 90')).toBeInTheDocument()
    await user.click(within(dailyPanel).getByRole('button', { name: 'Next' }))
    expect(within(dailyPanel).getByText('11–20 of 90')).toBeInTheDocument()
  })

  it('paginates ten ranking rows on a common scale and resets pagination for filter changes', async () => {
    const user = userEvent.setup()
    render(<ConsumerTrendsPage />)
    await screen.findByText('Product one')

    const productPanel = screen.getByRole('heading', { name: 'Most Scanned Products' }).closest('section')
    expect(productPanel).not.toBeNull()
    const products = within(productPanel as HTMLElement)
    expect(products.getAllByRole('listitem')).toHaveLength(10)
    expect(products.getByText('1–10 of 12')).toBeInTheDocument()
    expect(screen.getByTestId('product-bar-1')).toHaveStyle({ width: '100%' })
    expect(products.getByText(/Product six with/)).toBeInTheDocument()
    expect(products.queryByText('Product eleven')).not.toBeInTheDocument()
    expect(products.getByRole('button', { name: 'Previous' })).toBeDisabled()

    await user.click(products.getByRole('button', { name: 'Next' }))
    expect(products.getByText('11–12 of 12')).toBeInTheDocument()
    expect(products.getByText('Product eleven')).toBeInTheDocument()
    expect(products.getAllByRole('listitem')).toHaveLength(2)
    expect(products.getByRole('button', { name: 'Previous' })).toBeEnabled()
    expect(products.getByRole('button', { name: 'Next' })).toBeDisabled()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Product Category' }), 'Snacks')
    await waitFor(() => expect(consumerTrendsApiService.getConsumerTrends).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: 'Snacks', limit: 20 }),
    ))
    expect(products.getByText('1–10 of 12')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Snacks.*18 scans/i })).toHaveAttribute('aria-pressed', 'true')

    await user.click(products.getByRole('button', { name: 'Next' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Period' }), '7')
    await waitFor(() => {
      const lastQuery = vi.mocked(consumerTrendsApiService.getConsumerTrends).mock.calls.at(-1)?.[0]
      expect(daysInQuery(lastQuery ?? {})).toBe(7)
      expect(lastQuery?.limit).toBe(20)
    })
    expect(products.getByText('1–10 of 12')).toBeInTheDocument()

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
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeDisabled()

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
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeDisabled()
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
      dailyTrend: [],
      mostScannedProducts: [],
      categoryOverview: [],
      topRestrictions: [],
      topFlaggedIngredients: [],
    })
    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockImplementationOnce(async (query) =>
      prepareConsumerTrendsResponse(queryPeriodResponse(query)),
    )

    const { unmount } = render(<ConsumerTrendsPage />)
    expect(await screen.findByRole('heading', { name: 'Daily Scan Activity' })).toBeInTheDocument()
    expect(screen.getByText('No products were resolved for this period.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeEnabled()
    unmount()

    vi.mocked(consumerTrendsApiService.getConsumerTrends).mockRejectedValueOnce(
      new ApiError('The consumer trends data is incomplete. Please refresh and try again.'),
    )
    render(<ConsumerTrendsPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The consumer trends data is incomplete. Please refresh and try again.',
    )
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeDisabled()
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
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeDisabled()
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
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeEnabled()
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

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

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

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))
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

    await user.click(screen.getByRole('button', { name: 'Generate Report' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The report could not be downloaded. No file was saved. Please try again.')
    expect(alert).not.toHaveTextContent('Bearer secret')
    expect(screen.getByRole('button', { name: 'Generate Report' })).toBeEnabled()
  })

  it('validates a custom date range against the live API window', async () => {
    const user = userEvent.setup()
    render(<ConsumerTrendsPage />)
    await screen.findByRole('heading', { name: 'Daily Scan Activity' })

    const from = screen.getByLabelText('From')
    const to = screen.getByLabelText('To')
    await user.clear(from)
    await user.type(from, '2026-01-01')
    await user.clear(to)
    await user.type(to, '2026-08-18')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The reporting period must not exceed 90 days.',
    )
  })
})

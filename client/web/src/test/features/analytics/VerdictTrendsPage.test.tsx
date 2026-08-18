import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { sharePercents } from '../../../features/analytics/lib/verdictTrendDisplay'
import { VerdictTrendsPage } from '../../../features/analytics/pages/VerdictTrendsPage'
import { addCalendarDays, singaporeToday } from '../../../features/analytics/lib/consumerTrendsDateRange'
import { familyApiService } from '../../../features/family/api/familyApiService'
import type { ScanRecord } from '../../../shared/api/types'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getScanHistory: vi.fn(),
  },
}))

const FIXED_NOW = new Date('2026-03-15T10:00:00+08:00')

/** Singapore calendar day key for a scan bucket (matches verdictTrendAggregate). */
function bucketIso(daysAgo: number): string {
  return addCalendarDays(singaporeToday(FIXED_NOW), -daysAgo)
}

function scanOnBucket(daysAgo: number, verdict: ScanRecord['verdict'], scanId: number): ScanRecord {
  const day = bucketIso(daysAgo)
  return {
    scanId,
    product: `Product ${scanId}`,
    brand: 'CanMakan',
    memberId: 1,
    evaluatedProfile: 'Self',
    verdict,
    detectedIngredient: 'milk',
    resolvedIngredient: 'Milk',
    matchedRestriction: 'DAIRY',
    explanation: 'test',
    dataCompleteness: 'COMPLETE',
    dataSource: 'OFF',
    scannedAt: `${day}T12:00:00+08:00`,
  }
}

const populatedScans: ScanRecord[] = [
  scanOnBucket(0, 'SAFE', 1),
  scanOnBucket(0, 'SAFE', 2),
  scanOnBucket(1, 'WARNING', 3),
  scanOnBucket(2, 'UNSAFE', 4),
  // Older than the default 7-day window; still inside 30 days.
  scanOnBucket(20, 'SAFE', 5),
]

describe('sharePercents', () => {
  it('keeps Safe/Warning/Unsafe shares summing to 100', () => {
    expect(sharePercents([2, 3, 2], 7).reduce((sum, value) => sum + value, 0)).toBe(100)
    expect(sharePercents([2, 1, 1], 4)).toEqual([50, 25, 25])
  })
})

describe('VerdictTrendsPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: FIXED_NOW, shouldAdvanceTime: true })
    vi.mocked(familyApiService.getScanHistory).mockReset()
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue(populatedScans)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('loads family scans and shows the 7-day verdict summary', async () => {
    render(<VerdictTrendsPage />)

    expect(screen.getByText('Loading verdict trend...')).toBeInTheDocument()
    expect(await screen.findByLabelText('Verdict trend summary')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Verdict Trends' })).toBeInTheDocument()
    expect(screen.getByText('Family Circle')).toBeInTheDocument()
    expect(familyApiService.getScanHistory).toHaveBeenCalledTimes(1)

    expect(screen.getByLabelText('Reporting period')).toHaveValue('7')
    const summary = screen.getByLabelText('Verdict trend summary')
    expect(within(summary).getByRole('button', { name: /Total Scans/i })).toBeInTheDocument()
    expect(within(summary).getByText('4')).toBeInTheDocument()
    expect(within(summary).getByText('50%')).toBeInTheDocument()
    expect(within(summary).getByText('2 scans')).toBeInTheDocument()
    expect(
      screen.getByRole('img', {
        name: 'Verdict mix: 2 safe, 1 warning, 1 unsafe.',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('img', {
        name: 'Line chart of safe, warning and unsafe scan counts per day.',
      }),
    ).toBeInTheDocument()
  })

  it('widens the window to 30 and 90 days so older scans enter the totals', async () => {
    const user = userEvent.setup()
    render(<VerdictTrendsPage />)
    await screen.findByRole('heading', { name: 'Verdict mix' })

    await user.selectOptions(screen.getByLabelText('Reporting period'), '30')
    const summary = screen.getByLabelText('Verdict trend summary')
    expect(within(summary).getByText('5')).toBeInTheDocument()
    expect(within(summary).getByText('3 scans')).toBeInTheDocument()
    expect(screen.getByLabelText('Reporting period')).toHaveValue('30')

    await user.selectOptions(screen.getByLabelText('Reporting period'), '90')
    expect(screen.getByLabelText('Reporting period')).toHaveValue('90')
    expect(within(summary).getByText('5')).toBeInTheDocument()
  })

  it('filters the trend chart when a metric card is selected', async () => {
    const user = userEvent.setup()
    render(<VerdictTrendsPage />)
    await screen.findByRole('heading', { name: 'Verdict mix' })

    await user.click(screen.getByRole('button', { name: /Unsafe/i }))
    expect(screen.getByText(/Showing unsafe scans only/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Unsafe/i })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: /Total Scans/i }))
    expect(screen.queryByText(/Showing unsafe scans only/i)).not.toBeInTheDocument()
  })

  it('shows an empty period when history has no scans in range', async () => {
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue([])
    render(<VerdictTrendsPage />)

    expect(await screen.findByText('No scans in this period')).toBeInTheDocument()
    expect(
      screen.getByText('Once your family scans some products, their verdict trend will appear here.'),
    ).toBeInTheDocument()
  })

  it('shows an error then retries through the existing control', async () => {
    const user = userEvent.setup()
    let rejectRequest: (reason: Error) => void = () => undefined
    vi.mocked(familyApiService.getScanHistory).mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          rejectRequest = reject
        }),
    )

    render(<VerdictTrendsPage />)
    expect(screen.getByText('Loading verdict trend...')).toBeInTheDocument()
    await waitFor(() => expect(familyApiService.getScanHistory).toHaveBeenCalledTimes(1))

    await act(async () => rejectRequest(new Error('Scan history unavailable')))
    expect(await screen.findByRole('alert')).toHaveTextContent('Scan history unavailable')

    vi.mocked(familyApiService.getScanHistory).mockResolvedValue(populatedScans)
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Verdict mix' })).toBeInTheDocument()
  })

  it('exports the visible daily series as CSV', async () => {
    const user = userEvent.setup()
    const click = vi.fn()
    const createObjectURL = vi.fn((_blob: Blob) => 'blob:verdict-trend')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', {
      createObjectURL,
      revokeObjectURL,
    })
    const originalCreate = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      const element = originalCreate(tagName)
      if (tagName === 'a') {
        Object.defineProperty(element, 'click', { value: click })
      }
      return element
    })

    render(<VerdictTrendsPage />)
    await screen.findByRole('heading', { name: 'Verdict mix' })

    await user.click(screen.getByRole('button', { name: 'Export CSV' }))

    expect(createObjectURL).toHaveBeenCalledTimes(1)
    const blob = createObjectURL.mock.calls[0][0]
    expect(blob.type).toBe('text/csv;charset=utf-8')
    expect(click).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:verdict-trend')
    expect(screen.getByText('CSV download started.')).toBeInTheDocument()
  })
})

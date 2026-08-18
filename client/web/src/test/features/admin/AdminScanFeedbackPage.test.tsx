import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { AdminScanFeedbackPage } from '../../../features/admin/pages/AdminScanFeedbackPage'
import { formatExactCreatedAt, formatRelativeCreatedAt } from '../../../features/admin/lib/feedbackTimestamps'
import { adminService } from '../../../features/admin/api/adminService'
import type { AdminScanFeedbackItem, AdminScanFeedbackListResponse } from '../../../features/admin/api/models'
import { selfProfileApiService } from '../../../features/family/api/selfProfileApiService'
import { ApiError } from '../../../shared/api/apiErrors'

vi.mock('../../../features/admin/api/adminService', () => ({
  adminService: {
    getScanFeedback: vi.fn(),
    updateScanFeedbackResolved: vi.fn(),
  },
}))

vi.mock('../../../features/family/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
  },
}))

const restrictionCatalog = [
  { id: 1, code: 'GLUTEN', displayName: 'Gluten', category: 'Allergy' },
  { id: 2, code: 'HALAL', displayName: 'Halal', category: 'Religious' },
]

const positiveItem = {
  id: 15,
  scanId: 2,
  userEmail: 'sarah@example.test',
  productName: 'Oat Milk',
  isPositive: true,
  userComments: null,
  resolved: false,
  createdAt: '2026-08-10T09:30:00',
}

const negativeItem = {
  id: 5,
  scanId: 19,
  userEmail: 'david@example.test',
  productName: 'Butter Chicken Biryani',
  isPositive: false,
  userComments:
    'The pack I have says "trans fat free" and lists sunflower oil, not partially hydrogenated oil. Might be an old product photo in the database.',
  resolved: true,
  createdAt: '2026-08-13T15:04:00',
}

function buildItems(count: number): AdminScanFeedbackItem[] {
  return Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    scanId: index + 1,
    userEmail: `user${index + 1}@example.test`,
    productName: `Product ${index + 1}`,
    isPositive: true,
    userComments: null,
    resolved: false,
    createdAt: '2026-08-10T09:30:00',
  }))
}

function sampleResponse(items = [positiveItem, negativeItem]): AdminScanFeedbackListResponse {
  return {
    summary: {
      totalFeedback: items.length,
      negativePercentage: 50,
      feedbackPerDay: 0.07,
      negativeFeedbackPerDay: 0.03,
    },
    items,
    pageInfo: { page: 0, pageSize: 30, totalItems: items.length, totalPages: 1 },
  }
}

// Mirrors what the real backend returns for one page of a larger filtered
// set: only that page's slice of rows, with pageInfo describing the whole.
function buildPageResponse(totalCount: number, page: number, pageSize = 30): AdminScanFeedbackListResponse {
  const allItems = buildItems(totalCount)
  const start = page * pageSize
  const items = allItems.slice(start, start + pageSize)
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))
  return {
    summary: {
      totalFeedback: totalCount,
      negativePercentage: 0,
      feedbackPerDay: 0,
      negativeFeedbackPerDay: 0,
    },
    items,
    pageInfo: { page, pageSize, totalItems: totalCount, totalPages },
  }
}

function renderPage(initialEntries: string[] = ['/system/feedback']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AdminScanFeedbackPage />
    </MemoryRouter>,
  )
}

describe('AdminScanFeedbackPage UC20 admin review', () => {
  beforeEach(() => {
    vi.mocked(adminService.getScanFeedback).mockReset()
    vi.mocked(adminService.updateScanFeedbackResolved).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue(restrictionCatalog)
  })

  it('shows the loading state', () => {
    vi.mocked(adminService.getScanFeedback).mockImplementation(() => new Promise(() => {}))

    renderPage()

    expect(screen.getByText('Loading user feedback…')).toBeInTheDocument()
  })

  it('renders the four summary cards from the API response', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    const summary = screen.getByRole('region', { name: 'Feedback summary' })
    expect(await within(summary).findByText('2')).toBeInTheDocument()
    expect(within(summary).getByText('Total feedback')).toBeInTheDocument()
    expect(within(summary).getByText('Percentage negative feedback')).toBeInTheDocument()
    expect(within(summary).getByText('50.0%')).toBeInTheDocument()
    expect(within(summary).getByText('Feedback per day')).toBeInTheDocument()
    expect(within(summary).getByText('0.07')).toBeInTheDocument()
    expect(within(summary).getByText('Negative feedback per day')).toBeInTheDocument()
    expect(within(summary).getByText('0.03')).toBeInTheDocument()
  })

  it('renders a row per feedback item with thumbs icons for type', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    await screen.findByText('sarah@example.test')
    expect(screen.getByText('Oat Milk')).toBeInTheDocument()
    expect(screen.getByText('david@example.test')).toBeInTheDocument()
    expect(screen.getByText('Butter Chicken Biryani')).toBeInTheDocument()
    expect(screen.getByLabelText('Positive feedback')).toBeInTheDocument()
    expect(screen.getByLabelText('Negative feedback')).toBeInTheDocument()
  })

  it('shows a muted empty indicator when there is no user feedback', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    await screen.findByText('sarah@example.test')
    const row = screen.getByText('sarah@example.test').closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByText('No comment')).toBeInTheDocument()
  })

  it('truncates a long comment to a short preview', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    const preview = await screen.findByRole('button', { name: /trans fat free/ })
    expect(preview.textContent).not.toEqual(negativeItem.userComments)
    expect(preview.textContent?.length).toBeLessThan(negativeItem.userComments!.length)
  })

  it('opens a modal with the full comment when the preview is clicked', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())
    const user = userEvent.setup()

    renderPage()

    const preview = await screen.findByRole('button', { name: /trans fat free/ })
    await user.click(preview)

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(negativeItem.userComments!)).toBeInTheDocument()
  })

  it('shows the full comment in a hover preview', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())
    const user = userEvent.setup()

    renderPage()

    const preview = await screen.findByRole('button', { name: /trans fat free/ })
    await user.hover(preview)
    expect(await screen.findByRole('tooltip')).toHaveTextContent(negativeItem.userComments!)
  })

  it('renders resolved status as equal-width action buttons', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    await screen.findByText('sarah@example.test')
    expect(
      screen.getByRole('button', { name: 'Mark sarah@example.test feedback as resolved' }),
    ).toHaveTextContent('Resolve')
    expect(
      screen.getByRole('button', { name: 'Mark david@example.test feedback as not resolved' }),
    ).toHaveTextContent('Unresolve')
  })

  it('submits the new resolved status and refetches the list', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())
    vi.mocked(adminService.updateScanFeedbackResolved).mockResolvedValue({ id: 15, resolved: true })
    const user = userEvent.setup()

    renderPage()

    await screen.findByText('sarah@example.test')
    await user.click(
      screen.getByRole('button', { name: 'Mark sarah@example.test feedback as resolved' }),
    )

    await waitFor(() => {
      expect(adminService.updateScanFeedbackResolved).toHaveBeenCalledWith(15, true)
    })
    await waitFor(() => expect(adminService.getScanFeedback).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Feedback marked as Resolved.')).toBeInTheDocument()
  })

  it('renders the empty state when no feedback matches', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse([]))

    renderPage()

    expect(await screen.findByText('No feedback matches')).toBeInTheDocument()
  })

  it('surfaces an API error', async () => {
    vi.mocked(adminService.getScanFeedback).mockRejectedValue(
      new ApiError('Feedback listing is unavailable.', 500),
    )

    renderPage()

    expect(await screen.findByText('Feedback listing is unavailable.')).toBeInTheDocument()
  })

  it('lists dietary restriction options from the shared catalog', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    await screen.findByText('sarah@example.test')
    expect(
      within(screen.getByLabelText('Dietary restriction')).getByRole('option', { name: 'Halal' }),
    ).toBeInTheDocument()
  })

  it('applies the keyword, restriction, period, type and resolved filters as they change, with the page reset to 0', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())
    const user = userEvent.setup()

    renderPage()
    await screen.findByText('sarah@example.test')

    await user.type(screen.getByLabelText('Keyword'), '  biryani  ')
    await user.selectOptions(screen.getByLabelText('Dietary restriction'), 'HALAL')
    await user.selectOptions(screen.getByLabelText('Date period'), '7')
    await user.selectOptions(screen.getByLabelText('Feedback type'), 'NEGATIVE')
    await user.selectOptions(screen.getByLabelText('Resolved'), 'RESOLVED')

    await waitFor(() => {
      expect(adminService.getScanFeedback).toHaveBeenLastCalledWith({
        keyword: 'biryani',
        restrictionCode: 'HALAL',
        periodDays: 7,
        isPositive: false,
        resolved: true,
        page: 0,
        pageSize: 30,
      })
    })
  })

  it('resets filters to the default 30-day view', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())
    const user = userEvent.setup()

    renderPage()
    await screen.findByText('sarah@example.test')

    await user.type(screen.getByLabelText('Keyword'), 'biryani')
    await user.selectOptions(screen.getByLabelText('Feedback type'), 'NEGATIVE')

    await waitFor(() => {
      expect(adminService.getScanFeedback).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: 'biryani', isPositive: false, page: 0 }),
      )
    })

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    expect(screen.getByLabelText('Keyword')).toHaveValue('')
    expect(screen.getByLabelText('Feedback type')).toHaveValue('ALL')
    expect(screen.getByLabelText('Date period')).toHaveValue('30')
    await waitFor(() => {
      expect(adminService.getScanFeedback).toHaveBeenLastCalledWith({
        periodDays: 30,
        page: 0,
        pageSize: 30,
      })
    })
  })

  it('defaults to a 30-day period, page 0 and a 30-row page size on first load', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage()

    await waitFor(() => {
      expect(adminService.getScanFeedback).toHaveBeenCalledWith({
        periodDays: 30,
        page: 0,
        pageSize: 30,
      })
    })
  })

  it('applies an unresolved filter from the URL', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())

    renderPage(['/system/feedback?resolved=UNRESOLVED'])

    await waitFor(() => {
      expect(adminService.getScanFeedback).toHaveBeenCalledWith({
        periodDays: 30,
        resolved: false,
        page: 0,
        pageSize: 30,
      })
    })
    expect(screen.getByLabelText('Resolved')).toHaveValue('UNRESOLVED')
  })

  it('does not show pagination controls when the backend reports a single page', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(buildPageResponse(30, 0))

    renderPage()

    await screen.findByText('user1@example.test')
    expect(screen.queryByRole('navigation', { name: 'Feedback pages' })).not.toBeInTheDocument()
  })

  it('shows pagination controls and only this page\'s rows when the backend reports more than one page', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(buildPageResponse(34, 0))

    renderPage()

    await screen.findByText('user1@example.test')
    expect(screen.getByText('user30@example.test')).toBeInTheDocument()
    expect(screen.queryByText('user31@example.test')).not.toBeInTheDocument()
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled()
  })

  it('requests the next page from the backend and renders its rows', async () => {
    vi.mocked(adminService.getScanFeedback)
      .mockResolvedValueOnce(buildPageResponse(34, 0))
      .mockResolvedValueOnce(buildPageResponse(34, 1))
    const user = userEvent.setup()

    renderPage()

    await screen.findByText('user1@example.test')
    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(await screen.findByText('user34@example.test')).toBeInTheDocument()
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.queryByText('user1@example.test')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(adminService.getScanFeedback).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, pageSize: 30 }),
    )
  })

  it('resets to page 0 when filters change after paging forward', async () => {
    vi.mocked(adminService.getScanFeedback)
      .mockResolvedValueOnce(buildPageResponse(34, 0))
      .mockResolvedValueOnce(buildPageResponse(34, 1))
      .mockResolvedValueOnce(buildPageResponse(34, 0))
    const user = userEvent.setup()

    renderPage()
    await screen.findByText('user1@example.test')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Feedback type'), 'POSITIVE')

    expect(await screen.findByText('user1@example.test')).toBeInTheDocument()
    expect(adminService.getScanFeedback).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, pageSize: 30, isPositive: true }),
    )
  })

  it('snaps back to the last valid page if a mutation shrinks the total while on a later page', async () => {
    // Changing a row's resolved status refetches with the *same* page (unlike
    // changing filters, which already resets to page 0) — simulating a row
    // dropping out of the filtered set (e.g. it stopped matching a resolved
    // filter) and page 2 no longer existing.
    vi.mocked(adminService.getScanFeedback)
      .mockResolvedValueOnce(buildPageResponse(34, 0))
      .mockResolvedValueOnce(buildPageResponse(34, 1))
      .mockResolvedValueOnce({
        summary: { totalFeedback: 30, negativePercentage: 0, feedbackPerDay: 0, negativeFeedbackPerDay: 0 },
        items: [],
        pageInfo: { page: 1, pageSize: 30, totalItems: 30, totalPages: 1 },
      })
      .mockResolvedValueOnce(buildPageResponse(30, 0))
    vi.mocked(adminService.updateScanFeedbackResolved).mockResolvedValue({ id: 31, resolved: true })
    const user = userEvent.setup()

    renderPage()
    await screen.findByText('user1@example.test')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('user31@example.test')

    await user.click(
      screen.getByRole('button', { name: 'Mark user31@example.test feedback as resolved' }),
    )

    expect(await screen.findByText('user1@example.test')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Feedback pages' })).not.toBeInTheDocument()
    expect(adminService.getScanFeedback).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0 }),
    )
  })

  it('shows relative submission dates with the exact timestamp available on hover', async () => {
    vi.mocked(adminService.getScanFeedback).mockResolvedValue(sampleResponse())
    const user = userEvent.setup()

    renderPage()

    const relative = await screen.findByText(formatRelativeCreatedAt(positiveItem.createdAt))
    expect(relative.tagName).toBe('TIME')
    expect(relative).toHaveAttribute('dateTime', positiveItem.createdAt)
    expect(screen.getByText(formatRelativeCreatedAt(negativeItem.createdAt))).toBeInTheDocument()

    await user.hover(relative)
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      formatExactCreatedAt(positiveItem.createdAt),
    )
  })
})

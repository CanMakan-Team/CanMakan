import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FamilyScanHistoryPage } from '../../../features/family/pages/FamilyScanHistoryPage'
import { familyApiService } from '../../../features/family/api/familyApiService'
import type { ScanRecord } from '../../../shared/api/types'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getScanHistory: vi.fn(),
    getMembers: vi.fn(),
  },
}))

const baseRecord: ScanRecord = {
  scanId: 1,
  product: 'Satay Sauce',
  brand: 'ABC Foods',
  memberId: 10,
  evaluatedProfile: 'Alice',
  verdict: 'UNSAFE',
  detectedIngredient: '',
  resolvedIngredient: '',
  matchedRestriction: 'PEANUT',
  explanation: 'Contains peanut.',
  dataCompleteness: 'COMPLETE',
  dataSource: 'assessment',
  scannedAt: '2026-08-18T12:04:30+08:00',
}

function buildRecords(count: number): ScanRecord[] {
  return Array.from({ length: count }, (_, index) => ({
    ...baseRecord,
    scanId: index + 1,
    product: `Product ${index + 1}`,
    brand: index % 2 === 0 ? 'ABC Foods' : 'Sunny Dairy',
    scannedAt: new Date(Date.now() - index * 3_600_000).toISOString(),
  }))
}

describe('FamilyScanHistoryPage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getScanHistory).mockReset()
    vi.mocked(familyApiService.getMembers).mockReset()
    vi.mocked(familyApiService.getMembers).mockResolvedValue([
      {
        memberId: 10,
        profileId: 1,
        profileName: 'Alice',
        relationship: 'SELF',
        commonRequirements: [],
        restrictions: [],
        source: 'REGISTERED_USER',
        profileActive: true,
      },
    ])
  })

  it('searches by product and brand and hides empty ingredient columns', async () => {
    const user = userEvent.setup()
    const yesterdayIso = new Date(Date.now() - 86_400_000).toISOString()
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue([
      {
        ...baseRecord,
        scannedAt: new Date().toISOString(),
      },
      {
        ...baseRecord,
        scanId: 2,
        product: 'Oat Milk',
        brand: 'Sunny Dairy',
        verdict: 'SAFE',
        scannedAt: yesterdayIso,
      },
    ])

    render(<FamilyScanHistoryPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    expect(screen.queryByRole('columnheader', { name: 'Notable ingredient' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('columnheader', { name: 'Resolved name / rule' }),
    ).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Search'), 'oat')
    expect(screen.getByRole('row', { name: /Oat Milk/ })).toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /Satay Sauce/ })).not.toBeInTheDocument()

    const oatRow = screen.getByRole('row', { name: /Oat Milk/ })
    expect(within(oatRow).getByText('SAFE')).toBeInTheDocument()
    expect(within(oatRow).getByText(/Yesterday at/i)).toBeInTheDocument()
  })

  it('shows ingredient columns when values are present', async () => {
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue([
      {
        ...baseRecord,
        detectedIngredient: 'Peanut pieces',
        resolvedIngredient: 'Peanut',
      },
    ])

    render(<FamilyScanHistoryPage />)

    await waitFor(() => {
      expect(screen.getByRole('columnheader', { name: 'Notable ingredient' })).toBeInTheDocument()
    })
    expect(screen.getByRole('columnheader', { name: 'Resolved name / rule' })).toBeInTheDocument()
    expect(screen.getByText('Peanut pieces')).toBeInTheDocument()
  })

  it('paginates filtered rows at 15 per page and resets on filter change', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue(buildRecords(16))

    render(<FamilyScanHistoryPage />)

    await waitFor(() => {
      expect(screen.getByRole('navigation', { name: 'Scan history pages' })).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: 'Product 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Product 15' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Product 16' })).not.toBeInTheDocument()
    expect(screen.getByText(/Page 1 of 2/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByRole('button', { name: 'Product 16' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Product 1' })).not.toBeInTheDocument()
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search'), 'Product 16')
    expect(screen.getByRole('navigation', { name: 'Scan history pages' })).toBeInTheDocument()
    expect(screen.getByText(/Page 1 of 1/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Product 16' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('shows pagination status even when all records fit on one page', async () => {
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue(buildRecords(3))

    render(<FamilyScanHistoryPage />)

    await waitFor(() => {
      expect(screen.getByRole('navigation', { name: 'Scan history pages' })).toBeInTheDocument()
    })
    expect(screen.getByText(/Page 1 of 1/)).toBeInTheDocument()
    expect(screen.getByText(/3 records/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })
})

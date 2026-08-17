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

    expect(
      screen.getByText('From past records; not the active assessment profile.'),
    ).toBeInTheDocument()
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
})

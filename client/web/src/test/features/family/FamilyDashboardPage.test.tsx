import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import { FamilyDashboardPage } from '../../../features/family/pages/FamilyDashboardPage'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { ApiError } from '../../../shared/api/apiErrors'
import type { FamilyMember, ScanRecord } from '../../../shared/api/types'

vi.mock('../../../features/family/useFamilyMe', () => ({
  useFamilyMe: () => ({
    family: {
      familyId: 3,
      familyName: 'Wong Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 1,
      createdByUserId: 14,
    },
    isPrimaryAdmin: true,
    reload: vi.fn(),
    loading: false,
    error: '',
    hasFamily: true,
  }),
}))

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMembers: vi.fn(),
    getScanHistory: vi.fn(),
  },
}))

const members: FamilyMember[] = [
  {
    memberId: 1,
    profileId: 1,
    profileName: 'Admin',
    relationship: 'SELF',
    commonRequirements: ['HALAL'],
    restrictions: [],
    source: 'REGISTERED_USER',
    profileActive: true,
  },
  {
    memberId: 2,
    profileId: 2,
    profileName: 'Child',
    relationship: 'CHILD',
    commonRequirements: ['HALAL', 'PEANUT'],
    restrictions: ['PEANUT'],
    source: 'DEPENDANT_PROFILE',
    profileActive: true,
  },
]

function scan(partial: Partial<ScanRecord> & Pick<ScanRecord, 'scanId' | 'product' | 'verdict'>): ScanRecord {
  return {
    brand: 'Brand',
    memberId: 1,
    evaluatedProfile: 'Admin',
    detectedIngredient: '',
    resolvedIngredient: '',
    matchedRestriction: '',
    explanation: '',
    dataCompleteness: 'COMPLETE',
    dataSource: 'assessment',
    scannedAt: new Date().toISOString(),
    ...partial,
  }
}

function renderPage() {
  return render(
    <MemoryRouter>
      <FamilyDashboardPage />
    </MemoryRouter>,
  )
}

describe('FamilyDashboardPage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMembers).mockReset()
    vi.mocked(familyApiService.getScanHistory).mockReset()
  })

  it('renders summary cards and recent scans', async () => {
    vi.mocked(familyApiService.getMembers).mockResolvedValue(members)
    vi.mocked(familyApiService.getScanHistory).mockResolvedValue([
      scan({ scanId: 1, product: 'Satay', verdict: 'UNSAFE' }),
      scan({
        scanId: 2,
        product: 'Oat Milk',
        verdict: 'SAFE',
        scannedAt: new Date(Date.now() - 86_400_000).toISOString(),
      }),
      scan({
        scanId: 3,
        product: 'Bread',
        verdict: 'WARNING',
        scannedAt: new Date(Date.now() - 3 * 86_400_000).toISOString(),
      }),
    ])

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Manage Wong Family' })).toBeInTheDocument()
    expect(screen.getByText('Family members').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Common requirements').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Recent scans').nextElementSibling).toHaveTextContent('3')
    expect(screen.getByText('Satay')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Manage family/i })).toHaveAttribute(
      'href',
      '/family/members',
    )
    expect(screen.getByRole('link', { name: /View verdict trends/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Review restriction summary/i })).toBeInTheDocument()
    expect(screen.getByText('Safety reminder')).toBeInTheDocument()
  })

  it('shows empty scan copy when history is unavailable', async () => {
    vi.mocked(familyApiService.getMembers).mockResolvedValue(members)
    vi.mocked(familyApiService.getScanHistory).mockRejectedValue(new Error('forbidden'))

    renderPage()

    expect(await screen.findByText('None yet')).toBeInTheDocument()
    expect(
      screen.getByText(/No scans yet — scan a product in the CanMakan mobile app/),
    ).toBeInTheDocument()
  })

  it('shows an error state when members fail to load', async () => {
    vi.mocked(familyApiService.getMembers).mockRejectedValue(
      new ApiError('Dashboard unavailable.'),
    )

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Dashboard unavailable.')).toBeInTheDocument()
    })
  })
})

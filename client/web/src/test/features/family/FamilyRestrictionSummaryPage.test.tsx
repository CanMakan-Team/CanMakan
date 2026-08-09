import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { FamilyRestrictionSummaryPage } from '../../../features/family/pages/FamilyRestrictionSummaryPage'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { ApiError } from '../../../shared/api/apiErrors'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getRestrictionSummary: vi.fn(),
  },
}))

describe('FamilyRestrictionSummaryPage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getRestrictionSummary).mockReset()
  })

  it('shows the loading state initially', () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockImplementation(
      () => new Promise(() => {})
    )
    render(<FamilyRestrictionSummaryPage />)
    expect(screen.getByText('Building family restriction summary...')).toBeInTheDocument()
  })

  it('shows an error state when the API call fails', async () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockRejectedValue(
      new ApiError('Failed to load restriction summary.')
    )
    render(<FamilyRestrictionSummaryPage />)
    
    await waitFor(() => {
      expect(screen.getByText('Failed to load restriction summary.')).toBeInTheDocument()
    })
  })

  it('shows the empty state when there are no active members', async () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        { userId: 99, name: 'Inactive Member', isActive: false, restrictions: [] }
      ]
    })
    
    render(<FamilyRestrictionSummaryPage />)
    
    await waitFor(() => {
      expect(screen.getByText('No summary available')).toBeInTheDocument()
      expect(screen.getByText('Add an active family profile to build the restriction grid.')).toBeInTheDocument()
    })
  })

  it('renders a dynamic matrix with correctly mapped status badges for active members', async () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        {
          userId: 1,
          name: 'Alice',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'STRICT_AVOID' },
            { code: 'LACTOSE', displayName: 'Lactose', severity: 'INTOLERANCE' }
          ]
        },
        {
          userId: 2,
          name: 'Bob',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'PREFERENCE' },
            { code: 'VEGAN', displayName: 'Vegan', severity: 'STRICT_AVOID' }
          ]
        }
      ]
    })

    render(<FamilyRestrictionSummaryPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    // 1. Verify dynamic column headers (Peanut, Lactose, Vegan)
    const headers = screen.getAllByRole('columnheader').map(h => h.textContent)
    expect(headers).toEqual(['Family member', 'Peanut', 'Lactose', 'Vegan'])

    // 2. Verify Alice's Row (STRICT_AVOID -> AVOID, INTOLERANCE -> WARNING)
    const aliceRow = screen.getByRole('row', { name: /Alice/ })
    expect(within(aliceRow).getByText('AVOID')).toBeInTheDocument()
    expect(within(aliceRow).getByText('WARNING')).toBeInTheDocument()

    // 3. Verify Bob's Row (PREFERENCE -> SAFE, STRICT_AVOID -> AVOID)
    const bobRow = screen.getByRole('row', { name: /Bob/ })
    expect(within(bobRow).getByText('SAFE')).toBeInTheDocument()
    expect(within(bobRow).getByText('AVOID')).toBeInTheDocument()
  })
})
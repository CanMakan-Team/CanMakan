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

  it('renders a dynamic matrix with a Selected badge for every restriction on a profile, regardless of severity', async () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        {
          userId: 1,
          name: 'Alice',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'STRICT_AVOID' },
            { code: 'LACTOSE_INTOLERANT', displayName: 'Lactose Intolerant', severity: 'INTOLERANCE' }
          ]
        },
        {
          userId: 2,
          name: 'Bob',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'PREFERENCE' },
            { code: 'VEGAN', displayName: 'Vegan', severity: 'STRICT_AVOID' },
            { code: 'DAIRY', displayName: 'Dairy Free', severity: 'STRICT_AVOID' }
          ]
        }
      ]
    })

    render(<FamilyRestrictionSummaryPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    // Dairy Free and Lactose Intolerant collapse into one column
    const headers = screen.getAllByRole('columnheader').map(h => h.textContent)
    expect(headers).toEqual([
      'Family member',
      'Peanut',
      'Dairy Free / Lactose Intolerant',
      'Vegan',
    ])

    // Alice has Peanut and Lactose (dairy family); Vegan is not recorded for her
    const aliceRow = screen.getByRole('row', { name: /Alice/ })
    expect(within(aliceRow).getAllByText('SELECTED')).toHaveLength(2)
    expect(within(aliceRow).getByLabelText('Not selected')).toBeInTheDocument()

    // Bob has Peanut, Dairy, and Vegan — dairy still counts as the shared column
    const bobRow = screen.getByRole('row', { name: /Bob/ })
    expect(within(bobRow).getAllByText('SELECTED')).toHaveLength(3)
  })

  it('shows a Selected badge for a diet preference like Low Trans Fat, not just religious requirements', async () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        {
          userId: 1,
          name: 'David Lim',
          isActive: true,
          restrictions: [
            { code: 'HALAL', displayName: 'Halal', severity: 'STRICT_AVOID' },
            { code: 'LOW_TRANS_FAT', displayName: 'Low Trans Fat', severity: 'PREFERENCE' }
          ]
        }
      ]
    })

    render(<FamilyRestrictionSummaryPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    const davidRow = screen.getByRole('row', { name: /David Lim/ })
    expect(within(davidRow).getAllByText('SELECTED')).toHaveLength(2)
  })
})
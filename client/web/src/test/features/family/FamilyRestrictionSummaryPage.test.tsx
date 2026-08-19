import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

  it('renders a dynamic matrix with members as columns and restrictions as rows', async () => {
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        {
          userId: 1,
          name: 'Alice',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'STRICT_AVOID' },
            { code: 'LACTOSE_INTOLERANT', displayName: 'Lactose Intolerance', severity: 'INTOLERANCE' }
          ]
        },
        {
          userId: 2,
          name: 'Bob',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'PREFERENCE' },
            { code: 'VEGAN', displayName: 'Vegan', severity: 'STRICT_AVOID' },
            { code: 'DAIRY', displayName: 'Lactose Intolerance', severity: 'STRICT_AVOID' }
          ]
        }
      ]
    })

    render(<FamilyRestrictionSummaryPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    // Disclaimer sits above the matrix so readers see scope before the grid.
    const disclaimer = screen.getByText('Profile summary only')
    const table = screen.getByRole('table')
    expect(disclaimer.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    // Legend and filters sit outside the table card.
    expect(screen.getByLabelText('Restriction grid legend')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Allergies' })).toBeInTheDocument()

    // Members are column headers. Restriction rows sort as Allergies, then diets.
    const headers = screen.getAllByRole('columnheader').map(h => h.textContent)
    expect(headers).toEqual([
      'Dietary restriction',
      'Alice',
      'Bob',
    ])

    const lactoseRow = screen.getByRole('row', { name: /Lactose Intolerance/ })
    expect(within(lactoseRow).getAllByText('SELECTED')).toHaveLength(2)

    const peanutRow = screen.getByRole('row', { name: /Peanut/ })
    expect(within(peanutRow).getAllByText('SELECTED')).toHaveLength(2)
    expect(within(peanutRow).getAllByText('SELECTED')[0].className).toContain('tone-severe')
    expect(within(peanutRow).getAllByText('SELECTED')[1].className).toContain('tone-preference')

    const veganRow = screen.getByRole('row', { name: /Vegan/ })
    expect(within(veganRow).getAllByText('SELECTED')).toHaveLength(1)
    expect(within(veganRow).getByLabelText('Not selected')).toHaveTextContent('—')
  })

  it('filters restriction rows by dietary group', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        {
          userId: 1,
          name: 'Alice',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'STRICT_AVOID' },
            { code: 'VEGAN', displayName: 'Vegan', severity: 'PREFERENCE' },
            { code: 'HALAL', displayName: 'Halal', severity: 'STRICT_AVOID' },
          ]
        }
      ]
    })

    render(<FamilyRestrictionSummaryPage />)
    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Allergies' }))
    expect(screen.getByRole('row', { name: /Peanut/ })).toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /Vegan/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /Halal/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Diets & preferences' }))
    expect(screen.getByRole('row', { name: /Vegan/ })).toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /Peanut/ })).not.toBeInTheDocument()
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

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Dietary restriction',
      'David Lim',
    ])
    expect(within(screen.getByRole('row', { name: /Halal/ })).getByText('SELECTED')).toBeInTheDocument()
    expect(
      within(screen.getByRole('row', { name: /Low Trans Fat/ })).getByText('SELECTED').className,
    ).toContain('tone-preference')
  })

  it('opens household reference groups and marks in-family options', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getRestrictionSummary).mockResolvedValue({
      familyMembers: [
        {
          userId: 1,
          name: 'Alice',
          isActive: true,
          restrictions: [
            { code: 'PEANUT', displayName: 'Peanut', severity: 'STRICT_AVOID' },
            { code: 'VEGAN', displayName: 'Vegan', severity: 'PREFERENCE' },
          ],
        },
      ],
    })

    render(<FamilyRestrictionSummaryPage />)

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument()
    })

    const allergyTrigger = screen.getByRole('button', {
      name: /Allergies and intolerances/i,
    })
    const dietTrigger = screen.getByRole('button', {
      name: /Specific diets and health preferences/i,
    })
    const religiousTrigger = screen.getByRole('button', {
      name: /Religious requirements/i,
    })

    expect(allergyTrigger).toHaveAttribute('aria-expanded', 'true')
    expect(dietTrigger).toHaveAttribute('aria-expanded', 'true')
    expect(religiousTrigger).toHaveAttribute('aria-expanded', 'false')

    expect(screen.getByText('Severe reaction to peanuts and peanut derivatives.')).toBeInTheDocument()
    expect(screen.getByText('Avoids animal-derived ingredients.')).toBeInTheDocument()
    expect(
      screen.queryByText('Requires Halal-certified ingredients and no pork or alcohol.'),
    ).not.toBeInTheDocument()

    const peanutItem = screen.getByText('Peanut Allergy').closest('li')
    expect(peanutItem).not.toBeNull()
    expect(within(peanutItem as HTMLElement).getByText('In your family')).toBeInTheDocument()

    await user.click(religiousTrigger)
    expect(religiousTrigger).toHaveAttribute('aria-expanded', 'true')
    expect(
      screen.getByText('Requires Halal-certified ingredients and no pork or alcohol.'),
    ).toBeInTheDocument()
  })
})

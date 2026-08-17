import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { ScanEligibilityCard } from '../../../features/family/components/ScanEligibilityCard'
import type { FamilyMember } from '../../../shared/api/types'

function member(partial: Pick<FamilyMember, 'profileId' | 'profileName' | 'profileActive'>): FamilyMember {
  return {
    memberId: partial.profileId,
    linkedUserId: null,
    relationship: 'CHILD',
    commonRequirements: [],
    restrictions: [],
    source: 'DEPENDANT_PROFILE',
    ...partial,
  }
}

describe('ScanEligibilityCard', () => {
  it('counts eligible and inactive profiles', () => {
    render(
      <MemoryRouter>
        <ScanEligibilityCard
          members={[
            member({ profileId: 1, profileName: 'Alex', profileActive: true }),
            member({ profileId: 2, profileName: 'Sam', profileActive: true }),
            member({ profileId: 3, profileName: 'Pat', profileActive: false }),
          ]}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText('Who can be scanned for')).toBeInTheDocument()
    expect(screen.getByText('Eligible').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Inactive').nextElementSibling).toHaveTextContent('1')
    expect(
      screen.getByRole('link', { name: 'Review restriction summary' }),
    ).toHaveAttribute('href', '/family/restrictions')
  })
})

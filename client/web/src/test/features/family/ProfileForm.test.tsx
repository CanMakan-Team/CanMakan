import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProfileForm } from '../../../features/family/components/ProfileForm'
import { selfProfileApiService } from '../../../features/account/api/selfProfileApiService'

vi.mock('../../../features/account/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
  },
}))

describe('ProfileForm', () => {
  it('shows catalog options in the same groups as personal diet setup', async () => {
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue([
      { id: 10, code: 'VEGAN', displayName: 'Vegan', category: 'DIET' },
      { id: 3, code: 'PEANUT', displayName: 'Peanut Allergy', category: 'ALLERGEN' },
      { id: 8, code: 'HALAL', displayName: 'Halal', category: 'RELIGIOUS' },
      { id: 7, code: 'EGG', displayName: 'Egg Allergy', category: 'ALLERGEN' },
      { id: 15, code: 'KOSHER', displayName: 'Kosher', category: 'RELIGIOUS' },
    ])

    render(
      <ProfileForm
        submitLabel="Save changes"
        saving={false}
        error=""
        onSubmit={async () => undefined}
        onCancel={() => undefined}
      />,
    )

    await screen.findByLabelText('Halal')
    expect(
      screen.getAllByRole('checkbox').map((checkbox) => checkbox.closest('label')?.textContent?.trim()),
    ).toEqual(['Halal', 'Kosher', 'Peanut Allergy', 'Egg Allergy', 'Vegan'])
    expect(screen.getByText('RELIGIOUS')).toBeInTheDocument()
    expect(screen.getByText('ALLERGEN')).toBeInTheDocument()
    expect(screen.getByText('DIET')).toBeInTheDocument()
  })
})

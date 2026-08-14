import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { UserLandingPage } from '../../../features/family/pages/UserLandingPage'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: { getMyFamilyOrNull: vi.fn() },
}))

function renderLanding() {
  return render(
    <MemoryRouter initialEntries={['/family']}>
      <Routes>
        <Route path="/family" element={<UserLandingPage />} />
        <Route path="/family/personal" element={<p>Personal home</p>} />
        <Route path="/family/dashboard" element={<p>Family dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('UserLandingPage', () => {
  beforeEach(() => vi.mocked(familyApiService.getMyFamilyOrNull).mockReset())

  it('routes a no-family USER to personal home instead of family creation', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)

    renderLanding()

    expect(await screen.findByText('Personal home')).toBeInTheDocument()
  })

  it('preserves the existing family dashboard for a family member', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 9,
      familyName: 'Wong Family',
      memberRole: 'MEMBER',
      selfProfileId: 77,
      createdByUserId: 10,
    })

    renderLanding()

    expect(await screen.findByText('Family dashboard')).toBeInTheDocument()
    await waitFor(() => expect(familyApiService.getMyFamilyOrNull).toHaveBeenCalledTimes(1))
  })
})

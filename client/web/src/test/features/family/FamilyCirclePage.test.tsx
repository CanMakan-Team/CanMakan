import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { FamilyMeProvider } from '../../../features/family/FamilyMeContext'
import { FamilyMeGate } from '../../../features/family/FamilyMeGate'
import { FamilyCirclePage } from '../../../features/family/pages/FamilyCirclePage'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamilyOrNull: vi.fn(),
    createFamily: vi.fn(),
  },
}))

const adminFamily = {
  familyId: 9,
  familyName: 'Wong Family',
  memberRole: 'PRIMARY_ADMIN' as const,
  selfProfileId: 77,
  createdByUserId: 14,
}

function renderPage() {
  return render(
    <FamilyMeProvider>
      <MemoryRouter initialEntries={['/family/circle']}>
        <Routes>
          <Route path="/family/circle" element={<FamilyCirclePage />} />
          <Route element={<FamilyMeGate />}>
            <Route path="/family/dashboard" element={<p>Family dashboard</p>} />
          </Route>
          <Route path="/me" element={<p>Personal home</p>} />
        </Routes>
      </MemoryRouter>
    </FamilyMeProvider>,
  )
}

describe('FamilyCirclePage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockReset()
    vi.mocked(familyApiService.createFamily).mockReset()
  })

  it('offers creation only after the USER explicitly opens Family Circle', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Create your family circle' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/personal Dietary Profile does not require/)).toBeInTheDocument()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })

  it('keeps the new admin on the family dashboard instead of sending them home', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    vi.mocked(familyApiService.createFamily).mockImplementation(async () => {
      vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(adminFamily)
      return adminFamily
    })
    renderPage()

    await user.type(await screen.findByLabelText(/family name/i), 'Wong Family')
    await user.click(screen.getByRole('button', { name: 'Create family circle' }))

    await waitFor(() => expect(screen.getByText('Family dashboard')).toBeInTheDocument())
    expect(screen.queryByText('Personal home')).not.toBeInTheDocument()
    expect(familyApiService.createFamily).toHaveBeenCalledWith('Wong Family')
  })

  it('sends an existing family member to personal home', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 9,
      familyName: 'Wong Family',
      memberRole: 'MEMBER',
      selfProfileId: 77,
      createdByUserId: 10,
    })
    renderPage()

    expect(await screen.findByText('Personal home')).toBeInTheDocument()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })
})

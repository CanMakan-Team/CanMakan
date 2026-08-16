import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../../../shared/api/apiErrors'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { FamilyCirclePage } from '../../../features/family/pages/FamilyCirclePage'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamily: vi.fn(),
    createFamily: vi.fn(),
  },
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/family/circle']}>
      <Routes>
        <Route path="/family/circle" element={<FamilyCirclePage />} />
        <Route path="/family/dashboard" element={<p>Family dashboard</p>} />
        <Route path="/me" element={<p>Personal home</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('FamilyCirclePage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMyFamily).mockReset()
    vi.mocked(familyApiService.createFamily).mockReset()
  })

  it('offers creation only after the USER explicitly opens Family Circle', async () => {
    vi.mocked(familyApiService.getMyFamily).mockRejectedValue(
      new ApiError('Family not found.', 404),
    )
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Create your family circle' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/personal Dietary Profile does not require/)).toBeInTheDocument()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })

  it('creates a Family Circle only after an explicit named submission', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getMyFamily).mockRejectedValue(
      new ApiError('Family not found.', 404),
    )
    vi.mocked(familyApiService.createFamily).mockResolvedValue({
      familyId: 9,
      familyName: 'Wong Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 77,
      createdByUserId: 14,
    })
    renderPage()

    await user.type(await screen.findByLabelText(/family name/i), 'Wong Family')
    await user.click(screen.getByRole('button', { name: 'Create family circle' }))

    await waitFor(() => expect(screen.getByText('Family dashboard')).toBeInTheDocument())
    expect(familyApiService.createFamily).toHaveBeenCalledWith('Wong Family')
  })

  it('sends an existing family member to personal home', async () => {
    vi.mocked(familyApiService.getMyFamily).mockResolvedValue({
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

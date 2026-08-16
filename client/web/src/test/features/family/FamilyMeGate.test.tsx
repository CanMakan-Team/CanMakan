import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import { FamilyMeProvider } from '../../../features/family/FamilyMeContext'
import { FamilyMeGate } from '../../../features/family/FamilyMeGate'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { ApiError } from '../../../shared/api/apiErrors'

/** Test suite for FamilyMeGate.
 * 
 * @author Amelia
 */

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamilyOrNull: vi.fn(),
    createFamily: vi.fn(),
  },
}))

function renderGate(initialEntry = '/family') {
  return render(
    <FamilyMeProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/family" element={<FamilyMeGate />}>
            <Route index element={<p>Family portal content</p>} />
            <Route path="members" element={<p>Family members</p>} />
          </Route>
          <Route path="/me" element={<p>Personal home</p>} />
        </Routes>
      </MemoryRouter>
    </FamilyMeProvider>,
  )
}

describe('FamilyMeGate', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockReset()
    vi.mocked(familyApiService.createFamily).mockReset()
  })

  it('renders child routes when /families/me succeeds', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 3,
      familyName: 'Wong Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 77,
      createdByUserId: 14,
    })

    renderGate()

    await waitFor(() => {
      expect(screen.getByText('Family portal content')).toBeInTheDocument()
    })
  })

  it('sends a visitor with no family to personal home instead of family admin pages', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)

    renderGate('/family/members')

    expect(await screen.findByText('Personal home')).toBeInTheDocument()
    expect(screen.queryByText('Family members')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'This feature uses a Family Circle' }))
      .not.toBeInTheDocument()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })

  it('sends a family member to personal home instead of family admin pages', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 3,
      familyName: 'Wong Family',
      memberRole: 'MEMBER',
      selfProfileId: 77,
      createdByUserId: 10,
    })

    renderGate('/family/members')

    expect(await screen.findByText('Personal home')).toBeInTheDocument()
    expect(screen.queryByText('Family members')).not.toBeInTheDocument()
  })

  it('shows retryable error when /me fails for a non-404 reason', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockRejectedValue(
      new ApiError('The service is currently unreachable. Your page has been kept open.'),
    )

    renderGate()

    await waitFor(() => {
      expect(
        screen.getByText(
          'The service is currently unreachable. Your page has been kept open.',
        ),
      ).toBeInTheDocument()
    })
  })
})

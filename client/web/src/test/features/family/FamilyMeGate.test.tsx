import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import { FamilyMeGate } from '../../../features/family/FamilyMeGate'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { ApiError } from '../../../shared/api/apiErrors'

/** Test suite for FamilyMeGate.
 * 
 * @author Amelia
 */

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamily: vi.fn(),
    createFamily: vi.fn(),
  },
}))

function renderGate() {
  return render(
    <MemoryRouter initialEntries={['/family']}>
      <Routes>
        <Route path="/family" element={<FamilyMeGate />}>
          <Route index element={<p>Family portal content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('FamilyMeGate', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMyFamily).mockReset()
    vi.mocked(familyApiService.createFamily).mockReset()
  })

  it('renders child routes when /families/me succeeds', async () => {
    vi.mocked(familyApiService.getMyFamily).mockResolvedValue({
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

  it('keeps personal navigation available on 404 without opening family creation', async () => {
    vi.mocked(familyApiService.getMyFamily).mockRejectedValue(
      new ApiError('Family not found.', 404),
    )

    renderGate()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: 'This feature uses a Family Circle' }),
      ).toBeInTheDocument()
    })
    expect(screen.getByRole('link', { name: 'Return to personal home' })).toHaveAttribute(
      'href',
      '/family/personal',
    )
    expect(screen.queryByLabelText(/family name/i)).not.toBeInTheDocument()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })

  it('shows retryable error when /me fails for a non-404 reason', async () => {
    vi.mocked(familyApiService.getMyFamily).mockRejectedValue(
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

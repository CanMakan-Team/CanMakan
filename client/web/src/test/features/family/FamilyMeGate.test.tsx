import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

  it('shows create-circle empty state on 404', async () => {
    vi.mocked(familyApiService.getMyFamily).mockRejectedValue(
      new ApiError('Family not found.', 404),
    )

    renderGate()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: 'Create your family circle' }),
      ).toBeInTheDocument()
    })
  })

  it('creates a family and reloads /me after successful submit', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.getMyFamily)
      .mockRejectedValueOnce(new ApiError('Family not found.', 404))
      .mockResolvedValueOnce({
        familyId: 9,
        familyName: 'Wong Family',
        memberRole: 'PRIMARY_ADMIN',
        selfProfileId: 77,
        createdByUserId: 14,
      })
    vi.mocked(familyApiService.createFamily).mockResolvedValue({
      familyId: 9,
      familyName: 'Wong Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 77,
      createdByUserId: 14,
    })

    renderGate()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: 'Create your family circle' }),
      ).toBeInTheDocument()
    })

    await user.type(screen.getByLabelText(/family name/i), 'Wong Family')
    await user.click(screen.getByRole('button', { name: /create family circle/i }))

    await waitFor(() => {
      expect(familyApiService.createFamily).toHaveBeenCalledWith('Wong Family')
      expect(screen.getByText('Family portal content')).toBeInTheDocument()
    })
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

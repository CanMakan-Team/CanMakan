import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FamilyRegisterPage } from '../../../pages/FamilyRegisterPage'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { authService } from '../../../features/auth/authService'
import { ApiError } from '../../../shared/api/apiErrors'

/** Test suite for FamilyRegisterPage.
 * 
 * @author Amelia
 */

vi.mock('../../../features/auth/authService', () => ({
  authService: {
    loginWithCredentials: vi.fn(),
    register: vi.fn(),
    refreshSession: vi.fn(),
    getCurrentUser: vi.fn(),
    synchronizeCurrentUser: vi.fn(),
    logout: vi.fn(),
  },
}))

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="login-location">Family login {location.search}</p>
}

function renderRegisterPage(initialEntry = '/family-register') {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/family-register" element={<FamilyRegisterPage />} />
          <Route path="/family" element={<p>Family destination</p>} />
          <Route path="/family-login" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </SessionProvider>,
  )
}

describe('FamilyRegisterPage', () => {
  beforeEach(() => {
    vi.mocked(authService.register).mockReset()
    vi.mocked(authService.loginWithCredentials).mockReset()
    vi.mocked(authService.refreshSession).mockReset()
    vi.mocked(authService.refreshSession).mockRejectedValue(
      new ApiError('Authentication required.', 401),
    )
  })

  it('does not collect a name that registration cannot persist', () => {
    renderRegisterPage()

    expect(screen.queryByLabelText('Full name')).not.toBeInTheDocument()
    expect(screen.getByText(/Register with your email and password/)).toBeInTheDocument()
  })

  it('requires matching passwords', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password2!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.')
    expect(authService.register).not.toHaveBeenCalled()
  })

  it('registers without logging in and navigates to sign-in', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    renderRegisterPage()

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => {
      expect(screen.getByText('Family login')).toBeInTheDocument()
    })
    expect(authService.register).toHaveBeenCalled()
    expect(authService.loginWithCredentials).not.toHaveBeenCalled()
  })

  it('preserves an invitation token for authenticated claim after sign-in', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    renderRegisterPage('/family-register?invitationToken=invite-token')

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => {
      expect(screen.getByTestId('login-location')).toHaveTextContent(
        'Family login ?invitationToken=invite-token',
      )
    })
    expect(authService.register).toHaveBeenCalledWith({
      email: 'person@example.com',
      password: 'Password1!',
    })
  })

  it('shows backend duplicate-email failure', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockRejectedValue(
      new Error('An account with this email already exists.'),
    )
    renderRegisterPage()

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'An account with this email already exists.',
      )
    })
  })
})

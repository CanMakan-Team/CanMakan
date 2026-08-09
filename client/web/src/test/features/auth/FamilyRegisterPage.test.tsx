import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FamilyRegisterPage } from '../../../pages/FamilyRegisterPage'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { authService } from '../../../features/auth/authService'

/** Test suite for FamilyRegisterPage.
 * 
 * @author Amelia
 */

vi.mock('../../../features/auth/authService', () => ({
  authService: {
    loginWithCredentials: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  },
}))

function renderRegisterPage() {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={['/family-register']}>
        <Routes>
          <Route path="/family-register" element={<FamilyRegisterPage />} />
          <Route path="/family" element={<p>Family destination</p>} />
          <Route path="/family-login" element={<p>Family login</p>} />
        </Routes>
      </MemoryRouter>
    </SessionProvider>,
  )
}

describe('FamilyRegisterPage', () => {
  beforeEach(() => {
    vi.mocked(authService.register).mockReset()
    vi.mocked(authService.loginWithCredentials).mockReset()
  })

  it('validates short name without calling register', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    await user.type(screen.getByLabelText('Full name'), 'Al')
    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Name must be between 3 and 100 characters.',
    )
    expect(authService.register).not.toHaveBeenCalled()
  })

  it('requires matching passwords', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    await user.type(screen.getByLabelText('Full name'), 'Person Name')
    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password2!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.')
    expect(authService.register).not.toHaveBeenCalled()
  })

  it('registers, logs in, and navigates to family portal', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      profileId: 77,
      name: 'Person Name',
      email: 'person@example.com',
      active: true,
    })
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    renderRegisterPage()

    await user.type(screen.getByLabelText('Full name'), 'Person Name')
    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => {
      expect(screen.getByText('Family destination')).toBeInTheDocument()
    })
    expect(authService.register).toHaveBeenCalled()
    expect(authService.loginWithCredentials).toHaveBeenCalled()
  })

  it('shows backend duplicate-email failure', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockRejectedValue(
      new Error('An account with this email already exists.'),
    )
    renderRegisterPage()

    await user.type(screen.getByLabelText('Full name'), 'Person Name')
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

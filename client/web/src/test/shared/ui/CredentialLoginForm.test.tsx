import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CredentialLoginForm } from '../../../shared/ui/CredentialLoginForm'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { authService } from '../../../features/auth/authService'

/** Test suite for CredentialLoginForm.
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

function renderFamilyLogin() {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={['/family-login']}>
        <Routes>
          <Route
            path="/family-login"
            element={
              <CredentialLoginForm
                portal="FAMILY"
                expectedRole="ROLE_FAMILY_ADMIN"
                destination="/family"
                buttonLabel="Sign in to Family Portal"
                buttonClassName="button--primary"
                registerPath="/family-register"
              />
            }
          />
          <Route path="/family" element={<p>Family destination</p>} />
          <Route path="/family-register" element={<p>Register page</p>} />
        </Routes>
      </MemoryRouter>
    </SessionProvider>,
  )
}

describe('CredentialLoginForm', () => {
  beforeEach(() => {
    vi.mocked(authService.loginWithCredentials).mockReset()
    vi.mocked(authService.logout).mockReset()
  })

  it('validates missing credentials without calling login', async () => {
    const user = userEvent.setup()
    renderFamilyLogin()

    await user.click(
      screen.getByRole('button', { name: 'Sign in to Family Portal' }),
    )

    expect(
      screen.getByRole('alert'),
    ).toHaveTextContent('Enter your email and password to continue.')
    expect(authService.loginWithCredentials).not.toHaveBeenCalled()
  })

  it('rejects invalid email format before calling the API', async () => {
    const user = userEvent.setup()
    renderFamilyLogin()

    await user.type(screen.getByLabelText('Email'), 'test1@abc')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(
      screen.getByRole('button', { name: 'Sign in to Family Portal' }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Enter a valid email address.',
    )
    expect(authService.loginWithCredentials).not.toHaveBeenCalled()
  })

  it('navigates to family destination after successful family login', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    renderFamilyLogin()

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(
      screen.getByRole('button', { name: 'Sign in to Family Portal' }),
    )

    await waitFor(() => {
      expect(screen.getByText('Family destination')).toBeInTheDocument()
    })
  })

  it('logs out and shows portal mismatch when role is wrong', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 1,
      displayName: 'admin',
      roles: ['ROLE_SYSTEM_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    renderFamilyLogin()

    await user.type(screen.getByLabelText('Email'), 'admin@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(
      screen.getByRole('button', { name: 'Sign in to Family Portal' }),
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'This account cannot access this portal.',
      )
    })
    expect(authService.logout).toHaveBeenCalled()
  })

  it('surfaces API login failures', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockRejectedValue(
      new Error('Invalid credentials or account unavailable.'),
    )
    renderFamilyLogin()

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Wrong1!')
    await user.click(
      screen.getByRole('button', { name: 'Sign in to Family Portal' }),
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Invalid credentials or account unavailable.',
      )
    })
  })
})

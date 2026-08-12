import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CredentialLoginForm } from '../../../shared/ui/CredentialLoginForm'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { authService } from '../../../features/auth/authService'
import { familyApiService } from '../../../features/family/api/familyApiService'

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

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    claimInvitation: vi.fn(),
  },
}))

function renderFamilyLogin(initialEntry = '/family-login') {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
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
    vi.mocked(familyApiService.claimInvitation).mockReset()
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

  it('claims an invitation after login before navigating', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    vi.mocked(familyApiService.claimInvitation).mockResolvedValue({
      familyId: 1,
      familyName: 'Host Family',
      memberRole: 'MEMBER',
      selfProfileId: 77,
      createdByUserId: 10,
    })
    renderFamilyLogin('/family-login?invitationToken=invite-token')

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Sign in to Family Portal' }))

    await waitFor(() => {
      expect(familyApiService.claimInvitation).toHaveBeenCalledWith('invite-token')
      expect(screen.getByText('Family destination')).toBeInTheDocument()
    })
  })

  it('keeps invitation claim failures visible without redirecting', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    vi.mocked(familyApiService.claimInvitation).mockRejectedValue(
      new Error('Invitation has expired.'),
    )
    renderFamilyLogin('/family-login?invitationToken=invite-token')

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Sign in to Family Portal' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Invitation has expired.')
    })
    expect(screen.queryByText('Family destination')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Retry invitation claim' }),
    ).toBeInTheDocument()
  })

  it('does not redirect while an invitation claim is pending', async () => {
    const user = userEvent.setup()
    let resolveClaim: ((value: {
      familyId: number
      familyName: string
      memberRole: string
      selfProfileId: number
      createdByUserId: number
    }) => void) | undefined
    const pendingClaim = new Promise<{
      familyId: number
      familyName: string
      memberRole: string
      selfProfileId: number
      createdByUserId: number
    }>((resolve) => {
      resolveClaim = resolve
    })
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    vi.mocked(familyApiService.claimInvitation).mockReturnValue(pendingClaim)
    renderFamilyLogin('/family-login?invitationToken=invite-token')

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Sign in to Family Portal' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Claiming invitation…' })).toBeDisabled()
    })
    expect(screen.queryByText('Family destination')).not.toBeInTheDocument()

    resolveClaim?.({
      familyId: 1,
      familyName: 'Host Family',
      memberRole: 'MEMBER',
      selfProfileId: 77,
      createdByUserId: 10,
    })
    await waitFor(() => {
      expect(screen.getByText('Family destination')).toBeInTheDocument()
    })
  })

  it('retries a failed invitation claim without logging in again', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })
    vi.mocked(familyApiService.claimInvitation)
      .mockRejectedValueOnce(new Error('Invitation claim failed.'))
      .mockResolvedValueOnce({
        familyId: 1,
        familyName: 'Host Family',
        memberRole: 'MEMBER',
        selfProfileId: 77,
        createdByUserId: 10,
      })
    renderFamilyLogin('/family-login?invitationToken=invite-token')

    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Sign in to Family Portal' }))
    await user.click(
      await screen.findByRole('button', { name: 'Retry invitation claim' }),
    )

    await waitFor(() => {
      expect(screen.getByText('Family destination')).toBeInTheDocument()
    })
    expect(authService.loginWithCredentials).toHaveBeenCalledTimes(1)
    expect(familyApiService.claimInvitation).toHaveBeenCalledTimes(2)
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

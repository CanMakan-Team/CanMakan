import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UserRegisterPage } from '../../../pages/UserRegisterPage'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { authService } from '../../../features/auth/api/authService'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { pendingRegistrationOnboardingStore } from '../../../features/auth/lib/pendingRegistrationOnboardingStore'
import { authSessionStore } from '../../../features/auth/lib/authSessionStore'
import { ApiError } from '../../../shared/api/apiErrors'
import { appUserSession } from '../../testUtils'

vi.mock('../../../features/auth/api/authService', () => ({
  authService: {
    loginWithCredentials: vi.fn(),
    register: vi.fn(),
    refreshSession: vi.fn(),
    getCurrentUser: vi.fn(),
    synchronizeCurrentUser: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    previewInvitation: vi.fn(),
    claimInvitation: vi.fn(),
  },
}))

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname}{location.search}</p>
}

function renderRegisterPage(initialEntry = '/family-register') {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/family-register" element={<UserRegisterPage />} />
          <Route path="/register" element={<UserRegisterPage />} />
          <Route path="/me/setup-profile" element={<p>Dietary setup</p>} />
          <Route path="/family/setup-profile" element={<p>Dietary setup</p>} />
          <Route path="/family" element={<p>Family destination</p>} />
          <Route path="/login" element={<LocationProbe />} />
          <Route path="/family-login" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </SessionProvider>,
  )
}

async function enterRegistration(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email'), 'person@example.com')
  await user.type(screen.getByLabelText('Password'), 'Password1!')
  await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
}

describe('UserRegisterPage', () => {
  beforeEach(() => {
    authSessionStore.clear()
    pendingRegistrationOnboardingStore.clear()
    vi.mocked(authService.register).mockReset()
    vi.mocked(authService.loginWithCredentials).mockReset()
    vi.mocked(authService.refreshSession).mockReset()
    vi.mocked(authService.refreshSession).mockRejectedValue(
      new ApiError('Authentication required.', 401),
    )
    vi.mocked(familyApiService.previewInvitation).mockReset()
    vi.mocked(familyApiService.previewInvitation).mockRejectedValue(
      new ApiError('Invitation was not found.', 404),
    )
    vi.mocked(familyApiService.claimInvitation).mockReset()
  })

  it('requires matching passwords', async () => {
    const user = userEvent.setup()
    renderRegisterPage()

    expect(screen.queryByLabelText('Profile Name')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password2!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.')
    expect(authService.register).not.toHaveBeenCalled()
  })

  it('registers, performs normal login and opens authenticated dietary setup', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    vi.mocked(authService.loginWithCredentials).mockResolvedValue(appUserSession())
    renderRegisterPage()
    await enterRegistration(user)

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(screen.getByText('Dietary setup')).toBeInTheDocument())
    expect(authService.register).toHaveBeenCalledWith({
      email: 'person@example.com',
      password: 'Password1!',
    })
    expect(authService.loginWithCredentials).toHaveBeenCalledWith({
      email: 'person@example.com',
      password: 'Password1!',
      portal: 'FAMILY',
    })
    expect(pendingRegistrationOnboardingStore.peekForEmail('person@example.com')).toEqual({
      email: 'person@example.com',
      invitationToken: undefined,
    })
  })

  it('models login failure after account creation without registering again', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    vi.mocked(authService.loginWithCredentials).mockRejectedValue(new Error('offline'))
    renderRegisterPage()
    await enterRegistration(user)

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Your account was created, but automatic sign-in failed.',
      ),
    )
    expect(screen.getByRole('link', { name: 'Log in here' })).toHaveAttribute(
      'href',
      '/login?email=person%40example.com',
    )
    expect(screen.getByRole('button', { name: 'Account created' })).toBeDisabled()
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByLabelText('Confirm password')).toHaveValue('')
    expect(authService.register).toHaveBeenCalledTimes(1)
  })

  it('shows the approved duplicate message and a prefilled login action', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockRejectedValue(
      new ApiError('An account with this email already exists.', 409),
    )
    renderRegisterPage()
    await enterRegistration(user)

    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'An account with this email already exists.',
      ),
    )
    expect(screen.getByRole('link', { name: 'Log in here' })).toHaveAttribute(
      'href',
      '/login?email=person%40example.com',
    )
    expect(pendingRegistrationOnboardingStore.peekForEmail('person@example.com')).toBeNull()
  })

  it('locks registration to the invited email, sends the invitation token and claims it immediately', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.previewInvitation).mockResolvedValue({
      invitedEmail: 'person@example.com',
      familyName: 'Wong Family',
      expired: false,
    })
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    vi.mocked(authService.loginWithCredentials).mockResolvedValue(appUserSession())
    vi.mocked(familyApiService.claimInvitation).mockResolvedValue({
      familyId: 7,
      familyName: 'Wong Family',
      memberRole: 'MEMBER',
      selfProfileId: null,
      createdByUserId: 1,
    })
    renderRegisterPage('/family-register?invitationToken=invite-token')

    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveValue('person@example.com'),
    )
    expect(screen.getByLabelText('Email')).toBeDisabled()

    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(screen.getByText('Dietary setup')).toBeInTheDocument())
    expect(authService.register).toHaveBeenCalledWith({
      email: 'person@example.com',
      password: 'Password1!',
      invitationToken: 'invite-token',
    })
    // The invitation must be claimed right after login succeeds, not deferred to
    // whatever the user does next on the dietary-profile setup screen.
    expect(familyApiService.claimInvitation).toHaveBeenCalledWith('invite-token')
    expect(
      pendingRegistrationOnboardingStore.peekForEmail('person@example.com')?.invitationToken,
    ).toBeUndefined()
  })

  it('keeps the invitation token pending for a later retry when the immediate claim fails', async () => {
    const user = userEvent.setup()
    vi.mocked(familyApiService.previewInvitation).mockResolvedValue({
      invitedEmail: 'person@example.com',
      familyName: 'Wong Family',
      expired: false,
    })
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    vi.mocked(authService.loginWithCredentials).mockResolvedValue(appUserSession())
    vi.mocked(familyApiService.claimInvitation).mockRejectedValue(
      new ApiError('Invitation was not found.', 404),
    )
    renderRegisterPage('/family-register?invitationToken=invite-token')

    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveValue('person@example.com'),
    )

    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(screen.getByText('Dietary setup')).toBeInTheDocument())
    expect(familyApiService.claimInvitation).toHaveBeenCalledWith('invite-token')
    // Setup-profile's own finishPath() fallback still has a token to retry with.
    expect(
      pendingRegistrationOnboardingStore.peekForEmail('person@example.com')?.invitationToken,
    ).toBe('invite-token')
  })
})

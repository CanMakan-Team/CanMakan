import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SessionContext, type SessionContextValue } from '../../../features/auth/SessionContext'
import { pendingRegistrationOnboardingStore } from '../../../features/auth/pendingRegistrationOnboardingStore'
import { SelfProfileSetupPage } from '../../../features/family/pages/SelfProfileSetupPage'
import { selfProfileApiService } from '../../../features/account/api/selfProfileApiService'
import { ApiError } from '../../../shared/api/apiErrors'
import { appUserSession } from '../../testUtils'

vi.mock('../../../features/account/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
    createSelfProfile: vi.fn(),
    getSelfProfile: vi.fn(),
    updateSelfProfile: vi.fn(),
  },
}))

const sessionValue: SessionContextValue = {
  session: appUserSession(),
  loading: false,
  restoring: false,
  restorationError: '',
  retryRestoration: () => undefined,
  loginWithCredentials: async () => {
    throw new Error('unused')
  },
  register: async () => {
    throw new Error('unused')
  },
  registerAndLogin: async () => {
    throw new Error('unused')
  },
  logout: async () => undefined,
}

function renderPage() {
  return render(
    <SessionContext.Provider value={sessionValue}>
      <MemoryRouter initialEntries={['/me/setup-profile']}>
        <Routes>
          <Route path="/me/setup-profile" element={<SelfProfileSetupPage />} />
          <Route path="/me" element={<p>Personal destination</p>} />
          <Route path="/invite/:token" element={<p>Invitation continuation</p>} />
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  )
}

function renderPageWithoutPending() {
  pendingRegistrationOnboardingStore.clear()
  return renderPage()
}

describe('SelfProfileSetupPage', () => {
  beforeEach(() => {
    pendingRegistrationOnboardingStore.clear()
    pendingRegistrationOnboardingStore.request({
      email: 'person@example.com',
      profileName: 'Person Name',
    })
    vi.mocked(selfProfileApiService.getCatalog).mockReset()
    vi.mocked(selfProfileApiService.createSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.getSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.updateSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue([
      { id: 1, code: 'HALAL', displayName: 'Halal', category: 'RELIGIOUS' },
      { id: 2, code: 'PEANUT', displayName: 'Peanut', category: 'ALLERGEN' },
    ])
    // Default: no SELF profile exists yet, matching a brand-new account.
    // Individual tests override this to simulate a returning user.
    vi.mocked(selfProfileApiService.getSelfProfile).mockRejectedValue(
      new ApiError('No SELF profile exists for this account yet.', 404),
    )
  })

  it('shows the pending Profile Name and makes setup explicitly optional', async () => {
    renderPage()

    expect(await screen.findByLabelText('Peanut')).toBeInTheDocument()
    expect(screen.getByLabelText('Profile Name')).toHaveValue('Person Name')
    expect(screen.getByText(/You can complete this later/)).toBeInTheDocument()
  })

  it('Set Up Later creates no profile and enters the authenticated area', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('Peanut')

    await user.click(screen.getByRole('button', { name: 'Set Up Later' }))

    expect(screen.getByText('Personal destination')).toBeInTheDocument()
    expect(selfProfileApiService.createSelfProfile).not.toHaveBeenCalled()
    expect(pendingRegistrationOnboardingStore.peekForEmail('person@example.com')).toBeNull()
  })

  it('creates the SELF profile through the authenticated API with pending name', async () => {
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.createSelfProfile).mockResolvedValue({
      profileId: 77,
      profileName: 'Person Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    renderPage()
    await screen.findByLabelText('Peanut')

    await user.click(screen.getByLabelText('Peanut'))
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() => expect(screen.getByText('Personal destination')).toBeInTheDocument())
    expect(selfProfileApiService.createSelfProfile).toHaveBeenCalledWith(
      'Person Name',
      { 2: 'STRICT_AVOID' },
    )
  })

  it('keeps the session handoff and allows retry after profile failure', async () => {
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.createSelfProfile).mockRejectedValue(
      new Error('Profile service unavailable.'),
    )
    renderPage()
    await screen.findByLabelText('Peanut')

    await user.click(screen.getByLabelText('Peanut'))
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Profile service unavailable.'),
    )
    expect(screen.getByRole('button', { name: 'Save Profile' })).toBeEnabled()
    expect(pendingRegistrationOnboardingStore.peekForEmail('person@example.com')).not.toBeNull()
    expect(sessionValue.session).not.toBeNull()
  })

  it('supports completing setup later from the authenticated route', async () => {
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.createSelfProfile).mockResolvedValue({
      profileId: 78,
      profileName: 'Later Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    renderPageWithoutPending()

    await screen.findByLabelText('Peanut')
    await user.type(screen.getByLabelText('Profile Name'), 'Later Name')
    await user.click(screen.getByLabelText('Peanut'))
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() => expect(screen.getByText('Personal destination')).toBeInTheDocument())
    expect(selfProfileApiService.createSelfProfile).toHaveBeenCalledWith(
      'Later Name',
      { 2: 'STRICT_AVOID' },
    )
  })

  it('continues invitation joining after setup instead of opening family creation', async () => {
    const user = userEvent.setup()
    pendingRegistrationOnboardingStore.request({
      email: 'person@example.com',
      profileName: 'Person Name',
      invitationToken: 'invite-token',
    })
    renderPage()
    await screen.findByLabelText('Peanut')

    await user.click(screen.getByRole('button', { name: 'Set Up Later' }))

    expect(screen.getByText('Invitation continuation')).toBeInTheDocument()
    expect(selfProfileApiService.createSelfProfile).not.toHaveBeenCalled()
  })

  it('resolves an auto-provisioned placeholder profile from joining a family: shows the typed name, saves via update, and finishes onboarding', async () => {
    // Accepting a family invitation auto-provisions a placeholder SELF profile
    // server-side (an email-derived name, no restrictions) before this page is
    // ever reached. The pending onboarding record still carries the name the
    // user actually typed at registration.
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 91,
      profileName: 'person', // placeholder derived from the email local part
      relationship: 'SELF',
      active: true,
      restrictions: {},
    })
    vi.mocked(selfProfileApiService.updateSelfProfile).mockResolvedValue({
      profileId: 91,
      profileName: 'Person Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    renderPage()
    await screen.findByLabelText('Peanut')

    // The typed onboarding name wins over the persisted placeholder.
    expect(screen.getByLabelText('Profile Name')).toHaveValue('Person Name')

    await user.click(screen.getByLabelText('Peanut'))
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() => expect(screen.getByText('Personal destination')).toBeInTheDocument())
    expect(selfProfileApiService.updateSelfProfile).toHaveBeenCalledWith(
      'Person Name',
      { 2: 'STRICT_AVOID' },
    )
    expect(selfProfileApiService.createSelfProfile).not.toHaveBeenCalled()
    expect(pendingRegistrationOnboardingStore.peekForEmail('person@example.com')).toBeNull()
  })

  it('pre-populates Profile Name and existing selections for a returning user', async () => {
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    renderPageWithoutPending()

    await screen.findByLabelText('Peanut')

    expect(screen.getByLabelText('Profile Name')).toHaveValue('Existing Name')
    expect(screen.getByLabelText('Peanut')).toBeChecked()
    expect(screen.getByLabelText('Halal')).not.toBeChecked()
  })

  it('saves changes to an existing SELF profile through updateSelfProfile, not createSelfProfile', async () => {
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    vi.mocked(selfProfileApiService.updateSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 1: 'STRICT_AVOID', 2: 'STRICT_AVOID' },
    })
    renderPageWithoutPending()

    await screen.findByLabelText('Peanut')
    expect(screen.getByLabelText('Peanut')).toBeChecked()

    await user.click(screen.getByLabelText('Halal'))
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(
        'Your dietary profile has been saved successfully.',
      ),
    )
    expect(selfProfileApiService.updateSelfProfile).toHaveBeenCalledWith(
      'Existing Name',
      { 1: 'STRICT_AVOID', 2: 'STRICT_AVOID' },
    )
    expect(selfProfileApiService.createSelfProfile).not.toHaveBeenCalled()
    // Stays on the Dietary Profile page instead of redirecting to Personal Home.
    expect(screen.queryByText('Personal destination')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Profile Name')).toBeInTheDocument()
  })

  it('preserves an untouched restriction saved with an unsupported severity instead of rewriting it to STRICT_AVOID', async () => {
    // This page only offers an on/off toggle, so a restriction saved elsewhere
    // (e.g. by a family admin) with PREFERENCE severity displays here as
    // checked/STRICT_AVOID. Saving without touching that checkbox must resend
    // its original PREFERENCE severity rather than silently overwriting it.
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'PREFERENCE' } as unknown as Record<
        number,
        'STRICT_AVOID' | 'INTOLERANCE'
      >,
    })
    vi.mocked(selfProfileApiService.updateSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'PREFERENCE' } as unknown as Record<
        number,
        'STRICT_AVOID' | 'INTOLERANCE'
      >,
    })
    renderPageWithoutPending()

    await screen.findByLabelText('Peanut')
    expect(screen.getByLabelText('Peanut')).toBeChecked()

    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(
        'Your dietary profile has been saved successfully.',
      ),
    )
    expect(selfProfileApiService.updateSelfProfile).toHaveBeenCalledWith(
      'Existing Name',
      { 2: 'PREFERENCE' },
    )
  })

  it('rewrites a restriction with an unsupported severity to STRICT_AVOID only once the user actually toggles it', async () => {
    // Unchecking then rechecking a restriction is an explicit user action, so
    // unlike an untouched save, it is expected to replace the original
    // PREFERENCE severity with the on/off value this form represents.
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'PREFERENCE' } as unknown as Record<
        number,
        'STRICT_AVOID' | 'INTOLERANCE'
      >,
    })
    vi.mocked(selfProfileApiService.updateSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    renderPageWithoutPending()

    await screen.findByLabelText('Peanut')
    expect(screen.getByLabelText('Peanut')).toBeChecked()

    await user.click(screen.getByLabelText('Peanut'))
    await user.click(screen.getByLabelText('Peanut'))
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent(
        'Your dietary profile has been saved successfully.',
      ),
    )
    expect(selfProfileApiService.updateSelfProfile).toHaveBeenCalledWith(
      'Existing Name',
      { 2: 'STRICT_AVOID' },
    )
  })

  it('clears the success message once the user starts editing again', async () => {
    const user = userEvent.setup()
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    vi.mocked(selfProfileApiService.updateSelfProfile).mockResolvedValue({
      profileId: 55,
      profileName: 'Existing Name',
      relationship: 'SELF',
      active: true,
      restrictions: { 2: 'STRICT_AVOID' },
    })
    renderPageWithoutPending()

    await screen.findByLabelText('Peanut')
    await user.click(screen.getByRole('button', { name: 'Save Profile' }))
    await waitFor(() => expect(screen.getByRole('status')).toBeInTheDocument())

    await user.click(screen.getByLabelText('Halal'))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})

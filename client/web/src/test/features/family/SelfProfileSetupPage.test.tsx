import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SessionContext, type SessionContextValue } from '../../../features/auth/SessionContext'
import { pendingRegistrationOnboardingStore } from '../../../features/auth/pendingRegistrationOnboardingStore'
import { SelfProfileSetupPage } from '../../../features/family/pages/SelfProfileSetupPage'
import { selfProfileApiService } from '../../../features/family/api/selfProfileApiService'
import { appUserSession } from '../../testUtils'

vi.mock('../../../features/family/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
    createSelfProfile: vi.fn(),
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
      <MemoryRouter initialEntries={['/family/setup-profile']}>
        <Routes>
          <Route path="/family/setup-profile" element={<SelfProfileSetupPage />} />
          <Route path="/family/personal" element={<p>Personal destination</p>} />
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
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue([
      { id: 1, code: 'HALAL', displayName: 'Halal', category: 'RELIGIOUS' },
      { id: 2, code: 'PEANUT', displayName: 'Peanut', category: 'ALLERGEN' },
    ])
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
})

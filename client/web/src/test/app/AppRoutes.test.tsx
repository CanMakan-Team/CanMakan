import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AppRoutes } from '../../app/router/AppRoutes'
import { SessionContext, type SessionContextValue } from '../../features/auth/SessionContext'
import { pendingRegistrationOnboardingStore } from '../../features/auth/pendingRegistrationOnboardingStore'
import { appUserSession, systemAdminSession } from '../testUtils'
import { familyApiService } from '../../features/family/api/familyApiService'
import { selfProfileApiService } from '../../features/family/api/selfProfileApiService'

vi.mock('../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamilyOrNull: vi.fn(),
    getMyFamily: vi.fn(),
    createFamily: vi.fn(),
    claimInvitation: vi.fn(),
  },
}))

vi.mock('../../features/family/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
    createSelfProfile: vi.fn(),
  },
}))

function sessionValue(session: SessionContextValue['session']): SessionContextValue {
  return {
    session,
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
}

function renderRoutes(path: string, session: SessionContextValue['session']) {
  return render(
    <SessionContext.Provider value={sessionValue(session)}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </SessionContext.Provider>,
  )
}

describe('AppRoutes USER and family boundaries', () => {
  beforeEach(() => {
    pendingRegistrationOnboardingStore.clear()
    vi.mocked(familyApiService.getMyFamilyOrNull).mockReset()
    vi.mocked(familyApiService.getMyFamily).mockReset()
    vi.mocked(familyApiService.createFamily).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockReset()
    vi.mocked(selfProfileApiService.createSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue([
      { id: 2, code: 'PEANUT', displayName: 'Peanut', category: 'ALLERGEN' },
    ])
  })

  it('lets a newly authenticated no-family USER open setup and skip to personal home', async () => {
    const user = userEvent.setup()
    pendingRegistrationOnboardingStore.request({
      email: 'person@example.com',
      profileName: 'Person Name',
    })
    renderRoutes('/family/setup-profile', appUserSession())

    expect(await screen.findByLabelText('Profile Name')).toHaveValue('Person Name')
    await user.click(screen.getByRole('button', { name: 'Set Up Later' }))

    expect(await screen.findByRole('heading', { name: 'Your personal dietary space' }))
      .toBeInTheDocument()
    expect(selfProfileApiService.createSelfProfile).not.toHaveBeenCalled()
    expect(familyApiService.getMyFamilyOrNull).not.toHaveBeenCalled()
    expect(familyApiService.getMyFamily).not.toHaveBeenCalled()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })

  it('keeps System Admin isolated from USER onboarding', () => {
    renderRoutes('/family/setup-profile', systemAdminSession())

    expect(screen.getByRole('heading', { name: 'This portal is not available to your role.' }))
      .toBeInTheDocument()
    expect(selfProfileApiService.getCatalog).not.toHaveBeenCalled()
  })
})

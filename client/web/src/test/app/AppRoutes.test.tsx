import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AppRoutes } from '../../app/router/AppRoutes'
import { SessionContext, type SessionContextValue } from '../../features/auth/SessionContext'
import { pendingRegistrationOnboardingStore } from '../../features/auth/pendingRegistrationOnboardingStore'
import { appUserSession, systemAdminSession } from '../testUtils'
import { familyApiService } from '../../features/family/api/familyApiService'
import { selfProfileApiService } from '../../features/account/api/selfProfileApiService'
import { ApiError } from '../../shared/api/apiErrors'

vi.mock('../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamilyOrNull: vi.fn(),
    getMyFamily: vi.fn(),
    createFamily: vi.fn(),
    claimInvitation: vi.fn(),
    getMembers: vi.fn(),
  },
}))

vi.mock('../../features/account/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
    getSelfProfile: vi.fn(),
    createSelfProfile: vi.fn(),
    updateSelfProfile: vi.fn(),
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
    vi.mocked(familyApiService.getMembers).mockReset()
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    vi.mocked(familyApiService.getMembers).mockResolvedValue([])
    vi.mocked(selfProfileApiService.getCatalog).mockReset()
    vi.mocked(selfProfileApiService.getSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.createSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.updateSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue([
      { id: 2, code: 'PEANUT', displayName: 'Peanut', category: 'ALLERGEN' },
    ])
    // Default: no SELF profile exists yet, matching a brand-new account.
    vi.mocked(selfProfileApiService.getSelfProfile).mockRejectedValue(
      new ApiError('No SELF profile exists for this account yet.', 404),
    )
  })

  it('lets a newly authenticated no-family USER open setup and skip to personal home', async () => {
    const user = userEvent.setup()
    pendingRegistrationOnboardingStore.request({
      email: 'person@example.com',
    })
    renderRoutes('/me/setup-profile', appUserSession())

    expect(await screen.findByLabelText('Profile Name')).toHaveValue('')
    await user.click(screen.getByRole('button', { name: 'Set Up Later' }))

    expect(await screen.findByRole('heading', { name: 'Your CanMakan account' }))
      .toBeInTheDocument()
    expect(selfProfileApiService.createSelfProfile).not.toHaveBeenCalled()
    expect(familyApiService.getMyFamily).not.toHaveBeenCalled()
    expect(familyApiService.createFamily).not.toHaveBeenCalled()
  })

  it('keeps System Admin isolated from USER onboarding', () => {
    renderRoutes('/me/setup-profile', systemAdminSession())

    expect(screen.getByRole('heading', { name: 'This portal is not available to your role.' }))
      .toBeInTheDocument()
    expect(selfProfileApiService.getCatalog).not.toHaveBeenCalled()
  })

  it('redirects legacy personal URLs to /me', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    renderRoutes('/family/personal', appUserSession())

    expect(
      await screen.findByRole('heading', { name: 'Your CanMakan account' }),
    ).toBeInTheDocument()
  })

  it('keeps a family MEMBER off /family/members', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 3,
      familyName: 'Wong Family',
      memberRole: 'MEMBER',
      selfProfileId: 77,
      createdByUserId: 10,
    })

    renderRoutes('/family/members', appUserSession())

    expect(
      await screen.findByRole('heading', { name: 'Your CanMakan account' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Family Members' })).not.toBeInTheDocument()
    expect(familyApiService.getMembers).not.toHaveBeenCalled()
  })

  it('keeps a user with no Family Circle off /family/members', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)

    renderRoutes('/family/members', appUserSession())

    expect(
      await screen.findByRole('heading', { name: 'Your CanMakan account' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Family Members' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'This feature uses a Family Circle' }),
    ).not.toBeInTheDocument()
    expect(familyApiService.getMembers).not.toHaveBeenCalled()
  })
})

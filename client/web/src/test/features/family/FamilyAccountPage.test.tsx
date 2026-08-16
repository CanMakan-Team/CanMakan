import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FamilyAccountPage } from '../../../features/family/pages/FamilyAccountPage'
import { authService } from '../../../features/auth/authService'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { ApiError } from '../../../shared/api/apiErrors'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import { appUserSession } from '../../testUtils'

vi.mock('../../../features/auth/authService', () => ({
  authService: {
    getCurrentUser: vi.fn(),
    deleteOwnAccount: vi.fn(),
  },
}))

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamilyOrNull: vi.fn(),
    getAccountProfiles: vi.fn(),
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
  logout: vi.fn(async () => undefined),
}

function renderPage() {
  return render(
    <SessionContext.Provider value={sessionValue}>
      <FamilyAccountPage />
    </SessionContext.Provider>,
  )
}

describe('FamilyAccountPage', () => {
  beforeEach(() => {
    vi.mocked(sessionValue.logout).mockClear()
    vi.mocked(authService.deleteOwnAccount).mockReset()
    vi.mocked(authService.deleteOwnAccount).mockResolvedValue(undefined)
    vi.stubGlobal('confirm', vi.fn(() => true))
    vi.mocked(authService.getCurrentUser).mockResolvedValue({
      userId: 14,
      email: 'verified@example.com',
      role: 'USER',
      active: true,
    })
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 3,
      familyName: 'Verified Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 77,
      createdByUserId: 14,
    })
    vi.mocked(familyApiService.getAccountProfiles).mockResolvedValue([
      {
        id: 77,
        profileName: 'Verified Person',
        familyId: 3,
        relationship: 'SELF',
        initials: 'VP',
        isPrimary: true,
        active: true,
      },
    ])
  })

  it('renders server-authoritative account and self-profile fields', async () => {
    renderPage()

    await screen.findByRole('heading', { name: 'Account Settings' })
    const panel = screen.getByText('Email').closest('section')
    expect(panel).not.toBeNull()
    expect(within(panel as HTMLElement).getByText('verified@example.com')).toBeInTheDocument()
    expect(within(panel as HTMLElement).getByText('Verified Family')).toBeInTheDocument()
    expect(within(panel as HTMLElement).getByText('Primary Admin')).toBeInTheDocument()
    expect(within(panel as HTMLElement).getByText('Verified Person · Self')).toBeInTheDocument()
    expect(screen.queryByText(/Bearer|JWT|token|Authorization/i)).not.toBeInTheDocument()
  })

  it('shows a retryable error when authoritative details cannot load', async () => {
    vi.mocked(authService.getCurrentUser).mockRejectedValue(
      new Error('Account information is temporarily unavailable.'),
    )
    renderPage()

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Account information is temporarily unavailable.',
      ),
    )
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('does not call delete when the confirmation is cancelled', async () => {
    vi.mocked(window.confirm).mockReturnValue(false)
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('button', { name: 'Delete My Account' })

    await user.click(screen.getByRole('button', { name: 'Delete My Account' }))

    expect(authService.deleteOwnAccount).not.toHaveBeenCalled()
    expect(sessionValue.logout).not.toHaveBeenCalled()
  })

  it('deletes the signed-in account then signs out', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('button', { name: 'Delete My Account' })

    await user.click(screen.getByRole('button', { name: 'Delete My Account' }))

    await waitFor(() => expect(authService.deleteOwnAccount).toHaveBeenCalledTimes(1))
    expect(sessionValue.logout).toHaveBeenCalledTimes(1)
  })

  it('keeps the session when the last family admin is blocked', async () => {
    vi.mocked(authService.deleteOwnAccount).mockRejectedValue(
      new ApiError('Add another family admin before deleting this account.', 409),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('button', { name: 'Delete My Account' })

    await user.click(screen.getByRole('button', { name: 'Delete My Account' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Add another family admin before deleting this account.',
      ),
    )
    expect(sessionValue.logout).not.toHaveBeenCalled()
  })

  it('loads account settings when the user has no family circle', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    renderPage()

    await screen.findByRole('heading', { name: 'Account Settings' })
    expect(screen.getByText('None')).toBeInTheDocument()
    expect(screen.getByText('Not in a family')).toBeInTheDocument()
    expect(familyApiService.getAccountProfiles).not.toHaveBeenCalled()
  })
})

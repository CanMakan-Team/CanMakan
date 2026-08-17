import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
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
      <MemoryRouter>
        <FamilyAccountPage />
      </MemoryRouter>
    </SessionContext.Provider>,
  )
}

/** Opens the danger-zone dialog and retypes the email it asks for. */
async function confirmDeleteDialog(
  user: ReturnType<typeof userEvent.setup>,
  email = 'verified@example.com',
) {
  await user.click(await screen.findByRole('button', { name: 'Delete My Account' }))
  await user.type(
    screen.getByLabelText(`Type ${email} to confirm`),
    email,
  )
  await user.click(
    screen.getByRole('button', { name: 'Delete account permanently' }),
  )
}

describe('FamilyAccountPage', () => {
  beforeEach(() => {
    vi.mocked(sessionValue.logout).mockClear()
    vi.mocked(authService.deleteOwnAccount).mockReset()
    vi.mocked(authService.deleteOwnAccount).mockResolvedValue(undefined)
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
    const panel = screen
      .getByRole('heading', { name: 'Verified Person' })
      .closest('section')
    expect(panel).not.toBeNull()
    expect(within(panel as HTMLElement).getByText('verified@example.com')).toBeInTheDocument()
    expect(within(panel as HTMLElement).getByText('Verified Family')).toBeInTheDocument()
    const dietaryRow = within(panel as HTMLElement)
      .getByText('Dietary profile')
      .closest('li')
    expect(within(dietaryRow as HTMLElement).getByText('Verified Person')).toBeInTheDocument()
    // The family role is implied by the Manage family action, and the profile
    // status duplicates the account badge, so neither is repeated in the rows.
    expect(within(panel as HTMLElement).queryByText('Primary Admin')).not.toBeInTheDocument()
    expect(within(panel as HTMLElement).queryByText('Active')).not.toBeInTheDocument()
    expect(screen.queryByText(/Bearer|JWT|token|Authorization/i)).not.toBeInTheDocument()
  })

  it('links the family and dietary profile rows to their management screens', async () => {
    renderPage()

    await screen.findByRole('heading', { name: 'Account Settings' })
    expect(screen.getByRole('link', { name: 'Edit profile' })).toHaveAttribute(
      'href',
      '/me/setup-profile',
    )
    expect(screen.getByRole('link', { name: 'Manage family' })).toHaveAttribute(
      'href',
      '/family/members',
    )
  })

  it('offers creating a family circle when the account has none', async () => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    renderPage()

    await screen.findByRole('heading', { name: 'Account Settings' })
    expect(
      screen.getByRole('link', { name: 'Create family circle' }),
    ).toHaveAttribute('href', '/family/circle')
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

  it('does not call delete when the danger dialog is cancelled', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Delete My Account' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(authService.deleteOwnAccount).not.toHaveBeenCalled()
    expect(sessionValue.logout).not.toHaveBeenCalled()
  })

  it('keeps deletion disabled until the account email is retyped', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Delete My Account' }))
    const confirmButton = screen.getByRole('button', {
      name: 'Delete account permanently',
    })
    expect(confirmButton).toBeDisabled()

    await user.type(
      screen.getByLabelText('Type verified@example.com to confirm'),
      'someone-else@example.com',
    )
    expect(confirmButton).toBeDisabled()

    await user.clear(screen.getByLabelText('Type verified@example.com to confirm'))
    await user.type(
      screen.getByLabelText('Type verified@example.com to confirm'),
      'verified@example.com',
    )
    expect(confirmButton).toBeEnabled()
  })

  it('deletes the signed-in account then signs out', async () => {
    const user = userEvent.setup()
    renderPage()

    await confirmDeleteDialog(user)

    await waitFor(() => expect(authService.deleteOwnAccount).toHaveBeenCalledTimes(1))
    expect(sessionValue.logout).toHaveBeenCalledTimes(1)
  })

  it('keeps the session when the last family admin is blocked', async () => {
    vi.mocked(authService.deleteOwnAccount).mockRejectedValue(
      new ApiError('Add another family admin before deleting this account.', 409),
    )
    const user = userEvent.setup()
    renderPage()

    await confirmDeleteDialog(user)

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

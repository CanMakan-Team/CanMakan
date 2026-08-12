import { render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FamilyAccountPage } from '../../../features/family/pages/FamilyAccountPage'
import { authService } from '../../../features/auth/authService'
import { familyApiService } from '../../../features/family/api/familyApiService'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import { familyAdminSession } from '../../testUtils'

vi.mock('../../../features/auth/authService', () => ({
  authService: { getCurrentUser: vi.fn() },
}))

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamily: vi.fn(),
    getAccountProfiles: vi.fn(),
  },
}))

const sessionValue: SessionContextValue = {
  session: familyAdminSession(),
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
  logout: async () => undefined,
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
    vi.mocked(authService.getCurrentUser).mockResolvedValue({
      userId: 14,
      email: 'verified@example.com',
      role: 'USER',
      active: true,
    })
    vi.mocked(familyApiService.getMyFamily).mockResolvedValue({
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
    expect(within(panel as HTMLElement).getByText('Verified Person')).toBeInTheDocument()
    expect(within(panel as HTMLElement).getByText('Self')).toBeInTheDocument()
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
})

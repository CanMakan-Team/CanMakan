import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PersonalHomePage } from '../../../features/account/pages/PersonalHomePage'
import { FamilyMeProvider } from '../../../features/family/FamilyMeContext'
import { familyApiService } from '../../../features/family/api/familyApiService'
import { selfProfileApiService } from '../../../features/account/api/selfProfileApiService'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import { ApiError } from '../../../shared/api/apiErrors'
import { appUserSession } from '../../testUtils'

vi.mock('../../../features/family/api/familyApiService', () => ({
  familyApiService: {
    getMyFamilyOrNull: vi.fn(),
    getMembers: vi.fn(),
  },
}))

vi.mock('../../../features/account/api/selfProfileApiService', () => ({
  selfProfileApiService: {
    getCatalog: vi.fn(),
    getSelfProfile: vi.fn(),
    getScanHistoryForProfile: vi.fn(),
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

function renderHome() {
  return render(
    <SessionContext.Provider value={sessionValue}>
      <FamilyMeProvider>
        <MemoryRouter>
          <PersonalHomePage />
        </MemoryRouter>
      </FamilyMeProvider>
    </SessionContext.Provider>,
  )
}

describe('PersonalHomePage', () => {
  beforeEach(() => {
    vi.mocked(familyApiService.getMyFamilyOrNull).mockReset()
    vi.mocked(familyApiService.getMembers).mockReset()
    vi.mocked(selfProfileApiService.getCatalog).mockReset()
    vi.mocked(selfProfileApiService.getSelfProfile).mockReset()
    vi.mocked(selfProfileApiService.getScanHistoryForProfile).mockReset()
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue(null)
    vi.mocked(familyApiService.getMembers).mockResolvedValue([])
    vi.mocked(selfProfileApiService.getCatalog).mockResolvedValue([
      { id: 2, code: 'DAIRY', displayName: 'Dairy', category: 'ALLERGEN' },
      { id: 8, code: 'SHELLFISH', displayName: 'Shellfish', category: 'ALLERGEN' },
      { id: 1, code: 'HALAL', displayName: 'Halal', category: 'RELIGIOUS' },
    ])
    vi.mocked(selfProfileApiService.getSelfProfile).mockRejectedValue(
      new ApiError('No SELF profile exists for this account yet.', 404),
    )
    vi.mocked(selfProfileApiService.getScanHistoryForProfile).mockResolvedValue([])
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('shows actionable account, profile, and family cards when setup is incomplete', async () => {
    renderHome()

    expect(await screen.findByText('person@example.com')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Manage settings' })).toHaveAttribute(
      'href',
      '/me/account',
    )
    expect(screen.getByRole('link', { name: 'Set Up Profile' })).toHaveAttribute(
      'href',
      '/me/setup-profile',
    )
    expect(await screen.findByRole('link', { name: 'Create Circle' })).toHaveAttribute(
      'href',
      '/family/circle',
    )
    expect(screen.getByRole('heading', { name: 'Get CanMakan on Mobile' })).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Open Firebase App Distribution' }),
    ).toHaveAttribute('href', 'https://appdistribution.firebase.google.com/')
    expect(
      screen.getByRole('link', { name: 'QR code for Firebase App Distribution' }),
    ).toHaveAttribute('href', 'https://appdistribution.firebase.google.com/')
    expect(
      screen.getByRole('heading', { name: 'Account setup: 1/3 complete' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/tester build/i)).toBeInTheDocument()
    expect(screen.getByRole('article', { name: 'Example scan result' })).toBeInTheDocument()
  })

  it('hides the tester App Distribution notice after dismiss', async () => {
    const user = userEvent.setup()
    renderHome()

    expect(await screen.findByText(/tester build/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByText(/tester build/i)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Get CanMakan on Mobile' })).toBeInTheDocument()
  })

  it('uses VITE_FIREBASE_APP_DISTRIBUTION_URL for the mobile banner links', async () => {
    vi.stubEnv(
      'VITE_FIREBASE_APP_DISTRIBUTION_URL',
      'https://appdistribution.firebase.google.com/pub/testerapps/custom',
    )
    renderHome()

    expect(
      await screen.findByRole('link', { name: 'Open Firebase App Distribution' }),
    ).toHaveAttribute(
      'href',
      'https://appdistribution.firebase.google.com/pub/testerapps/custom',
    )
    expect(
      screen.getByRole('link', { name: 'QR code for Firebase App Distribution' }),
    ).toHaveAttribute(
      'href',
      'https://appdistribution.firebase.google.com/pub/testerapps/custom',
    )
  })

  it('summarises a saved dietary profile and family admin circle', async () => {
    vi.mocked(selfProfileApiService.getSelfProfile).mockResolvedValue({
      profileId: 77,
      profileName: 'Lia',
      relationship: 'SELF',
      active: true,
      restrictions: { '2': 'STRICT_AVOID', '8': 'STRICT_AVOID', '1': 'INTOLERANCE' },
    })
    vi.mocked(familyApiService.getMyFamilyOrNull).mockResolvedValue({
      familyId: 3,
      familyName: 'Wong Family',
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: 77,
      createdByUserId: 14,
    })
    vi.mocked(familyApiService.getMembers).mockResolvedValue([
      { profileId: 77 },
      { profileId: 78 },
      { profileId: 79 },
      { profileId: 80 },
    ] as never)
    vi.mocked(selfProfileApiService.getScanHistoryForProfile).mockResolvedValue([
      {
        id: 9,
        scannedAt: '2026-08-01T10:00:00',
        verdict: 'SAFE',
        product: { productName: 'Oat Drink', brand: 'Brand', barcode: '1' },
      },
    ])

    renderHome()

    expect(await screen.findByText(/3 active restrictions/i)).toBeInTheDocument()
    expect(screen.getByText(/Halal, Dairy, Shellfish/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit profile' })).toHaveAttribute(
      'href',
      '/me/setup-profile',
    )
    expect(await screen.findByText('4 household members')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View circle' })).toHaveAttribute(
      'href',
      '/family/dashboard',
    )
    expect(
      screen.getByRole('heading', { name: 'Account setup: 3/3 complete' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Oat Drink')).toBeInTheDocument()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { CANMAKAN_MASCOT_POSES, CanMakanMascot } from '../../../shared/ui/CanMakanMascot'
import { EmptyState } from '../../../shared/ui/PageState'
import { UserLoginPage } from '../../../pages/UserLoginPage'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { authService } from '../../../features/auth/authService'
import { ApiError } from '../../../shared/api/apiErrors'

vi.mock('../../../features/auth/authService', () => ({
  authService: {
    loginWithCredentials: vi.fn(),
    register: vi.fn(),
    refreshSession: vi.fn(),
    getCurrentUser: vi.fn(),
    synchronizeCurrentUser: vi.fn(),
    logout: vi.fn(),
  },
}))

describe('CanMakanMascot', () => {
  beforeEach(() => {
    vi.mocked(authService.refreshSession).mockReset()
    vi.mocked(authService.refreshSession).mockRejectedValue(
      new ApiError('Authentication required.', 401),
    )
  })
  it('renders the requested pose asset', () => {
    render(<CanMakanMascot pose="wave" />)

    expect(screen.getByAltText('CanMakan mascot')).toHaveAttribute(
      'src',
      CANMAKAN_MASCOT_POSES.wave,
    )
  })

  it('uses the warning pose when requested', () => {
    render(<CanMakanMascot pose="warning" alt="Warning mascot" />)

    expect(screen.getByAltText('Warning mascot')).toHaveAttribute(
      'src',
      CANMAKAN_MASCOT_POSES.warning,
    )
  })

  it('shows a waving mascot in user-portal empty states', () => {
    render(<EmptyState title="Nothing here" description="Add an item to continue." />)

    expect(screen.getByAltText('CanMakan mascot')).toHaveAttribute(
      'src',
      CANMAKAN_MASCOT_POSES.wave,
    )
    expect(screen.getByText('Nothing here')).toBeInTheDocument()
  })

  it('omits the mascot from system-admin empty states', () => {
    render(
      <EmptyState
        title="No accounts match"
        description="Change the filters and try again."
        showMascot={false}
      />,
    )

    expect(screen.queryByAltText('CanMakan mascot')).not.toBeInTheDocument()
    expect(screen.getByText('No accounts match')).toBeInTheDocument()
  })

  it('greets the user login page with a waving mascot', () => {
    render(
      <SessionProvider>
        <MemoryRouter>
          <UserLoginPage />
        </MemoryRouter>
      </SessionProvider>,
    )

    expect(screen.getAllByAltText('CanMakan mascot')).toHaveLength(1)
    expect(screen.getByAltText('CanMakan mascot')).toHaveAttribute(
      'src',
      CANMAKAN_MASCOT_POSES.wave,
    )
  })
})

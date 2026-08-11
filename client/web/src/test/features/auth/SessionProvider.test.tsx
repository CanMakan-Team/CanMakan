import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { useSession } from '../../../features/auth/useSession'
import { authService } from '../../../features/auth/authService'
import { SESSION_KEY } from '../../testUtils'

/** Test suite for SessionProvider.
 * 
 * @author Amelia
 */

vi.mock('../../../features/auth/authService', () => ({
  authService: {
    loginWithCredentials: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  },
}))

function SessionProbe() {
  const { session, loading, loginWithCredentials, register, logout } =
    useSession()

  return (
    <div>
      <p data-testid="loading">{loading ? 'yes' : 'no'}</p>
      <p data-testid="session">{session ? session.displayName : 'none'}</p>
      <button
        type="button"
        onClick={() =>
          void loginWithCredentials({
            email: 'person@example.com',
            password: 'Password1!',
            portal: 'FAMILY',
          })
        }
      >
        Login
      </button>
      <button
        type="button"
        onClick={() =>
          void register({
            name: 'Person Name',
            email: 'person@example.com',
            password: 'Password1!',
          })
        }
      >
        Register
      </button>
      <button type="button" onClick={() => logout()}>
        Logout
      </button>
    </div>
  )
}

describe('SessionProvider', () => {
  beforeEach(() => {
    vi.mocked(authService.loginWithCredentials).mockReset()
    vi.mocked(authService.register).mockReset()
    vi.mocked(authService.logout).mockReset()
  })

  it('persists login session to localStorage', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue({
      accessToken: 'jwt',
      userId: 14,
      displayName: 'person',
      roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
      portal: 'FAMILY',
      prototype: false,
    })

    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Login' }))

    await waitFor(() => {
      expect(screen.getByTestId('session')).toHaveTextContent('person')
    })
    expect(JSON.parse(localStorage.getItem(SESSION_KEY) ?? '{}').accessToken).toBe(
      'jwt',
    )
  })

  it('registers without logging in or storing a session', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      profileId: 77,
      name: 'Person Name',
      email: 'person@example.com',
      active: true,
    })
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Register' }))

    await waitFor(() => {
      expect(authService.register).toHaveBeenCalledWith({
        name: 'Person Name',
        email: 'person@example.com',
        password: 'Password1!',
      })
      expect(authService.loginWithCredentials).not.toHaveBeenCalled()
      expect(screen.getByTestId('session')).toHaveTextContent('none')
      expect(localStorage.getItem(SESSION_KEY)).toBeNull()
    })
  })

  it('logout clears local session even if remote logout fails', async () => {
    const user = userEvent.setup()
    localStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        accessToken: 'jwt',
        userId: 14,
        displayName: 'person',
        roles: ['ROLE_FAMILY_ADMIN'],
        portal: 'FAMILY',
        prototype: false,
      }),
    )
    vi.mocked(authService.logout).mockRejectedValue(new Error('offline'))

    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    expect(screen.getByTestId('session')).toHaveTextContent('person')
    await user.click(screen.getByRole('button', { name: 'Logout' }))

    await waitFor(() => {
      expect(screen.getByTestId('session')).toHaveTextContent('none')
    })
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('clears session on canmakan:unauthorised', async () => {
    localStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        accessToken: 'jwt',
        userId: 14,
        displayName: 'person',
        roles: ['ROLE_FAMILY_ADMIN'],
        portal: 'FAMILY',
        prototype: false,
      }),
    )

    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    expect(screen.getByTestId('session')).toHaveTextContent('person')
    window.dispatchEvent(new Event('canmakan:unauthorised'))

    await waitFor(() => {
      expect(screen.getByTestId('session')).toHaveTextContent('none')
    })
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../../../shared/api/apiErrors'
import { apiRequest } from '../../../shared/api/apiClient'
import { SessionProvider } from '../../../features/auth/SessionProvider'
import { useSession } from '../../../features/auth/useSession'
import { authService } from '../../../features/auth/authService'
import { authSessionStore } from '../../../features/auth/authSessionStore'
import { familyAdminSession, SESSION_KEY } from '../../testUtils'
import { jsonResponse } from '../../testUtils'

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

function SessionProbe() {
  const {
    session,
    loading,
    restoring,
    restorationError,
    retryRestoration,
    loginWithCredentials,
    register,
    logout,
  } = useSession()

  return (
    <div>
      <p data-testid="loading">{loading ? 'yes' : 'no'}</p>
      <p data-testid="restoring">{restoring ? 'yes' : 'no'}</p>
      <p data-testid="session">{session ? session.displayName : 'none'}</p>
      <p data-testid="restore-error">{restorationError || 'none'}</p>
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
            email: 'person@example.com',
            password: 'Password1!',
          })
        }
      >
        Register
      </button>
      <button type="button" onClick={() => void logout()}>
        Logout
      </button>
      <button type="button" onClick={retryRestoration}>
        Retry restoration
      </button>
    </div>
  )
}

const currentUser = {
  userId: 14,
  email: 'person@example.com',
  role: 'USER' as const,
  active: true,
}

describe('SessionProvider', () => {
  beforeEach(() => {
    authSessionStore.clear()
    vi.mocked(authService.loginWithCredentials).mockReset()
    vi.mocked(authService.register).mockReset()
    vi.mocked(authService.refreshSession).mockReset()
    vi.mocked(authService.getCurrentUser).mockReset()
    vi.mocked(authService.synchronizeCurrentUser).mockReset()
    vi.mocked(authService.logout).mockReset()
    vi.mocked(authService.refreshSession).mockRejectedValue(
      new ApiError('Authentication required.', 401),
    )
    vi.mocked(authService.getCurrentUser).mockResolvedValue(currentUser)
    vi.mocked(authService.synchronizeCurrentUser).mockImplementation(
      (session) => session,
    )
    vi.mocked(authService.logout).mockResolvedValue(undefined)
  })

  it('restores a verified account from the refresh cookie', async () => {
    vi.mocked(authService.refreshSession).mockResolvedValue(familyAdminSession())

    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('restoring')).toHaveTextContent('no')
      expect(screen.getByTestId('session')).toHaveTextContent('person')
    })
    expect(authService.getCurrentUser).toHaveBeenCalledWith()
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('keeps a successful login in memory instead of localStorage', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.loginWithCredentials).mockResolvedValue(
      familyAdminSession(),
    )
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )
    await waitFor(() =>
      expect(screen.getByTestId('restoring')).toHaveTextContent('no'),
    )

    await user.click(screen.getByRole('button', { name: 'Login' }))

    await waitFor(() =>
      expect(screen.getByTestId('session')).toHaveTextContent('person'),
    )
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
    expect(authSessionStore.getAccessToken()).toBe('test-access-token')
  })

  it('shares one refresh across concurrent protected 401 responses', async () => {
    const expired = { ...familyAdminSession(), accessToken: 'expired-token' }
    const rotated = { ...familyAdminSession(), accessToken: 'rotated-token' }
    authSessionStore.replace(expired)
    vi.mocked(authService.refreshSession).mockResolvedValue(rotated)
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(401, { message: 'Expired.' }))
        .mockResolvedValueOnce(jsonResponse(401, { message: 'Expired.' }))
        .mockResolvedValueOnce(jsonResponse(200, { request: 1 }))
        .mockResolvedValueOnce(jsonResponse(200, { request: 2 })),
    )

    await expect(
      Promise.all([
        apiRequest<{ request: number }>('/api/first'),
        apiRequest<{ request: number }>('/api/second'),
      ]),
    ).resolves.toEqual([{ request: 1 }, { request: 2 }])

    expect(authService.refreshSession).toHaveBeenCalledTimes(1)
    expect(authSessionStore.getAccessToken()).toBe('rotated-token')
  })

  it('registers without logging in or creating a local session', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValue({
      userId: 14,
      email: 'person@example.com',
      active: true,
    })
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )
    await waitFor(() =>
      expect(screen.getByTestId('restoring')).toHaveTextContent('no'),
    )

    await user.click(screen.getByRole('button', { name: 'Register' }))

    await waitFor(() => {
      expect(authService.register).toHaveBeenCalled()
      expect(screen.getByTestId('session')).toHaveTextContent('none')
    })
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('shows a retryable restoration error without a blank page', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.refreshSession)
      .mockRejectedValueOnce(new Error('Service temporarily unavailable.'))
      .mockResolvedValueOnce(familyAdminSession())
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    await waitFor(() =>
      expect(screen.getByTestId('restore-error')).toHaveTextContent(
        'Service temporarily unavailable.',
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Retry restoration' }))

    await waitFor(() => {
      expect(screen.getByTestId('session')).toHaveTextContent('person')
      expect(screen.getByTestId('restore-error')).toHaveTextContent('none')
    })
  })

  it('does not trust a legacy localStorage access token', async () => {
    localStorage.setItem(SESSION_KEY, JSON.stringify(familyAdminSession()))
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )

    await waitFor(() =>
      expect(screen.getByTestId('restoring')).toHaveTextContent('no'),
    )
    expect(screen.getByTestId('session')).toHaveTextContent('none')
    expect(localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('clears local session even if remote logout fails', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.refreshSession).mockResolvedValue(familyAdminSession())
    vi.mocked(authService.logout).mockRejectedValue(new Error('offline'))
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )
    await waitFor(() =>
      expect(screen.getByTestId('session')).toHaveTextContent('person'),
    )

    await user.click(screen.getByRole('button', { name: 'Logout' }))

    await waitFor(() =>
      expect(screen.getByTestId('session')).toHaveTextContent('none'),
    )
    expect(authSessionStore.getAccessToken()).toBeNull()
  })

  it('clears stale identity when another tab logs out or changes account', async () => {
    vi.mocked(authService.refreshSession).mockResolvedValue(familyAdminSession())
    authSessionStore.replace(familyAdminSession())
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )
    await waitFor(() => {
      expect(screen.getByTestId('restoring')).toHaveTextContent('no')
      expect(screen.getByTestId('session')).toHaveTextContent('person')
    })

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'canmakan.session-event',
        newValue: JSON.stringify({
          type: 'account-change',
          generation: 1,
          timestamp: 1,
          coordinationId: 'test-event',
        }),
      }),
    )

    await waitFor(() =>
      expect(screen.getByTestId('session')).toHaveTextContent('none'),
    )
    expect(authSessionStore.getAccessToken()).toBeNull()
    expect(localStorage.getItem('canmakan.session-event')).toBeNull()
  })

  it('does not install a refresh response made stale by another-tab logout', async () => {
    let resolveRefresh: ((session: ReturnType<typeof familyAdminSession>) => void) | undefined
    vi.mocked(authService.refreshSession).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRefresh = resolve
        }),
    )

    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    )
    await waitFor(() => expect(authService.refreshSession).toHaveBeenCalledTimes(1))

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'canmakan.session-event',
        newValue: JSON.stringify({
          type: 'logout',
          generation: 2,
          timestamp: 2,
          coordinationId: 'other-tab-logout',
        }),
      }),
    )
    resolveRefresh?.(familyAdminSession())

    await waitFor(() =>
      expect(screen.getByTestId('restoring')).toHaveTextContent('no'),
    )
    expect(screen.getByTestId('session')).toHaveTextContent('none')
    expect(authService.getCurrentUser).not.toHaveBeenCalled()
  })

  it('publishes only credential-free cross-tab coordination metadata', async () => {
    const storageSet = vi.spyOn(Storage.prototype, 'setItem')

    authSessionStore.replace(familyAdminSession(), true)

    const eventWrite = storageSet.mock.calls.find(
      ([key]) => key === 'canmakan.session-event',
    )
    expect(eventWrite).toBeDefined()
    expect(eventWrite?.[1]).not.toContain('test-access-token')
    expect(eventWrite?.[1]).not.toContain('person@example.com')
    expect(JSON.parse(eventWrite?.[1] ?? '{}')).toEqual(
      expect.objectContaining({
        type: 'account-change',
        generation: expect.any(Number),
        timestamp: expect.any(Number),
        coordinationId: expect.any(String),
      }),
    )
  })
})

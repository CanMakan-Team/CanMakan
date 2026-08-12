import {
  useCallback,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { ApiError, getErrorMessage } from '../../shared/api/apiErrors'
import { configureApiAuthBridge } from '../../shared/api/apiClient'
import type { AuthenticatedSession } from '../../shared/api/types'
import { authService } from './authService'
import type { CredentialLoginInput, RegisterInput } from './authService'
import { authSessionStore } from './authSessionStore'
import { SessionContext } from './SessionContext'
import { withSessionMutationLock } from './sessionMutationCoordinator'

let refreshInFlight: Promise<AuthenticatedSession | null> | null = null

/**
 * Rotates the refresh cookie at most once for all concurrent callers. The
 * generation check prevents a late refresh response from signing the user back
 * in after logout or an account change.
 */
function refreshSessionSingleFlight(): Promise<AuthenticatedSession | null> {
  if (refreshInFlight) return refreshInFlight

  const startingGeneration = authSessionStore.getGeneration()
  const request = withSessionMutationLock('refresh', async () => {
    if (authSessionStore.getGeneration() !== startingGeneration) return null
    const session = await authService.refreshSession()
    if (authSessionStore.getGeneration() !== startingGeneration) return null
    authSessionStore.replace(session)
    return session
  })
    .catch((error: unknown) => {
      if (
        error instanceof ApiError &&
        error.status === 401 &&
        authSessionStore.getGeneration() === startingGeneration
      ) {
        authSessionStore.clear()
      }
      throw error
    })
    .finally(() => {
      if (refreshInFlight === request) refreshInFlight = null
    })

  refreshInFlight = request
  return request
}

configureApiAuthBridge({
  getAccessToken: authSessionStore.getAccessToken,
  refreshAccessToken: async () =>
    (await refreshSessionSingleFlight())?.accessToken ?? null,
  invalidate: authSessionStore.clear,
})

/** Owns the browser's in-memory access session and refresh-cookie lifecycle. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthenticatedSession | null>(() =>
    authSessionStore.getSession(),
  )
  const [loading, setLoading] = useState(false)
  const [restoring, setRestoring] = useState(true)
  const [restorationError, setRestorationError] = useState('')
  const [restorationAttempt, setRestorationAttempt] = useState(0)

  useEffect(() => authSessionStore.subscribe(setSession), [])

  useEffect(() => {
    let active = true

    const restore = async () => {
      setRestoring(true)
      setRestorationError('')
      try {
        const refreshed = await refreshSessionSingleFlight()
        if (!active || !refreshed) return

        const currentUser = await authService.getCurrentUser()
        if (!active) return

        const currentSession = authSessionStore.getSession()
        if (currentSession?.accessToken === refreshed.accessToken) {
          authSessionStore.replace(
            authService.synchronizeCurrentUser(currentSession, currentUser),
          )
        }
      } catch (error) {
        if (!active) return
        authSessionStore.clear()
        if (!(error instanceof ApiError && error.status === 401)) {
          setRestorationError(getErrorMessage(error))
        }
      } finally {
        if (active) setRestoring(false)
      }
    }

    void restore()
    return () => {
      active = false
    }
  }, [restorationAttempt])

  const retryRestoration = useCallback(() => {
    setRestorationAttempt((attempt) => attempt + 1)
  }, [])

  const loginWithCredentials = async (input: CredentialLoginInput) => {
    setLoading(true)
    try {
      return await withSessionMutationLock('login', async () => {
        const authenticatedSession = await authService.loginWithCredentials(input)
        authSessionStore.replace(authenticatedSession, true)
        setRestorationError('')
        return authenticatedSession
      })
    } finally {
      setLoading(false)
    }
  }

  const register = async (input: RegisterInput) => {
    setLoading(true)
    try {
      return await authService.register(input)
    } finally {
      setLoading(false)
    }
  }

  const logout = async () => {
    authSessionStore.clear(true)
    try {
      await refreshInFlight
    } catch {
      // Remote cleanup below still runs when an overlapping refresh failed.
    }
    await withSessionMutationLock('logout', async () => {
      authSessionStore.clear(true)
      try {
        await authService.logout()
      } catch {
        // Local logout remains authoritative when remote cleanup is unavailable.
      } finally {
        authSessionStore.clear(true)
      }
    })
  }

  return (
    <SessionContext.Provider
      value={{
        session,
        loading,
        restoring,
        restorationError,
        retryRestoration,
        loginWithCredentials,
        register,
        logout,
      }}
    >
      {children}
    </SessionContext.Provider>
  )
}

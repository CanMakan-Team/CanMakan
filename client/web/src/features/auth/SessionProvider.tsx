import { useEffect, useState, type ReactNode } from 'react'
import { authService } from './authService'
import type { RegisterInput, CredentialLoginInput } from './authService'
import type { AuthenticatedSession } from '../../shared/api/types'
import { SessionContext } from './SessionContext'

const sessionKey = 'canmakan.session'

/** Session provider
 * 
 * @author Amelia
 * @author YangMaowei
 */

// Persist session to localStorage
function persistSession(authenticatedSession: AuthenticatedSession) {
  localStorage.setItem(sessionKey, JSON.stringify(authenticatedSession))
}

// Provide session context to children
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthenticatedSession | null>(() => {
    const stored = localStorage.getItem(sessionKey)
    return stored ? (JSON.parse(stored) as AuthenticatedSession) : null
  })

  // Set loading state
  const [loading, setLoading] = useState(false)

  // Handle unauthorised event
  useEffect(() => {
    const handleUnauthorised = () => setSession(null)
    window.addEventListener('canmakan:unauthorised', handleUnauthorised)
    return () =>
      window.removeEventListener('canmakan:unauthorised', handleUnauthorised)
  }, [])

  // Login with credentials
  const loginWithCredentials = async (input: CredentialLoginInput) => {
    setLoading(true)
    try {
      const authenticatedSession = await authService.loginWithCredentials(input)
      persistSession(authenticatedSession)
      setSession(authenticatedSession)
      return authenticatedSession
    } finally {
      setLoading(false)
    }
  }

  // Register and login
  const registerAndLogin = async (input: RegisterInput) => {
    setLoading(true)
    try {
      await authService.register(input)
      const authenticatedSession = await authService.loginWithCredentials({
        email: input.email,
        password: input.password,
        portal: 'FAMILY',
      })
      persistSession(authenticatedSession)
      setSession(authenticatedSession)
      return authenticatedSession
    } finally {
      setLoading(false)
    }
  }

  // Logout
  const logout = () => {
    void authService.logout()
    localStorage.removeItem(sessionKey)
    setSession(null)
  }

  return (
    <SessionContext.Provider
      value={{
        session,
        loading,
        loginWithCredentials,
        registerAndLogin,
        logout,
      }}
    >
      {children}
    </SessionContext.Provider>
  )
}

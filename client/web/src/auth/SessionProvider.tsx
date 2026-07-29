import { useEffect, useState, type ReactNode } from 'react'
import { authService } from '../api/authService'
import type { AuthenticatedSession, Portal } from '../api/types'
import { SessionContext } from './SessionContext'

const sessionKey = 'canmakan.session'

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthenticatedSession | null>(() => {
    const stored = localStorage.getItem(sessionKey)
    return stored ? (JSON.parse(stored) as AuthenticatedSession) : null
  })
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const handleUnauthorised = () => setSession(null)
    window.addEventListener('canmakan:unauthorised', handleUnauthorised)
    return () =>
      window.removeEventListener('canmakan:unauthorised', handleUnauthorised)
  }, [])

  const login = async (portal: Portal) => {
    setLoading(true)
    try {
      const authenticatedSession = await authService.login(portal)
      localStorage.setItem(sessionKey, JSON.stringify(authenticatedSession))
      setSession(authenticatedSession)
      return authenticatedSession
    } finally {
      setLoading(false)
    }
  }

  const logout = () => {
    localStorage.removeItem(sessionKey)
    setSession(null)
  }

  return (
    <SessionContext.Provider value={{ session, loading, login, logout }}>
      {children}
    </SessionContext.Provider>
  )
}

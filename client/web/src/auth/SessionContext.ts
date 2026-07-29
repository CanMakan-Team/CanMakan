import { createContext } from 'react'
import type { AuthenticatedSession, Portal } from '../api/types'

export interface SessionContextValue {
  session: AuthenticatedSession | null
  loading: boolean
  login: (portal: Portal) => Promise<AuthenticatedSession>
  logout: () => void
}

export const SessionContext = createContext<SessionContextValue | null>(null)

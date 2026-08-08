import { createContext } from 'react'
import type { AuthenticatedSession } from '../../shared/api/types'
import type { CredentialLoginInput, RegisterInput } from './authService'

/** Session context value
 * 
 * @author Amelia
 */
export interface SessionContextValue {
  session: AuthenticatedSession | null
  loading: boolean
  loginWithCredentials: (
    input: CredentialLoginInput,
  ) => Promise<AuthenticatedSession>
  registerAndLogin: (input: RegisterInput) => Promise<AuthenticatedSession>
  logout: () => void
}

export const SessionContext = createContext<SessionContextValue | null>(null)

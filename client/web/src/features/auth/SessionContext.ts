import { createContext } from 'react'
import type {
  AuthenticatedSession,
  RegistrationResponse,
} from '../../shared/api/types'
import type { CredentialLoginInput, RegisterInput } from './api/authService'

export type RegisterAndLoginResult =
  | {
      status: 'authenticated'
      account: RegistrationResponse
      session: AuthenticatedSession
    }
  | {
      status: 'account-created-login-failed'
      account: RegistrationResponse
    }

/** Session context value
 * 
 */
export interface SessionContextValue {
  session: AuthenticatedSession | null
  loading: boolean
  restoring: boolean
  restorationError: string
  retryRestoration: () => void
  loginWithCredentials: (
    input: CredentialLoginInput,
  ) => Promise<AuthenticatedSession>
  register: (input: RegisterInput) => Promise<RegistrationResponse>
  registerAndLogin: (input: RegisterInput) => Promise<RegisterAndLoginResult>
  logout: () => Promise<void>
}

export const SessionContext = createContext<SessionContextValue | null>(null)

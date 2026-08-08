import { apiRequest } from '../../shared/api/apiClient'
import type {
  AuthenticatedSession,
  LoginResponse,
  Portal,
  RegistrationResponse,
  Role,
} from '../../shared/api/types'

/** Live auth against Spring Boot (pre-JWT; session drives X-User-Id). 
 * 
 * @author Amelia
*/
export const authEndpoints = {
  login: '/api/auth/login',
  register: '/api/auth/register',
} as const

export type RegisterInput = {
  name: string
  email: string
  password: string
}

export type CredentialLoginInput = {
  email: string
  password: string
  portal: Portal
}

function toSession(
  response: LoginResponse,
  portal: Portal,
): AuthenticatedSession {
  return {
    userId: response.userId,
    displayName: response.displayName,
    roles: response.roles as Role[],
    portal,
    prototype: false,
  }
}

export const authService = {
  register(input: RegisterInput): Promise<RegistrationResponse> {
    return apiRequest<RegistrationResponse>(authEndpoints.register, {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },

  loginWithCredentials(
    input: CredentialLoginInput,
  ): Promise<AuthenticatedSession> {
    return apiRequest<LoginResponse>(authEndpoints.login, {
      method: 'POST',
      body: JSON.stringify({
        email: input.email,
        password: input.password,
      }),
    }).then((response) => toSession(response, input.portal))
  },
}

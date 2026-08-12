import { apiRequest } from '../../shared/api/apiClient'
import type {
  AuthenticatedSession,
  AuthLoginResponse,
  Portal,
  RegistrationResponse,
  Role,
} from '../../shared/api/types'

/** Live auth against Spring Boot UC19 JWT login. */
export const authEndpoints = {
  login: '/api/auth/login',
  register: '/api/auth/register',
  logout: '/api/auth/logout',
} as const

export type RegisterInput = {
  email: string
  password: string
}

export type CredentialLoginInput = {
  email: string
  password: string
  portal: Portal
}

function mapSystemRoleToPortalRoles(role: 'USER' | 'ADMIN'): Role[] {
  if (role === 'ADMIN') {
    return ['ROLE_SYSTEM_ADMIN']
  }
  // Family portal access for normal accounts (membership PRIMARY_ADMIN is separate).
  return ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN']
}

function displayNameFromEmail(email: string): string {
  const at = email.indexOf('@')
  return at > 0 ? email.slice(0, at) : email
}

function toSession(
  response: AuthLoginResponse,
  portal: Portal,
): AuthenticatedSession {
  return {
    accessToken: response.accessToken,
    userId: response.user.userId,
    displayName: displayNameFromEmail(response.user.email),
    roles: mapSystemRoleToPortalRoles(response.user.role),
    portal,
    prototype: false,
  }
}

export const authService = {
  register(input: RegisterInput): Promise<RegistrationResponse> {
    return apiRequest<RegistrationResponse>(authEndpoints.register, {
      method: 'POST',
      body: JSON.stringify({
        email: input.email,
        password: input.password,
      }),
    })
  },

  loginWithCredentials(
    input: CredentialLoginInput,
  ): Promise<AuthenticatedSession> {
    return apiRequest<AuthLoginResponse>(authEndpoints.login, {
      method: 'POST',
      body: JSON.stringify({
        email: input.email,
        password: input.password,
      }),
    }).then((response) => toSession(response, input.portal))
  },

  async logout(): Promise<void> {
    try {
      await apiRequest<void>(authEndpoints.logout, { method: 'POST' })
    } catch {
      // Local session clear remains authoritative for the browser.
    }
  },
}

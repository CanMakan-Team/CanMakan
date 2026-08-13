import { ApiError } from '../../shared/api/apiErrors'
import { apiRequest } from '../../shared/api/apiClient'
import type {
  AuthenticatedSession,
  AuthLoginResponse,
  CurrentUserResponse,
  Portal,
  RegistrationResponse,
  Role,
} from '../../shared/api/types'

export const authEndpoints = {
  login: '/api/auth/login',
  register: '/api/auth/register',
  refresh: '/api/auth/refresh',
  logout: '/api/auth/logout',
  me: '/api/auth/me',
} as const

export type RegisterInput = { email: string; password: string }
export type CredentialLoginInput = {
  email: string
  password: string
  portal: Portal
}

function mapSystemRoleToPortalRoles(role: 'USER' | 'ADMIN'): Role[] {
  return role === 'ADMIN'
    ? ['ROLE_SYSTEM_ADMIN']
    : ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN']
}

function displayNameFromEmail(email: string): string {
  const at = email.indexOf('@')
  return at > 0 ? email.slice(0, at) : email
}

function validateUser(user: CurrentUserResponse): CurrentUserResponse {
  if (
    !Number.isSafeInteger(user?.userId) ||
    user.userId <= 0 ||
    typeof user.email !== 'string' ||
    !user.email.trim() ||
    (user.role !== 'USER' && user.role !== 'ADMIN') ||
    user.active !== true
  ) {
    throw new ApiError('Your session could not be verified. Please sign in again.', 401)
  }
  return user
}

function toSession(response: AuthLoginResponse): AuthenticatedSession {
  if (
    typeof response?.accessToken !== 'string' ||
    !response.accessToken.trim() ||
    response.tokenType?.toLowerCase() !== 'bearer' ||
    !Number.isFinite(response.expiresIn) ||
    response.expiresIn <= 0
  ) {
    throw new ApiError('Sign-in could not be completed. Please try again.')
  }
  const user = validateUser(response.user)
  return {
    accessToken: response.accessToken,
    userId: user.userId,
    email: user.email,
    active: user.active,
    displayName: displayNameFromEmail(user.email),
    roles: mapSystemRoleToPortalRoles(user.role),
    portal: user.role === 'ADMIN' ? 'SYSTEM' : 'FAMILY',
    prototype: false,
  }
}

export const authService = {
  register(input: RegisterInput): Promise<RegistrationResponse> {
    return apiRequest<RegistrationResponse>(authEndpoints.register, {
      method: 'POST',
      body: JSON.stringify(input),
      authentication: 'none',
      retryAuthentication: false,
    })
  },

  async loginWithCredentials(
    input: CredentialLoginInput,
  ): Promise<AuthenticatedSession> {
    const response = await apiRequest<AuthLoginResponse>(authEndpoints.login, {
      method: 'POST',
      body: JSON.stringify({ email: input.email, password: input.password }),
      authentication: 'none',
      retryAuthentication: false,
      sessionMutation: true,
    })
    return toSession(response)
  },

  async refreshSession(): Promise<AuthenticatedSession> {
    const response = await apiRequest<AuthLoginResponse>(authEndpoints.refresh, {
      method: 'POST',
      authentication: 'none',
      retryAuthentication: false,
      sessionMutation: true,
    })
    return toSession(response)
  },

  getCurrentUser(): Promise<CurrentUserResponse> {
    return apiRequest<CurrentUserResponse>(authEndpoints.me, {
      retryAuthentication: false,
    }).then(validateUser)
  },

  synchronizeCurrentUser(
    session: AuthenticatedSession,
    currentUser: CurrentUserResponse,
  ): AuthenticatedSession {
    const user = validateUser(currentUser)
    if (user.userId !== session.userId) {
      throw new ApiError('Your session could not be verified. Please sign in again.', 401)
    }
    return {
      ...session,
      email: user.email,
      active: user.active,
      displayName: displayNameFromEmail(user.email),
      roles: mapSystemRoleToPortalRoles(user.role),
      portal: user.role === 'ADMIN' ? 'SYSTEM' : 'FAMILY',
    }
  },

  logout(): Promise<void> {
    return apiRequest<void>(authEndpoints.logout, {
      method: 'POST',
      authentication: 'none',
      retryAuthentication: false,
      sessionMutation: true,
    })
  },
}

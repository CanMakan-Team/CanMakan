import { mockLogin } from '../mocks/mockAuthRepository'
import { apiRequest, useMockApi } from './apiClient'
import type { AuthenticatedSession, Portal } from './types'

export const authService = {
  login(portal: Portal): Promise<AuthenticatedSession> {
    return useMockApi
      ? mockLogin(portal)
      : apiRequest<AuthenticatedSession>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ portal }),
        })
  },
}

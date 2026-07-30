import type { AuthenticatedSession, Portal } from '../shared/api/types'

export async function mockLogin(portal: Portal): Promise<AuthenticatedSession> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  return portal === 'FAMILY'
    ? {
        userId: 101,
        displayName: 'Alicia Lim',
        roles: ['ROLE_FAMILY_ADMIN'],
        portal: 'FAMILY',
        prototype: true,
      }
    : {
        userId: 9001,
        displayName: 'System Administrator',
        roles: ['ROLE_SYSTEM_ADMIN'],
        portal: 'SYSTEM',
        prototype: true,
      }
}

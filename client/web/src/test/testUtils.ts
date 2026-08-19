/** Shared helpers for web client tests that mock fetch. 
 * 
 * @author Amelia
*/

import type { AuthenticatedSession } from '../shared/api/types'

export const SESSION_KEY = 'canmakan.session'

export function jsonResponse(status: number, body?: unknown): Response {
  if (status === 204 || body === undefined) {
    return new Response(null, { status })
  }
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function appUserSession(): AuthenticatedSession {
  return {
    accessToken: 'test-access-token',
    userId: 14,
    email: 'person@example.com',
    active: true,
    displayName: 'person',
    roles: ['ROLE_APP_USER'],
    portal: 'FAMILY',
    prototype: false,
  }
}

export function systemAdminSession(): AuthenticatedSession {
  return {
    accessToken: 'admin-access-token',
    userId: 1,
    email: 'admin@example.com',
    active: true,
    displayName: 'admin',
    roles: ['ROLE_SYSTEM_ADMIN'],
    portal: 'SYSTEM',
    prototype: false,
  }
}

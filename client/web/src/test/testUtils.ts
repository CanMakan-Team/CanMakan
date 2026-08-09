/** Shared helpers for web client tests that mock fetch. 
 * 
 * @author Amelia
*/

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

export function familyAdminSession() {
  return {
    accessToken: 'test-access-token',
    userId: 14,
    displayName: 'person',
    roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'] as const,
    portal: 'FAMILY' as const,
    prototype: false,
  }
}

export function systemAdminSession() {
  return {
    accessToken: 'admin-access-token',
    userId: 1,
    displayName: 'admin',
    roles: ['ROLE_SYSTEM_ADMIN'] as const,
    portal: 'SYSTEM' as const,
    prototype: false,
  }
}

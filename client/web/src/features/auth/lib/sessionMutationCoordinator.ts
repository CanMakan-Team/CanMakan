import { ApiError } from '../../../shared/api/apiErrors'

const sessionMutationLockName = 'canmakan.session-mutation'

export type SessionMutationKind = 'refresh' | 'login' | 'logout'

/**
 * Serialises every operation that can change the shared HttpOnly refresh cookie.
 * Web Locks is required so unsupported browsers fail closed instead of silently
 * falling back to a tab-local mutex.
 */
export async function withSessionMutationLock<T>(
  _kind: SessionMutationKind,
  operation: () => Promise<T>,
): Promise<T> {
  if (typeof navigator === 'undefined' || !navigator.locks?.request) {
    throw new ApiError(
      'This browser cannot coordinate a secure sign-in. Update the browser and try again.',
    )
  }

  return navigator.locks.request(
    sessionMutationLockName,
    { mode: 'exclusive' },
    operation,
  )
}

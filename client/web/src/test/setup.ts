import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach } from 'vitest'
import { cleanup } from '@testing-library/react'

let lockTail: Promise<unknown> = Promise.resolve()

function installSerialWebLocks() {
  lockTail = Promise.resolve()
  const locks = {
    request: (
      _name: string,
      optionsOrCallback: LockOptions | ((lock: Lock | null) => unknown),
      maybeCallback?: (lock: Lock | null) => unknown,
    ) => {
      const callback =
        typeof optionsOrCallback === 'function'
          ? optionsOrCallback
          : requireLockCallback(maybeCallback)
      const result = lockTail.then(() => callback(null))
      lockTail = result.then(
        () => undefined,
        () => undefined,
      )
      return result
    },
  } as unknown as LockManager
  Object.defineProperty(navigator, 'locks', {
    configurable: true,
    value: locks,
  })
}

function requireLockCallback(
  callback: ((lock: Lock | null) => unknown) | undefined,
) {
  if (!callback) throw new Error('Web Lock callback is required')
  return callback
}

/**
 * @author Amelia
 */

beforeEach(() => installSerialWebLocks())

afterEach(() => {
  cleanup()
  localStorage.clear()
  sessionStorage.clear()
})

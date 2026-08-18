import { describe, expect, it } from 'vitest'
import { withSessionMutationLock } from '../../../features/auth/lib/sessionMutationCoordinator'

describe('sessionMutationCoordinator', () => {
  it('serialises independent refresh callers that represent separate tabs', async () => {
    let active = 0
    let maximumActive = 0

    const refresh = () =>
      withSessionMutationLock('refresh', async () => {
        active += 1
        maximumActive = Math.max(maximumActive, active)
        await Promise.resolve()
        active -= 1
      })

    await Promise.all([refresh(), refresh()])

    expect(maximumActive).toBe(1)
  })

  it('serialises refresh, logout, and new-account login across the shared lock', async () => {
    const order: string[] = []
    let releaseRefresh: (() => void) | undefined
    const refreshGate = new Promise<void>((resolve) => {
      releaseRefresh = resolve
    })

    const refresh = withSessionMutationLock('refresh', async () => {
      order.push('refresh-start')
      await refreshGate
      order.push('refresh-end')
    })
    const logout = withSessionMutationLock('logout', async () => {
      order.push('logout')
    })
    const login = withSessionMutationLock('login', async () => {
      order.push('login')
    })

    await Promise.resolve()
    expect(order).toEqual(['refresh-start'])
    releaseRefresh?.()
    await Promise.all([refresh, logout, login])

    expect(order).toEqual(['refresh-start', 'refresh-end', 'logout', 'login'])
  })

  it('fails closed when Web Locks is unavailable', async () => {
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: undefined,
    })

    await expect(
      withSessionMutationLock('refresh', async () => 'unreachable'),
    ).rejects.toThrow('cannot coordinate a secure sign-in')
  })
})

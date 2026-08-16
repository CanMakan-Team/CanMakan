import { afterEach, describe, expect, it, vi } from 'vitest'
import { getFirebaseAppDistributionUrl } from '../../../../features/account/lib/firebaseAppDistribution'

describe('getFirebaseAppDistributionUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('uses the Vite env value when set', () => {
    vi.stubEnv('VITE_FIREBASE_APP_DISTRIBUTION_URL', 'https://example.test/app-dist')
    expect(getFirebaseAppDistributionUrl()).toBe('https://example.test/app-dist')
  })

  it('falls back to the public App Distribution portal', () => {
    vi.stubEnv('VITE_FIREBASE_APP_DISTRIBUTION_URL', '')
    expect(getFirebaseAppDistributionUrl()).toBe(
      'https://appdistribution.firebase.google.com/',
    )
  })
})

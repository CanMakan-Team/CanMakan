const DEFAULT_FIREBASE_APP_DISTRIBUTION_URL =
  'https://appdistribution.firebase.google.com/'

/**
 * Tester / UAT download URL for the Android app.
 * Production builds take VITE_FIREBASE_APP_DISTRIBUTION_URL from GitHub secret
 * FIREBASE_APP_DISTRIBUTION_URL. The value is public in the browser bundle.
 */
export function getFirebaseAppDistributionUrl(): string {
  const configured = import.meta.env.VITE_FIREBASE_APP_DISTRIBUTION_URL?.trim()
  return configured || DEFAULT_FIREBASE_APP_DISTRIBUTION_URL
}

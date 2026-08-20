import * as amplitude from '@amplitude/analytics-browser'

/**
 * Initializes Amplitude analytics when VITE_AMPLITUDE_API_KEY is configured.
 * Analytics are disabled (no-op) when the key is missing, which is expected
 * for local development and any environment that has not opted in.
 */
export function initAmplitude(
  amplitudeKey: string | undefined = import.meta.env.VITE_AMPLITUDE_API_KEY,
  isDev: boolean = import.meta.env.DEV,
): void {
  if (amplitudeKey) {
    amplitude.init(amplitudeKey, {
      defaultTracking: {
        pageViews: true,
        sessions: true,
        formInteractions: false,
        fileDownloads: false,
      },
    })
  } else if (isDev) {
    console.warn('Amplitude API key is missing. Analytics are disabled for this environment.')
  }
}

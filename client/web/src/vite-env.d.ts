/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_USE_MOCK_API?: string
  readonly VITE_FIREBASE_APP_DISTRIBUTION_URL?: string
  readonly VITE_NGROK_SKIP_BROWSER_WARNING?: string
  readonly VITE_APP_VERSION?: string
  readonly VITE_APP_ENV?: string
  readonly VITE_AMPLITUDE_API_KEY?: string
}

declare module '@mascot/*.png' {
  const src: string
  export default src
}



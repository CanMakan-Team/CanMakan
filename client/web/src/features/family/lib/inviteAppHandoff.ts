/** Native app handoff for HTTPS invite links opened on a phone. */

export const CANMAKAN_ANDROID_PACKAGE = 'sg.edu.nus.iss.canmakan'

export function isAndroidUserAgent(userAgent: string): boolean {
  return /Android/i.test(userAgent)
}

export function isMobileUserAgent(userAgent: string): boolean {
  return /Android|iPhone|iPad|iPod/i.test(userAgent)
}

export function canmakanInviteDeepLink(token: string): string {
  return `canmakan://invite/${encodeURIComponent(token)}`
}

/**
 * Chrome on Android honors this Intent URL and opens the installed app.
 * If the app is missing, it loads {@code webFallbackUrl} (use {@code ?web=1}).
 */
export function androidInviteIntentUrl(token: string, webFallbackUrl: string): string {
  return (
    `intent://invite/${encodeURIComponent(token)}#Intent;` +
    `scheme=canmakan;package=${CANMAKAN_ANDROID_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(webFallbackUrl)};end`
  )
}

export function preferWebInvite(search: string): boolean {
  return new URLSearchParams(search).get('web') === '1'
}

export function webInviteStayUrl(origin: string, token: string): string {
  return `${origin}/invite/${encodeURIComponent(token)}?web=1`
}

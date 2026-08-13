import { describe, expect, it } from 'vitest'
import {
  androidInviteIntentUrl,
  canmakanInviteDeepLink,
  isAndroidUserAgent,
  isMobileUserAgent,
  preferWebInvite,
  webInviteStayUrl,
} from '../../../../features/family/lib/inviteAppHandoff'

describe('inviteAppHandoff', () => {
  it('detects Android versus desktop and iOS', () => {
    expect(
      isAndroidUserAgent(
        'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36',
      ),
    ).toBe(true)
    expect(
      isMobileUserAgent(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
      ),
    ).toBe(true)
    expect(
      isAndroidUserAgent(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
      ),
    ).toBe(false)
  })

  it('builds the custom scheme and Android intent with a web fallback', () => {
    expect(canmakanInviteDeepLink('abc/def')).toBe('canmakan://invite/abc%2Fdef')
    expect(
      androidInviteIntentUrl('tok', 'http://localhost:5173/invite/tok?web=1'),
    ).toBe(
      'intent://invite/tok#Intent;scheme=canmakan;package=sg.edu.nus.iss.canmakan;S.browser_fallback_url=http%3A%2F%2Flocalhost%3A5173%2Finvite%2Ftok%3Fweb%3D1;end',
    )
  })

  it('treats web=1 as stay-in-browser', () => {
    expect(preferWebInvite('?web=1')).toBe(true)
    expect(preferWebInvite('')).toBe(false)
    expect(webInviteStayUrl('http://localhost:5173', 'tok')).toBe(
      'http://localhost:5173/invite/tok?web=1',
    )
  })
})

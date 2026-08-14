import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { SessionContext, type SessionContextValue } from '../../../features/auth/SessionContext'
import { InviteLandingPage } from '../../../features/family/pages/InviteLandingPage'

const guestSession: SessionContextValue = {
  session: null,
  loading: false,
  restoring: false,
  restorationError: '',
  retryRestoration: () => undefined,
  loginWithCredentials: async () => {
    throw new Error('unused')
  },
  register: async () => {
    throw new Error('unused')
  },
  registerAndLogin: async () => {
    throw new Error('unused')
  },
  logout: async () => undefined,
}

const DESKTOP_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36'
const ANDROID_UA =
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36'

function setUserAgent(userAgent: string) {
  Object.defineProperty(window.navigator, 'userAgent', {
    configurable: true,
    value: userAgent,
  })
}

function renderInvite(path: string) {
  return render(
    <SessionContext.Provider value={guestSession}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/invite/:token" element={<InviteLandingPage />} />
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  )
}

describe('InviteLandingPage app handoff', () => {
  const locationReplace = vi.fn()

  beforeEach(() => {
    locationReplace.mockReset()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        origin: 'http://localhost:5173',
        replace: locationReplace,
      },
    })
    setUserAgent(DESKTOP_UA)
  })

  it('keeps desktop browsers on the web register/login landing', () => {
    renderInvite('/invite/tok-1')

    expect(screen.getByRole('link', { name: 'Create account' })).toHaveAttribute(
      'href',
      '/family-register?invitationToken=tok-1',
    )
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('sends Android browsers to the CanMakan app', () => {
    setUserAgent(ANDROID_UA)
    renderInvite('/invite/tok-1')

    expect(screen.getByRole('heading', { name: 'Opening CanMakan…' })).toBeInTheDocument()
    expect(locationReplace).toHaveBeenCalledWith(
      'intent://invite/tok-1#Intent;scheme=canmakan;package=sg.edu.nus.iss.canmakan;S.browser_fallback_url=http%3A%2F%2Flocalhost%3A5173%2Finvite%2Ftok-1%3Fweb%3D1;end',
    )
    expect(screen.getByRole('link', { name: 'Open CanMakan app' })).toHaveAttribute(
      'href',
      'canmakan://invite/tok-1',
    )
  })

  it('stays on web when web=1 even on Android', () => {
    setUserAgent(ANDROID_UA)
    renderInvite('/invite/tok-1?web=1')

    expect(screen.getByRole('link', { name: 'Create account' })).toBeInTheDocument()
    expect(locationReplace).not.toHaveBeenCalled()
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PortalLayout } from '../../../shared/ui/PortalLayout'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import { systemAdminSession } from '../../testUtils'

const sessionValue: SessionContextValue = {
  session: systemAdminSession(),
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

function renderSystemPortal() {
  return render(
    <SessionContext.Provider value={sessionValue}>
      <MemoryRouter initialEntries={['/system']}>
        <Routes>
          <Route element={<PortalLayout portal="system" />}>
            <Route path="/system" element={<p>System dashboard</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  )
}

describe('PortalLayout navigation', () => {
  beforeEach(() => {
    localStorage.removeItem('canmakan.portal.nav-open')
    window.matchMedia = ((query: string) =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }) as MediaQueryList) as typeof window.matchMedia
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('closes and reopens the sidebar from the header and nav controls', async () => {
    const user = userEvent.setup()
    renderSystemPortal()

    expect(screen.getByRole('navigation', { name: 'System Administration navigation' }))
      .toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open navigation' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Hide navigation' }))
    expect(screen.getByRole('button', { name: 'Open navigation' })).toBeInTheDocument()
    expect(document.getElementById('portal-sidebar')).toHaveAttribute('aria-hidden', 'true')

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))
    expect(screen.getByRole('button', { name: 'Hide navigation' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Open navigation' })).not.toBeInTheDocument()
    expect(document.getElementById('portal-sidebar')).toHaveAttribute('aria-hidden', 'false')
  })

  it('remembers a closed sidebar after reload', async () => {
    const user = userEvent.setup()
    const { unmount } = renderSystemPortal()

    await user.click(screen.getByRole('button', { name: 'Hide navigation' }))
    expect(localStorage.getItem('canmakan.portal.nav-open')).toBe('0')
    unmount()

    renderSystemPortal()
    expect(screen.getByRole('button', { name: 'Open navigation' })).toBeInTheDocument()
    expect(document.getElementById('portal-sidebar')).toHaveAttribute('aria-hidden', 'true')
  })

  it('starts closed on compact viewports so the page title stays visible', () => {
    window.matchMedia = ((query: string) =>
      ({
        matches: query.includes('1100px'),
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }) as MediaQueryList) as typeof window.matchMedia

    renderSystemPortal()

    expect(screen.getByRole('button', { name: 'Open navigation' })).toBeInTheDocument()
    expect(document.getElementById('portal-sidebar')).toHaveAttribute('aria-hidden', 'true')
  })
})

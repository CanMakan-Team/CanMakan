import { describe, expect, it } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { ProtectedRoute } from '../../../features/auth/ProtectedRoute'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import type { AuthenticatedSession } from '../../../shared/api/types'
import { appUserSession, systemAdminSession } from '../../testUtils'

/** Test suite for ProtectedRoute.
 * 
 * @author Amelia
 */

function renderWithSession(
  session: AuthenticatedSession | null,
  initialPath: string,
  requiredRole: 'ROLE_APP_USER' | 'ROLE_SYSTEM_ADMIN',
  state: Partial<SessionContextValue> = {},
) {
  const value: SessionContextValue = {
    session,
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
    ...state,
  }

  return render(
    <SessionContext.Provider value={value}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route element={<ProtectedRoute requiredRole={requiredRole} />}>
            <Route path="/family" element={<p>Family home</p>} />
            <Route path="/system" element={<p>System home</p>} />
          </Route>
          <Route path="/family-login" element={<p>Family login</p>} />
          <Route path="/system-admin-login" element={<p>System login</p>} />
          <Route path="/access-denied" element={<p>Access denied</p>} />
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  )
}

describe('ProtectedRoute', () => {
  it('redirects unauthenticated family visitors to family login', () => {
    renderWithSession(null, '/family', 'ROLE_APP_USER')
    expect(screen.getByText('Family login')).toBeInTheDocument()
  })

  it('allows a platform USER into personal and family navigation', () => {
    renderWithSession(
      { ...appUserSession(), roles: [...appUserSession().roles] },
      '/family',
      'ROLE_APP_USER',
    )
    expect(screen.getByText('Family home')).toBeInTheDocument()
  })

  it('blocks system admin from family portal with access denied', () => {
    renderWithSession(
      { ...systemAdminSession(), roles: [...systemAdminSession().roles] },
      '/family',
      'ROLE_APP_USER',
    )
    expect(screen.getByText('Access denied')).toBeInTheDocument()
  })

  it('redirects unauthenticated system visitors to system login', () => {
    renderWithSession(null, '/system', 'ROLE_SYSTEM_ADMIN')
    expect(screen.getByText('System login')).toBeInTheDocument()
  })

  it('shows a visible loading state while a direct system visit restores', () => {
    renderWithSession(null, '/system', 'ROLE_SYSTEM_ADMIN', { restoring: true })
    expect(screen.getByRole('status')).toHaveTextContent(
      'Restoring your secure session…',
    )
    expect(screen.queryByText('System login')).not.toBeInTheDocument()
  })

  it('shows a retryable error instead of a blank system page', () => {
    renderWithSession(null, '/system', 'ROLE_SYSTEM_ADMIN', {
      restorationError: 'The service is temporarily unavailable.',
    })
    expect(screen.getByRole('alert')).toHaveTextContent(
      'The service is temporarily unavailable.',
    )
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

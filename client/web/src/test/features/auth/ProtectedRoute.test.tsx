import { describe, expect, it } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import { ProtectedRoute } from '../../../features/auth/ProtectedRoute'
import {
  SessionContext,
  type SessionContextValue,
} from '../../../features/auth/SessionContext'
import type { AuthenticatedSession } from '../../../shared/api/types'
import { familyAdminSession, systemAdminSession } from '../../testUtils'

/** Test suite for ProtectedRoute.
 * 
 * @author Amelia
 */

function renderWithSession(
  session: AuthenticatedSession | null,
  initialPath: string,
  requiredRole: 'ROLE_FAMILY_ADMIN' | 'ROLE_SYSTEM_ADMIN',
) {
  const value: SessionContextValue = {
    session,
    loading: false,
    loginWithCredentials: async () => {
      throw new Error('unused')
    },
    registerAndLogin: async () => {
      throw new Error('unused')
    },
    logout: () => undefined,
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
    renderWithSession(null, '/family', 'ROLE_FAMILY_ADMIN')
    expect(screen.getByText('Family login')).toBeInTheDocument()
  })

  it('allows family admin into the family portal', () => {
    renderWithSession(
      { ...familyAdminSession(), roles: [...familyAdminSession().roles] },
      '/family',
      'ROLE_FAMILY_ADMIN',
    )
    expect(screen.getByText('Family home')).toBeInTheDocument()
  })

  it('blocks system admin from family portal with access denied', () => {
    renderWithSession(
      { ...systemAdminSession(), roles: [...systemAdminSession().roles] },
      '/family',
      'ROLE_FAMILY_ADMIN',
    )
    expect(screen.getByText('Access denied')).toBeInTheDocument()
  })

  it('redirects unauthenticated system visitors to system login', () => {
    renderWithSession(null, '/system', 'ROLE_SYSTEM_ADMIN')
    expect(screen.getByText('System login')).toBeInTheDocument()
  })
})

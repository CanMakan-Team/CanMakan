import { useState } from 'react'
import {
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { useSession } from '../../features/auth/useSession'

interface NavigationItem {
  label: string
  to: string
  icon: string
}

const familyNavigation: NavigationItem[] = [
  { label: 'Home', to: '/family', icon: '⌂' },
  { label: 'Personal Home', to: '/family/personal', icon: '◇' },
  { label: 'Dietary Profile', to: '/family/setup-profile', icon: '◇' },
  { label: 'Family Circle', to: '/family/circle', icon: '♙' },
  { label: 'Family Dashboard', to: '/family/dashboard', icon: '⌂' },
  { label: 'Family Members', to: '/family/members', icon: '♙' },
  { label: 'Restriction Summary', to: '/family/restrictions', icon: '▦' },
  { label: 'Family Scan History', to: '/family/history', icon: '◷' },
  { label: 'Account Settings', to: '/family/account', icon: '⚙' },
]

const systemNavigation: NavigationItem[] = [
  { label: 'Dashboard', to: '/system', icon: '⌂' },
  { label: 'Consumer Trends', to: '/system/trends', icon: '↗' },
  { label: 'User Accounts & Access', to: '/system/users', icon: '♙' },
  { label: 'Future Features', to: '/system/future', icon: '◇' },
]

export function PortalLayout({ portal }: { portal: 'family' | 'system' }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const { session, logout } = useSession()
  const navigate = useNavigate()
  const location = useLocation()

  // CMK-55 Read the Web Version, Falling Back for Local Development
  const appVersion = import.meta.env.VITE_APP_VERSION || 'Local Development'

  const navigation = portal === 'family' ? familyNavigation : systemNavigation
  const portalName =
    portal === 'family' ? 'CanMakan User Portal' : 'System Administration'

  const signOut = () => {
    void logout()
    navigate(portal === 'family' ? '/family-login' : '/system-admin-login')
  }

  return (
    <div className={`portal-shell portal-shell--${portal}`}>
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <aside className={`sidebar ${menuOpen ? 'sidebar--open' : ''}`}>
        <div className="sidebar__brand">
          <span className="brand-mark" aria-hidden="true">
            CM
          </span>
          <div>
            <strong>CanMakan</strong>
            <span>{portalName}</span>
          </div>
        </div>
        <nav className="sidebar__nav" aria-label={`${portalName} navigation`}>
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === `/${portal}`}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                isActive ? 'sidebar__link sidebar__link--active' : 'sidebar__link'
              }
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar__footer">
          <div className="sidebar__user">
            <span className="avatar" aria-hidden="true">
              {session?.displayName.slice(0, 1)}
            </span>
            <div>
              <strong>{session?.displayName}</strong>
              <span>
                {portal === 'family' ? 'CanMakan User' : 'System Admin'}
              </span>
            </div>
          </div>
          <button className="sidebar__signout" type="button" onClick={signOut}>
            Sign out
          </button>

          {/* CMK-55 Display the Web Version, Falling Back for Local Development */}
          <span style={{ fontSize: '0.65rem', color: '#91ada3', padding: '0 0.6rem' }}>
            Version {appVersion}
          </span>
          
        </div>
      </aside>
      {menuOpen && (
        <button
          className="sidebar-scrim"
          type="button"
          aria-label="Close navigation"
          onClick={() => setMenuOpen(false)}
        />
      )}
      <div className="portal-main">
        <header className="mobile-header">
          <button
            className="icon-button"
            type="button"
            aria-label="Open navigation"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen(true)}
          >
            ☰
          </button>
          <strong>CanMakan</strong>
          <span className="mobile-header__page">
            {navigation.find(
              (item) =>
                item.to === location.pathname ||
                (item.to !== `/${portal}` && location.pathname.startsWith(item.to)),
            )?.label ?? portalName}
          </span>
        </header>
        <main id="main-content" className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

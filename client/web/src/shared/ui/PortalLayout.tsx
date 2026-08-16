import { useState } from 'react'
import {
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { USER_LOGIN_PATH } from '../../app/userPortalPaths'
import { FamilyMeProvider } from '../../features/family/FamilyMeContext'
import { useFamilyMe } from '../../features/family/useFamilyMe'
import { userPortalSections } from '../../features/family/lib/userPortalNav'
import { useSession } from '../../features/auth/useSession'

interface NavigationItem {
  label: string
  to: string
  icon: string
}

interface NavigationSection {
  label: string
  items: NavigationItem[]
}

const systemSections: NavigationSection[] = [
  {
    label: '',
    items: [
      { label: 'Dashboard', to: '/system', icon: '⌂' },
      { label: 'Consumer Trends', to: '/system/trends', icon: '↗' },
      { label: 'User Accounts & Access', to: '/system/users', icon: '♙' },
      { label: 'Handle User Feedback', to: '/system/feedback', icon: '⚑' },
      { label: 'Future Features', to: '/system/future', icon: '◇' },
    ],
  },
]

export function PortalLayout({ portal }: { portal: 'family' | 'system' }) {
  if (portal === 'system') {
    return <PortalShell portal="system" sections={systemSections} />
  }

  return (
    <FamilyMeProvider>
      <UserPortalShell />
    </FamilyMeProvider>
  )
}

function UserPortalShell() {
  const { hasFamily, isPrimaryAdmin } = useFamilyMe()
  const sections = userPortalSections({ hasFamily, isPrimaryAdmin })
  return <PortalShell portal="family" sections={sections} />
}

function PortalShell({
  portal,
  sections,
}: {
  portal: 'family' | 'system'
  sections: NavigationSection[]
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const { session, logout } = useSession()
  const navigate = useNavigate()
  const location = useLocation()

  const appVersion = import.meta.env.VITE_APP_VERSION || 'Local Development'
  const navigation = sections.flatMap((section) => section.items)
  const portalName =
    portal === 'family' ? 'CanMakan User Portal' : 'System Administration'

  const signOut = () => {
    void logout()
    navigate(portal === 'family' ? USER_LOGIN_PATH : '/system-admin-login')
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
          {sections.map((section, sectionIndex) => (
            <div className="sidebar__section" key={section.label || sectionIndex}>
              {sectionIndex > 0 && <hr className="sidebar__divider" />}
              {section.label && (
                <div className="sidebar__section-label">{section.label}</div>
              )}
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === `/${portal}` || item.to === '/me'}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    isActive ? 'sidebar__link sidebar__link--active' : 'sidebar__link'
                  }
                >
                  <span aria-hidden="true">{item.icon}</span>
                  {item.label}
                </NavLink>
              ))}
            </div>
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
                (item.to !== `/${portal}` &&
                  item.to !== '/me' &&
                  location.pathname.startsWith(item.to)),
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

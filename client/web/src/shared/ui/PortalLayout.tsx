import { useState } from 'react'
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { ME_PATH, USER_LOGIN_PATH } from '../../app/userPortalPaths'
import { FamilyMeProvider } from '../../features/family/FamilyMeContext'
import { useFamilyMe } from '../../features/family/useFamilyMe'
import { userPortalSections } from '../../features/family/lib/userPortalNav'
import { useSession } from '../../features/auth/useSession'
import { PortalIcon } from './PortalIcon'

interface NavigationItem {
  label: string
  to: string
  icon: string
}

interface NavigationSection {
  label: string
  items: NavigationItem[]
}

const NAV_OPEN_STORAGE_KEY = 'canmakan.portal.nav-open'
const COMPACT_NAV_QUERY = '(max-width: 1100px)'

function isCompactNavViewport() {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia(COMPACT_NAV_QUERY).matches
  )
}

function readNavOpen(): boolean {
  try {
    const stored = localStorage.getItem(NAV_OPEN_STORAGE_KEY)
    if (stored === '1') return true
    if (stored === '0') return false
  } catch {
    return !isCompactNavViewport()
  }
  return !isCompactNavViewport()
}

function writeNavOpen(open: boolean) {
  try {
    localStorage.setItem(NAV_OPEN_STORAGE_KEY, open ? '1' : '0')
  } catch {
    return
  }
}

const systemSections: NavigationSection[] = [
  {
    label: '',
    items: [
      { label: 'Dashboard', to: '/system', icon: 'overview' },
      { label: 'Consumer Trends', to: '/system/trends', icon: 'trends' },
      { label: 'Usage Statistics', to: '/system/usage', icon: 'chart' },
      { label: 'User Accounts & Access', to: '/system/users', icon: 'people' },
      { label: 'Handle User Feedback', to: '/system/feedback', icon: 'message' },
      { label: 'System Health', to: '/system/health', icon: 'activity' },
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
  const { hasFamily, isPrimaryAdmin, loading } = useFamilyMe()
  const sections = userPortalSections({ hasFamily, isPrimaryAdmin, loading })
  return <PortalShell portal="family" sections={sections} />
}

function PortalShell({
  portal,
  sections,
}: {
  portal: 'family' | 'system'
  sections: NavigationSection[]
}) {
  const [navOpen, setNavOpen] = useState(readNavOpen)
  const { session, logout } = useSession()
  const navigate = useNavigate()
  const location = useLocation()

  const appVersion = import.meta.env.VITE_APP_VERSION || 'Local Development'
  const navigation = sections.flatMap((section) => section.items)
  const portalName =
    portal === 'family' ? 'CanMakan User Portal' : 'System Administration'

  const setNavigationOpen = (open: boolean) => {
    setNavOpen(open)
    writeNavOpen(open)
  }

  const signOut = () => {
    void logout()
    navigate(portal === 'family' ? USER_LOGIN_PATH : '/system-admin-login')
  }

  return (
    <div
      className={`portal-shell portal-shell--${portal}${navOpen ? ' portal-shell--nav-open' : ''}`}
    >
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <aside
        id="portal-sidebar"
        className="sidebar"
        aria-hidden={!navOpen}
      >
        <div className="sidebar__brand">
          <span className="brand-mark" aria-hidden="true">
            CM
          </span>
          <div>
            <strong>CanMakan</strong>
            <span>{portalName}</span>
          </div>
          <button
            className="sidebar__collapse"
            type="button"
            aria-label="Hide navigation"
            onClick={() => setNavigationOpen(false)}
          >
            «
          </button>
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
                  onClick={() => {
                    if (isCompactNavViewport()) setNavigationOpen(false)
                  }}
                  className={({ isActive }) =>
                    isActive ? 'sidebar__link sidebar__link--active' : 'sidebar__link'
                  }
                >
                  <span aria-hidden="true">
                    <PortalIcon name={item.icon} />
                  </span>
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
      {navOpen && isCompactNavViewport() ? (
        <button
          className="sidebar-scrim"
          type="button"
          aria-label="Close navigation"
          onClick={() => setNavigationOpen(false)}
        />
      ) : null}
      <div className="portal-main">
        <header className="mobile-header">
          {navOpen ? null : (
            <button
              className="icon-button"
              type="button"
              aria-label="Open navigation"
              aria-expanded={false}
              aria-controls="portal-sidebar"
              onClick={() => setNavigationOpen(true)}
            >
              ☰
            </button>
          )}
          <nav className="mobile-header__crumb" aria-label="Breadcrumb">
            <Link className="mobile-header__brand" to={portal === 'family' ? ME_PATH : '/system'}>
              CanMakan
            </Link>
            <span className="mobile-header__sep" aria-hidden="true">
              /
            </span>
            <span className="mobile-header__page">
              {navigation.find(
                (item) =>
                  item.to === location.pathname ||
                  (item.to !== `/${portal}` &&
                    item.to !== '/me' &&
                    location.pathname.startsWith(item.to)),
              )?.label ?? portalName}
            </span>
          </nav>
        </header>
        <main id="main-content" className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

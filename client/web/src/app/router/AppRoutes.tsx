import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AccessDenied } from '../../features/auth/AccessDenied'
import { ProtectedRoute } from '../../features/auth/ProtectedRoute'
import { FamilyMeGate } from '../../features/family/FamilyMeGate'
import { AccountPage } from '../../features/account/pages/AccountPage'
import { FamilyCirclePage } from '../../features/family/pages/FamilyCirclePage'
import { SelfProfileSetupPage } from '../../features/account/pages/SelfProfileSetupPage'
import { PersonalHomePage } from '../../features/account/pages/PersonalHomePage'
import { UserLandingPage } from '../../features/family/pages/UserLandingPage'
import { FamilyDashboardPage } from '../../features/family/pages/FamilyDashboardPage'
import { FamilyMembersPage } from '../../features/family/pages/FamilyMembersPage'
import { FamilyRestrictionSummaryPage } from '../../features/family/pages/FamilyRestrictionSummaryPage'
import { FamilyScanHistoryPage } from '../../features/family/pages/FamilyScanHistoryPage'
import { ConsumerTrendsPage } from '../../features/analytics/ConsumerTrendsPage'
import { VerdictTrendsPage } from '../../features/analytics/VerdictTrendsPage'
import { FutureFeaturesPage } from '../../features/admin/FutureFeaturesPage'
import { SystemDashboardPage } from '../../features/admin/SystemDashboardPage'
import { UserAccessPage } from '../../features/admin/UserAccessPage'
import { PortalLayout } from '../../shared/ui/PortalLayout'
import { FamilyLoginPage } from '../../pages/FamilyLoginPage'
import { FamilyRegisterPage } from '../../pages/FamilyRegisterPage'
import { SystemAdminLoginPage } from '../../pages/SystemAdminLoginPage'
import { InviteLandingPage } from '../../features/family/pages/InviteLandingPage'
import { NotFoundPage } from '../../pages/NotFoundPage'
import {
  FAMILY_ROOT_PATH,
  LEGACY_USER_LOGIN_PATH,
  LEGACY_USER_REGISTER_PATH,
  ME_ACCOUNT_PATH,
  ME_PATH,
  ME_SETUP_PROFILE_PATH,
  USER_LOGIN_PATH,
  USER_REGISTER_PATH,
} from '../userPortalPaths'

function RedirectWithSearch({ to }: { to: string }) {
  const { search } = useLocation()
  return <Navigate to={`${to}${search}`} replace />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={USER_LOGIN_PATH} replace />} />
      <Route path={USER_LOGIN_PATH} element={<FamilyLoginPage />} />
      <Route path={USER_REGISTER_PATH} element={<FamilyRegisterPage />} />
      <Route
        path={LEGACY_USER_LOGIN_PATH}
        element={<RedirectWithSearch to={USER_LOGIN_PATH} />}
      />
      <Route
        path={LEGACY_USER_REGISTER_PATH}
        element={<RedirectWithSearch to={USER_REGISTER_PATH} />}
      />
      <Route path="/invite/:token" element={<InviteLandingPage />} />
      <Route path="/system-admin-login" element={<SystemAdminLoginPage />} />
      <Route path="/access-denied" element={<AccessDenied />} />

      <Route element={<ProtectedRoute requiredRole="ROLE_APP_USER" />}>
        <Route path={ME_PATH} element={<PortalLayout portal="family" />}>
          <Route index element={<PersonalHomePage />} />
          <Route path="setup-profile" element={<SelfProfileSetupPage />} />
          <Route path="account" element={<AccountPage />} />
        </Route>
        <Route path={FAMILY_ROOT_PATH} element={<PortalLayout portal="family" />}>
          <Route index element={<UserLandingPage />} />
          <Route
            path="personal"
            element={<Navigate to={ME_PATH} replace />}
          />
          <Route
            path="setup-profile"
            element={<Navigate to={ME_SETUP_PROFILE_PATH} replace />}
          />
          <Route path="account" element={<Navigate to={ME_ACCOUNT_PATH} replace />} />
          <Route path="circle" element={<FamilyCirclePage />} />
          <Route element={<FamilyMeGate />}>
            <Route path="dashboard" element={<FamilyDashboardPage />} />
            <Route path="members" element={<FamilyMembersPage />} />
            <Route
              path="restrictions"
              element={<FamilyRestrictionSummaryPage />}
            />
            <Route path="history" element={<FamilyScanHistoryPage />} />
            <Route path="verdict-trends" element={<VerdictTrendsPage />} />
          </Route>
        </Route>
      </Route>

      <Route element={<ProtectedRoute requiredRole="ROLE_SYSTEM_ADMIN" />}>
        <Route path="/system" element={<PortalLayout portal="system" />}>
          <Route index element={<SystemDashboardPage />} />
          <Route path="trends" element={<ConsumerTrendsPage />} />
          <Route path="users" element={<UserAccessPage />} />
          <Route path="future" element={<FutureFeaturesPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

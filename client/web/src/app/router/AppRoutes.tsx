import { Navigate, Route, Routes } from 'react-router-dom'
import { AccessDenied } from '../../features/auth/AccessDenied'
import { ProtectedRoute } from '../../features/auth/ProtectedRoute'
import { FamilyMeGate } from '../../features/family/FamilyMeGate'
import { FamilyAccountPage } from '../../features/family/pages/FamilyAccountPage'
import { FamilyCirclePage } from '../../features/family/pages/FamilyCirclePage'
import { SelfProfileSetupPage } from '../../features/family/pages/SelfProfileSetupPage'
import { PersonalHomePage } from '../../features/family/pages/PersonalHomePage'
import { UserLandingPage } from '../../features/family/pages/UserLandingPage'
import { FamilyDashboardPage } from '../../features/family/pages/FamilyDashboardPage'
import { FamilyMembersPage } from '../../features/family/pages/FamilyMembersPage'
import { FamilyRestrictionSummaryPage } from '../../features/family/pages/FamilyRestrictionSummaryPage'
import { FamilyScanHistoryPage } from '../../features/family/pages/FamilyScanHistoryPage'
import { ConsumerTrendsPage } from '../../features/analytics/ConsumerTrendsPage'
import { VerdictTrendsPage } from '../../features/analytics/VerdictTrendsPage'
import { UsageStatisticsPage } from '../../features/analytics/UsageStatisticsPage'
import { FutureFeaturesPage } from '../../features/admin/FutureFeaturesPage'
import { SystemDashboardPage } from '../../features/admin/SystemDashboardPage'
import { UserAccessPage } from '../../features/admin/UserAccessPage'
import { PortalLayout } from '../../shared/ui/PortalLayout'
import { FamilyLoginPage } from '../../pages/FamilyLoginPage'
import { FamilyRegisterPage } from '../../pages/FamilyRegisterPage'
import { SystemAdminLoginPage } from '../../pages/SystemAdminLoginPage'
import { InviteLandingPage } from '../../features/family/pages/InviteLandingPage'
import { NotFoundPage } from '../../pages/NotFoundPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/family-login" replace />} />
      <Route path="/family-login" element={<FamilyLoginPage />} />
      <Route path="/family-register" element={<FamilyRegisterPage />} />
      <Route path="/invite/:token" element={<InviteLandingPage />} />
      <Route path="/system-admin-login" element={<SystemAdminLoginPage />} />
      <Route path="/access-denied" element={<AccessDenied />} />

      <Route element={<ProtectedRoute requiredRole="ROLE_APP_USER" />}>
        <Route path="/family" element={<PortalLayout portal="family" />}>
          <Route index element={<UserLandingPage />} />
          <Route path="personal" element={<PersonalHomePage />} />
          <Route path="setup-profile" element={<SelfProfileSetupPage />} />
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
            <Route path="account" element={<FamilyAccountPage />} />
          </Route>
        </Route>
      </Route>

      <Route element={<ProtectedRoute requiredRole="ROLE_SYSTEM_ADMIN" />}>
        <Route path="/system" element={<PortalLayout portal="system" />}>
          <Route index element={<SystemDashboardPage />} />
          <Route path="trends" element={<ConsumerTrendsPage />} />
          <Route path="usage" element={<UsageStatisticsPage />} />
          <Route path="users" element={<UserAccessPage />} />
          <Route path="future" element={<FutureFeaturesPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

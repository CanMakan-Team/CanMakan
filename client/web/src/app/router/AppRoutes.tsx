import { Navigate, Route, Routes } from 'react-router-dom'
import { AccessDenied } from '../../features/auth/AccessDenied'
import { ProtectedRoute } from '../../features/auth/ProtectedRoute'
import { FamilyAccountPage } from '../../features/family/FamilyAccountPage'
import { FamilyDashboardPage } from '../../features/family/FamilyDashboardPage'
import { FamilyMembersPage } from '../../features/family/FamilyMembersPage'
import { FamilyRestrictionSummaryPage } from '../../features/family/FamilyRestrictionSummaryPage'
import { FamilyScanHistoryPage } from '../../features/family/FamilyScanHistoryPage'
import { FamilyTestPage } from '../../features/family/FamilyTestPage'
import { ConsumerTrendsPage } from '../../features/analytics/ConsumerTrendsPage'
import { FutureFeaturesPage } from '../../features/admin/FutureFeaturesPage'
import { SystemDashboardPage } from '../../features/admin/SystemDashboardPage'
import { UserAccessPage } from '../../features/admin/UserAccessPage'
import { PortalLayout } from '../../shared/ui/PortalLayout'
import { FamilyLoginPage } from '../../pages/FamilyLoginPage'
import { SystemAdminLoginPage } from '../../pages/SystemAdminLoginPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/family-login" replace />} />
      <Route path="/family-login" element={<FamilyLoginPage />} />
      <Route path="/system-admin-login" element={<SystemAdminLoginPage />} />
      <Route path="/access-denied" element={<AccessDenied />} />

      <Route element={<ProtectedRoute requiredRole="ROLE_FAMILY_ADMIN" />}>
        <Route path="/family" element={<PortalLayout portal="family" />}>
          <Route index element={<FamilyDashboardPage />} />
          <Route path="members" element={<FamilyMembersPage />} />
          <Route
            path="restrictions"
            element={<FamilyRestrictionSummaryPage />}
          />
          <Route path="history" element={<FamilyScanHistoryPage />} />
          <Route path="account" element={<FamilyAccountPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute requiredRole="ROLE_SYSTEM_ADMIN" />}>
        <Route path="/system" element={<PortalLayout portal="system" />}>
          <Route index element={<SystemDashboardPage />} />
          <Route path="trends" element={<ConsumerTrendsPage />} />
          <Route path="users" element={<UserAccessPage />} />
          <Route path="future" element={<FutureFeaturesPage />} />
        </Route>
        <Route path="/family-test" element={<FamilyTestPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/family-login" replace />} />
    </Routes>
  )
}

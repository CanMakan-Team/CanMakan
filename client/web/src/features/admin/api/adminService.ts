import { apiRequest } from '../../../shared/api/apiClient'
import type {
  AdminScanFeedbackFilters,
  AdminScanFeedbackListResponse,
  AdminUser,
  AdminUserFilters,
  UpdateAccountStatusInput,
  UpdateAccountStatusResponse,
  UpdateScanFeedbackResolvedResponse,
} from './models'

export const adminEndpoints = {
  users: '/api/admin/users',
  scanFeedback: '/api/admin/scan-feedback',
} as const

function buildUsersPath(filters: AdminUserFilters): string {
  const parameters = new URLSearchParams()
  const query = filters.query?.trim()
  if (query) parameters.set('query', query)
  if (filters.role !== undefined) parameters.set('role', filters.role)
  if (filters.active !== undefined) {
    parameters.set('active', String(filters.active))
  }

  const queryString = parameters.toString()
  return queryString
    ? `${adminEndpoints.users}?${queryString}`
    : adminEndpoints.users
}

function buildScanFeedbackPath(filters: AdminScanFeedbackFilters): string {
  const parameters = new URLSearchParams()
  const keyword = filters.keyword?.trim()
  if (keyword) parameters.set('keyword', keyword)
  if (filters.restrictionCode) {
    parameters.set('restrictionCode', filters.restrictionCode)
  }
  if (filters.periodDays !== undefined) {
    parameters.set('periodDays', String(filters.periodDays))
  }
  if (filters.isPositive !== undefined) {
    parameters.set('isPositive', String(filters.isPositive))
  }
  if (filters.resolved !== undefined) {
    parameters.set('resolved', String(filters.resolved))
  }
  if (filters.page !== undefined) parameters.set('page', String(filters.page))
  if (filters.pageSize !== undefined) {
    parameters.set('pageSize', String(filters.pageSize))
  }

  const queryString = parameters.toString()
  return queryString
    ? `${adminEndpoints.scanFeedback}?${queryString}`
    : adminEndpoints.scanFeedback
}

export const adminService = {
  getUsers: (filters: AdminUserFilters = {}) =>
    apiRequest<AdminUser[]>(buildUsersPath(filters)),
  updateAccountStatus: (
    userId: number,
    input: UpdateAccountStatusInput,
  ) =>
    apiRequest<UpdateAccountStatusResponse>(
      `${adminEndpoints.users}/${userId}/status`,
      {
        method: 'PATCH',
        body: JSON.stringify(input),
      },
    ),
  getScanFeedback: (filters: AdminScanFeedbackFilters = {}) =>
    apiRequest<AdminScanFeedbackListResponse>(buildScanFeedbackPath(filters)),
  updateScanFeedbackResolved: (feedbackId: number, resolved: boolean) =>
    apiRequest<UpdateScanFeedbackResolvedResponse>(
      `${adminEndpoints.scanFeedback}/${feedbackId}/resolved`,
      {
        method: 'PATCH',
        body: JSON.stringify({ resolved }),
      },
    ),
}

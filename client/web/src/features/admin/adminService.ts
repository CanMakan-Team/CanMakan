import { mockAdminRepository } from '../../mocks/mockAdminRepository'
import { apiRequest, useMockApi } from '../../shared/api/apiClient'
import type { ConsumerTrendResponse } from '../../shared/api/types'
import type {
  AdminUser,
  AdminUserFilters,
  UpdateAccountStatusInput,
  UpdateAccountStatusResponse,
} from './models'

export const adminEndpoints = {
  consumerTrends: '/api/admin/consumer-trends',
  users: '/api/admin/users',
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

export const adminService = {
  getConsumerTrends: () =>
    useMockApi
      ? mockAdminRepository.getConsumerTrends()
      : apiRequest<ConsumerTrendResponse>(adminEndpoints.consumerTrends),
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
}

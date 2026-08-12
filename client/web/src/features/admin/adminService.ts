import { apiRequest } from '../../shared/api/apiClient'
import type {
  AdminUser,
  AdminUserFilters,
  UpdateAccountStatusInput,
  UpdateAccountStatusResponse,
} from './models'

export const adminEndpoints = {
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

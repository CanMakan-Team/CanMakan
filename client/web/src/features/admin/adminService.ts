import { mockAdminRepository } from '../../mocks/mockAdminRepository'
import { apiRequest, useMockApi } from '../../shared/api/apiClient'
import type {
  AccessUpdate,
  AuditEntry,
  ConsumerTrendResponse,
  UserAccessSummary,
} from '../../shared/api/types'

export const adminEndpoints = {
  consumerTrends: '/api/admin/consumer-trends',
  users: '/api/admin/users',
} as const

export const adminService = {
  getConsumerTrends: () =>
    useMockApi
      ? mockAdminRepository.getConsumerTrends()
      : apiRequest<ConsumerTrendResponse>(adminEndpoints.consumerTrends),
  getUsers: () =>
    useMockApi
      ? mockAdminRepository.getUsers()
      : apiRequest<UserAccessSummary[]>(adminEndpoints.users),
  updateUserAccess: (userId: number, update: AccessUpdate, actor: string) =>
    useMockApi
      ? mockAdminRepository.updateUserAccess(userId, update, actor)
      : apiRequest<UserAccessSummary>(`${adminEndpoints.users}/${userId}/access`, {
          method: 'PATCH',
          body: JSON.stringify(update),
        }),
  getAuditEntries: () =>
    useMockApi
      ? mockAdminRepository.getAuditEntries()
      : apiRequest<AuditEntry[]>('/api/admin/audit'),
}

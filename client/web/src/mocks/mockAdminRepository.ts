import { ApiError } from '../api/apiErrors'
import type {
  AccessUpdate,
  AuditEntry,
  ConsumerTrendResponse,
  UserAccessSummary,
} from '../api/types'
import { consumerTrends, initialAudit, initialUsers } from './mockData'

interface AdminState {
  users: UserAccessSummary[]
  audit: AuditEntry[]
}

const stateKey = 'canmakan.mock.admin'
const delay = (milliseconds = 500) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

const readState = (): AdminState => {
  const stored = localStorage.getItem(stateKey)
  return stored
    ? (JSON.parse(stored) as AdminState)
    : { users: structuredClone(initialUsers), audit: structuredClone(initialAudit) }
}

const writeState = (state: AdminState) =>
  localStorage.setItem(stateKey, JSON.stringify(state))

export const mockAdminRepository = {
  async getConsumerTrends(): Promise<ConsumerTrendResponse> {
    await delay(600)
    return structuredClone(consumerTrends)
  },

  async getUsers(): Promise<UserAccessSummary[]> {
    await delay()
    return readState().users
  },

  async updateUserAccess(
    userId: number,
    update: AccessUpdate,
    actor: string,
  ): Promise<UserAccessSummary> {
    await delay(650)
    if (userId === 9001) {
      throw new ApiError(
        'The current administrator’s required access is protected in mock mode.',
      )
    }
    const state = readState()
    const index = state.users.findIndex((user) => user.userId === userId)
    if (index < 0) throw new ApiError('The selected account could not be found.')
    state.users[index] = { ...state.users[index], ...update }
    state.audit.unshift({
      auditId: Date.now(),
      actor,
      targetUserId: userId,
      action: update.roles
        ? `Updated roles to ${update.roles.join(', ')}`
        : `Changed account status to ${update.accountStatus}`,
      createdAt: new Date().toISOString(),
    })
    writeState(state)
    return state.users[index]
  },

  async getAuditEntries(): Promise<AuditEntry[]> {
    await delay(200)
    return readState().audit.slice(0, 5)
  },
}

import { ApiError } from '../shared/api/apiErrors'
import { formatCode } from '../features/family/lib/profileOptions'
import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMember,
  FamilyProfileInput,
  ScanRecord,
  FamilyRestrictionSumRes
} from '../shared/api/types'
import {
  existingUsers,
  initialFamilyState,
  scanRecords,
  type MockFamilyState,
} from './mockData'

/**
 * Mock family repository for unfinished surfaces when VITE_USE_MOCK_API=true.
 * UC8 create/`/me` are always live and are not mocked here.
 *
 * @author Amelia
 * 
 * Mock family restriction summary repository for unfinished surfaces when VITE_USE_MOCK_API=true.
 * UC6 family restriction summary endpoints are always live and are not mocked here.
 */
const stateKey = 'canmakan.mock.family'
const delay = (milliseconds = 450) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

function readState(): MockFamilyState {
  const stored = localStorage.getItem(stateKey)
  return stored ? (JSON.parse(stored) as MockFamilyState) : structuredClone(initialFamilyState)
}

function writeState(state: MockFamilyState) {
  localStorage.setItem(stateKey, JSON.stringify(state))
  window.dispatchEvent(new Event('canmakan:family-data-changed'))
}

export const mockFamilyRepository = {
  async getMembers(): Promise<FamilyMember[]> {
    await delay()
    return readState().members
  },

  async searchExistingUser(email: string): Promise<ExistingUserSearchResult | null> {
    await delay(650)
    if (email.toLowerCase() === 'error@demo.test') {
      throw new ApiError('Controlled demo error: user search is unavailable.')
    }
    const match = existingUsers[email.trim().toLowerCase()]
    if (!match) return null
    const isNowLinked = readState().members.some(
      (member) => member.memberId === match.userId,
    )
    return {
      ...match,
      familyLinkStatus: isNowLinked ? 'ALREADY_LINKED' : match.familyLinkStatus,
    }
  },

  async linkExistingUser(userId: number): Promise<FamilyMember> {
    await delay(650)
    const match = Object.values(existingUsers).find((user) => user.userId === userId)
    if (!match || match.familyLinkStatus !== 'NOT_LINKED') {
      throw new ApiError('This user cannot be linked in the current state.')
    }
    const state = readState()
    if (state.members.some((member) => member.memberId === userId)) {
      throw new ApiError('This user is already linked to the family.')
    }
    const member: FamilyMember = {
      memberId: match.userId,
      profileName: match.displayName,
      relationship: 'OTHER',
      ageGroup: 'UNSPECIFIED',
      commonRequirements: [],
      restrictions: [],
      source: 'REGISTERED_USER',
      maskedEmail: match.maskedEmail,
    }
    state.members.push(member)
    writeState(state)
    return member
  },

  async createProfile(input: FamilyProfileInput): Promise<FamilyMember> {
    await delay(650)
    const state = readState()
    const member: FamilyMember = {
      memberId: Date.now(),
      ...input,
      source: 'DEPENDANT_PROFILE',
    }
    state.members.push(member)
    writeState(state)
    return member
  },

  async updateProfile(
    memberId: number,
    input: FamilyProfileInput,
  ): Promise<FamilyMember> {
    await delay(650)
    const state = readState()
    const index = state.members.findIndex((member) => member.memberId === memberId)
    if (index < 0) throw new ApiError('The family profile could not be found.')
    state.members[index] = { ...state.members[index], ...input }
    if (state.activeProfile.memberId === memberId) {
      state.activeProfile.profileName = input.profileName
    }
    writeState(state)
    return state.members[index]
  },

  async getActiveProfile(): Promise<ActiveProfile> {
    await delay(250)
    return readState().activeProfile
  },

  async setActiveProfile(memberId: number): Promise<ActiveProfile> {
    await delay(500)
    const state = readState()
    const member = state.members.find((candidate) => candidate.memberId === memberId)
    if (!member) throw new ApiError('The selected profile is unavailable.')
    state.activeProfile = {
      memberId,
      profileName: member.profileName,
      activatedAt: new Date().toISOString(),
    }
    writeState(state)
    return state.activeProfile
  },

  async getScanHistory(): Promise<ScanRecord[]> {
    await delay(550)
    return scanRecords
  },

  // UC6 Retrieve the current user's family members' restriction summary
  async getRestrictionSummary(): Promise<FamilyRestrictionSumRes> {
    await delay(550)
    const state = readState()
    return {
      familyMembers: state.members.map((member) => ({
        userId: member.memberId,
        name: member.profileName,
        isActive: true,
        restrictions: [...member.commonRequirements, ...member.restrictions].map((code) => ({
          code: code,
          displayName: formatCode(code),
          severity: 'STRICT_AVOID',
        }),
        ),
      })),
    }
  },
}
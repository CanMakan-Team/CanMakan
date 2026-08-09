import { ApiError } from '../shared/api/apiErrors'
import { formatCode } from '../features/family/lib/profileOptions'
import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMe,
  FamilyMember,
  FamilyProfileInput,
  InvitationResponse,
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
 *
 * @author Amelia
 * @author YangMaowei
 * @author Khai
 */
const stateKey = 'canmakan.mock.family'
const delay = (milliseconds = 450) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

// Read the state
function readState(): MockFamilyState {
  const stored = localStorage.getItem(stateKey)
  return stored ? (JSON.parse(stored) as MockFamilyState) : structuredClone(initialFamilyState)
}

// Write the state
function writeState(state: MockFamilyState) {
  localStorage.setItem(stateKey, JSON.stringify(state))
  window.dispatchEvent(new Event('canmakan:family-data-changed'))
}

// Define the mock family repository
export const mockFamilyRepository = {
  async getMembers(): Promise<FamilyMember[]> {
    await delay()
    return readState().members
  },

  async searchExistingUser(email: string): Promise<ExistingUserSearchResult> {
    await delay(650)
    if (email.toLowerCase() === 'error@demo.test') {
      throw new ApiError('Controlled demo error: user search is unavailable.')
    }
    const normalized = email.trim().toLowerCase()
    const match = existingUsers[normalized]
    if (!match) {
      return {
        userId: null,
        displayName: null,
        maskedEmail: normalized.replace(/^(.{1}).*(@.*)$/, '$1***$2'),
        accountStatus: 'NOT_REGISTERED',
        familyLinkStatus: 'NOT_LINKED',
      }
    }
    const isNowLinked = readState().members.some(
      (member) => member.memberId === match.userId,
    )
    return {
      ...match,
      familyLinkStatus: isNowLinked ? 'ALREADY_LINKED' : match.familyLinkStatus,
    }
  },

  // Create an invitation
  async createInvitation(email: string): Promise<InvitationResponse> {
    await delay(650)
    const normalized = email.trim().toLowerCase()
    const token = `mock-token-${Date.now()}`
    const code = `MOCK${String(Date.now()).slice(-4)}`
    return {
      invitationId: Date.now(),
      invitedEmail: normalized,
      invitationToken: token,
      inviteCode: code,
      inviteUrl: `${window.location.origin}/invite/${token}`,
      status: 'PENDING',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      inviteeRegistered: Boolean(existingUsers[normalized]),
    }
  },

  // Claim an invitation
  async claimInvitation(_invitationToken: string): Promise<FamilyMe> {
    await delay(400)
    return {
      familyId: 1,
      familyName: 'Mock Family',
      memberRole: 'MEMBER',
      selfProfileId: 1,
      createdByUserId: 1,
    }
  },

  // Link an existing user
  async linkExistingUser(userId: number): Promise<FamilyMember> {
    await delay(650)
    const match = Object.values(existingUsers).find((user) => user.userId === userId)
    if (!match || match.familyLinkStatus !== 'NOT_LINKED') {
      throw new ApiError('This user cannot be linked in the current state.')
    }
    if (match.userId == null || !match.displayName) {
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

  // Create a dependant profile
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

  // Update a dependant profile
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

  // Get the active profile
  async getActiveProfile(): Promise<ActiveProfile> {
    await delay(250)
    return readState().activeProfile
  },

  // Set the active profile
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

  // Get the scan history
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
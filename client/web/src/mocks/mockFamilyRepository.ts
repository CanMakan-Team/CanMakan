import { ApiError } from '../shared/api/apiErrors'
import { formatCode } from '../features/family/lib/profileOptions'
import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMe,
  FamilyMember,
  FamilyProfileInput,
  InvitationResponse,
  Relationship,
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
  async createInvitation(
    email: string,
    relationship: Exclude<Relationship, 'SELF'>,
  ): Promise<InvitationResponse> {
    await delay(650)
    const normalized = email.trim().toLowerCase()
    const token = `mock-token-${Date.now()}`
    const code = `MOCK${String(Date.now()).slice(-4)}`
    return {
      invitationId: Date.now(),
      invitedEmail: normalized,
      relationship,
      invitationToken: token,
      inviteCode: code,
      inviteUrl: `${window.location.origin}/invite/${token}`,
      status: 'PENDING',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      inviteeRegistered: Boolean(existingUsers[normalized]),
      emailSent: false,
    }
  },

  // Claim an invitation
  async claimInvitation(_invitationToken: string, _profileName?: string): Promise<FamilyMe> {
    await delay(400)
    return {
      familyId: 1,
      familyName: 'Mock Family',
      memberRole: 'MEMBER',
      selfProfileId: 1,
      createdByUserId: 1,
    }
  },

  async previewInvitation(_invitationToken: string) {
    await delay(50)
    return {
      invitedEmail: 'invitee@example.com',
      familyName: 'Mock Family',
      expired: false,
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
      profileId: match.userId,
      linkedUserId: match.userId,
      profileName: match.displayName,
      relationship: 'OTHER',
      commonRequirements: [],
      restrictions: [],
      source: 'REGISTERED_USER',
      maskedEmail: match.maskedEmail,
      memberRole: 'MEMBER',
      profileActive: true,
    }
    state.members.push(member)
    writeState(state)
    return member
  },

  // Create a dependant profile
  async createProfile(input: FamilyProfileInput): Promise<FamilyMember> {
    await delay(650)
    const state = readState()
    const id = Date.now()
    const member: FamilyMember = {
      memberId: id,
      profileId: id,
      linkedUserId: null,
      ...input,
      source: 'DEPENDANT_PROFILE',
      memberRole: null,
      profileActive: true,
    }
    state.members.push(member)
    writeState(state)
    return member
  },

  async getProfiles() {
    await delay(250)
    return readState().members.map((member) => ({
      id: member.profileId,
      profileName: member.profileName,
      familyId: 1,
      relationship: member.relationship,
      initials: member.profileName.slice(0, 2).toUpperCase(),
      isPrimary: member.memberRole === 'PRIMARY_ADMIN',
      active: member.profileActive,
    }))
  },

  async updateProfile(
    profileId: number,
    input: FamilyProfileInput,
  ): Promise<FamilyMember> {
    await delay(650)
    const state = readState()
    const index = state.members.findIndex((member) => member.profileId === profileId)
    if (index < 0) throw new ApiError('The family profile could not be found.')
    state.members[index] = {
      ...state.members[index],
      profileName: input.profileName,
      relationship: input.relationship,
      commonRequirements: input.commonRequirements,
      restrictions: input.restrictions,
    }
    if (state.activeProfile.profileId === profileId) {
      state.activeProfile.profileName = input.profileName
    }
    writeState(state)
    return state.members[index]
  },

  async setProfileActive(profileId: number, active: boolean) {
    await delay(400)
    const state = readState()
    const member = state.members.find((candidate) => candidate.profileId === profileId)
    if (!member) throw new ApiError('The family profile could not be found.')
    if (!active && member.memberRole === 'PRIMARY_ADMIN') {
      throw new ApiError('Cannot deactivate the family admin profile.', 403)
    }
    member.profileActive = active
    writeState(state)
    return {
      id: member.profileId,
      profileName: member.profileName,
      active: member.profileActive,
    }
  },

  async removeMember(userId: number) {
    await delay(400)
    const state = readState()
    const admins = state.members.filter(
      (m) => m.memberRole === 'PRIMARY_ADMIN' && m.profileActive,
    )
    const target = state.members.find((m) => m.linkedUserId === userId)
    if (!target) throw new ApiError('The family member could not be found.')
    if (target.memberRole === 'PRIMARY_ADMIN' && admins.length <= 1) {
      throw new ApiError('Cannot remove the last primary admin without an allowed transfer.')
    }
    state.members = state.members.filter((m) => m.linkedUserId !== userId)
    if (state.activeProfile.profileId === target.profileId && state.members[0]) {
      state.activeProfile = {
        profileId: state.members[0].profileId,
        profileName: state.members[0].profileName,
      }
    }
    writeState(state)
  },

  async removeDependantProfile(profileId: number) {
    await delay(400)
    const state = readState()
    const target = state.members.find(
      (m) => m.profileId === profileId && m.source === 'DEPENDANT_PROFILE',
    )
    if (!target) throw new ApiError('The dependant profile could not be found.')
    state.members = state.members.filter((m) => m.profileId !== profileId)
    writeState(state)
  },

  async getActiveProfile(): Promise<ActiveProfile> {
    await delay(250)
    return readState().activeProfile
  },

  async setActiveProfile(profileId: number): Promise<ActiveProfile> {
    await delay(500)
    const state = readState()
    const member = state.members.find(
      (candidate) => candidate.profileId === profileId && candidate.profileActive,
    )
    if (!member) throw new ApiError('The selected profile is unavailable.')
    state.activeProfile = {
      profileId,
      profileName: member.profileName,
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
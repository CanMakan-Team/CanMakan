import { ApiError } from '../shared/api/apiErrors'
import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMe,
  FamilyMember,
  FamilyProfileInput,
  ScanRecord,
} from '../shared/api/types'
import {
  existingUsers,
  initialFamilyState,
  scanRecords,
  type MockFamilyState,
} from './mockData'

/** UC8 Mock family repository
 * 
 * @author Amelia
 */
const stateKey = 'canmakan.mock.family'
const meKey = 'canmakan.mock.family.me'
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

function defaultMockMe(): FamilyMe {
  return {
    familyId: 1,
    familyName: 'Lim Family',
    memberRole: 'PRIMARY_ADMIN',
    selfProfileId: 101,
    createdByUserId: 101,
  }
}

function readMe(): FamilyMe | null {
  const stored = localStorage.getItem(meKey)
  if (stored === 'null') return null
  if (stored) return JSON.parse(stored) as FamilyMe
  return defaultMockMe()
}

function writeMe(me: FamilyMe | null) {
  localStorage.setItem(meKey, me === null ? 'null' : JSON.stringify(me))
  window.dispatchEvent(new Event('canmakan:family-me-changed'))
}

function currentSessionUserId(): number {
  const stored = localStorage.getItem('canmakan.session')
  if (!stored) return 101
  try {
    const session = JSON.parse(stored) as { userId?: number }
    return typeof session.userId === 'number' ? session.userId : 101
  } catch {
    return 101
  }
}

export const mockFamilyRepository = {

  // UC8 get my family
  async getMyFamily(): Promise<FamilyMe> {
    await delay(300)
    const me = readMe()
    if (!me) {
      throw new ApiError('You are not a member of a family circle.', 404)
    }
    return me
  },

  // UC8 create family
  async createFamily(familyName: string): Promise<FamilyMe> {
    await delay(650)
    const trimmed = familyName.trim()
    if (!trimmed) {
      throw new ApiError('Family name is required.', 400)
    }
    if (trimmed.length > 100) {
      throw new ApiError('Family name must be at most 100 characters.', 400)
    }
    if (readMe()) {
      throw new ApiError('You already belong to a family circle.', 409)
    }
    const me: FamilyMe = {
      familyId: Date.now(),
      familyName: trimmed,
      memberRole: 'PRIMARY_ADMIN',
      selfProfileId: Date.now() + 1,
      createdByUserId: currentSessionUserId(),
    }
    writeMe(me)
    return me
  },

  /** Demo helper: clear mock membership so create-circle empty state can be shown. */
  clearMyFamilyForDemo() {
    writeMe(null)
  },

  // UC8 get members
  async getMembers(): Promise<FamilyMember[]> {
    await delay()
    return readState().members
  },

  // UC8 search existing user
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

  // UC8 link existing user
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

  // UC8 create profile
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

  // UC8 update profile
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

  // UC8 get active profile
  async getActiveProfile(): Promise<ActiveProfile> {
    await delay(250)
    return readState().activeProfile
  },

  // UC8 set active profile
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

  // UC8 get scan history
  async getScanHistory(): Promise<ScanRecord[]> {
    await delay(550)
    return scanRecords
  },
}

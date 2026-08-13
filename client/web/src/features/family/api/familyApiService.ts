import { mockFamilyRepository } from '../../../mocks/mockFamilyRepository'
import { apiRequest, useMockApi } from '../../../shared/api/apiClient'
import { ApiError } from '../../../shared/api/apiErrors'
import type {
  ActiveProfile,
  DependantProfileResponse,
  ExistingUserSearchResult,
  FamilyMe,
  FamilyMember,
  FamilyProfileInput,
  InvitationResponse,
  ScanRecord,
  FamilyRestrictionSumRes,
} from '../../../shared/api/types'

export interface FamilyProfileSummary {
  id: number
  profileName: string
  familyId: number | null
  relationship: string
  initials: string
  isPrimary: boolean
  active: boolean
}

/** Family service endpoints (UC8 / UC9 / UC6 / UC11 / UC12).
 *
 * @author Amelia
 * @author Khai
 */

/** Family service endpoints. */
export const familyEndpoints = {
  families: '/api/families',
  me: '/api/families/me',
  members: '/api/families/me/members',
  userSearch: '/api/families/me/user-search',
  invitations: '/api/families/me/invitations',
  claimInvitation: '/api/families/me/invitations/claim',
  profiles: '/api/families/me/profiles',
  activeProfile: '/api/families/me/active-profile',
  restrictionSummary: '/api/families/me/restriction-summary',
  scans: '/api/families/me/scans',
} as const

/** Family service API. */
export const familyApiService = {
  /** Get the family of the current user. */
  getMyFamily: () => apiRequest<FamilyMe>(familyEndpoints.me),

  /** Resolve optional membership without turning a normal 404 into a UI error. */
  async getMyFamilyOrNull(): Promise<FamilyMe | null> {
    try {
      return await apiRequest<FamilyMe>(familyEndpoints.me)
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null
      throw error
    }
  },

  /** Create a new family. */
  createFamily: (familyName: string) =>
    apiRequest<FamilyMe>(familyEndpoints.families, {
      method: 'POST',
      body: JSON.stringify({ familyName }),
    }),

  /** Get the members of the current family. */
  getMembers: () =>
    useMockApi
      ? mockFamilyRepository.getMembers()
      : apiRequest<FamilyMember[]>(familyEndpoints.members),

  /** Get the profiles of the current family. */
  getProfiles: () =>
    useMockApi
      ? mockFamilyRepository.getProfiles()
      : apiRequest<FamilyProfileSummary[]>(familyEndpoints.profiles),

  /** Account settings always uses server-authoritative profile data. */
  getAccountProfiles: () =>
    apiRequest<FamilyProfileSummary[]>(familyEndpoints.profiles),

  /** Search for an existing user by email. */
  searchExistingUser: (email: string) =>
    useMockApi
      ? mockFamilyRepository.searchExistingUser(email)
      : apiRequest<ExistingUserSearchResult>(
          `${familyEndpoints.userSearch}?email=${encodeURIComponent(email)}`,
        ),

  /** Create a new invitation. */
  createInvitation: (email: string) =>
    useMockApi
      ? mockFamilyRepository.createInvitation(email)
      : apiRequest<InvitationResponse>(familyEndpoints.invitations, {
          method: 'POST',
          body: JSON.stringify({ email }),
        }),

  /** Claim an invitation. */
  claimInvitation: (invitationToken: string) =>
    useMockApi
      ? mockFamilyRepository.claimInvitation(invitationToken)
      : apiRequest<FamilyMe>(familyEndpoints.claimInvitation, {
          method: 'POST',
          body: JSON.stringify({ invitationToken }),
        }),

  /** Create a new profile. */
  createProfile: (input: FamilyProfileInput) =>
    useMockApi
      ? mockFamilyRepository.createProfile(input)
      : apiRequest<DependantProfileResponse>(familyEndpoints.profiles, {
          method: 'POST',
          body: JSON.stringify({
            profileName: input.profileName,
            relationship: input.relationship,
            commonRequirements: input.commonRequirements,
            restrictions: input.restrictions,
          }),
        }),

  /** Update a profile. */
  updateProfile: (
    profileId: number,
    input: FamilyProfileInput,
    options?: { includeRestrictions?: boolean },
  ) =>
    useMockApi
      ? mockFamilyRepository.updateProfile(profileId, input)
      : apiRequest<FamilyMember>(`${familyEndpoints.profiles}/${profileId}`, {
          method: 'PUT',
          body: JSON.stringify(
            options?.includeRestrictions === false
              ? {
                  profileName: input.profileName,
                  relationship: input.relationship,
                }
              : {
                  profileName: input.profileName,
                  relationship: input.relationship,
                  commonRequirements: input.commonRequirements,
                  restrictions: input.restrictions,
                },
          ),
        }),

  /** Set the active status of a profile. */
  setProfileActive: (profileId: number, active: boolean) =>
    useMockApi
      ? mockFamilyRepository.setProfileActive(profileId, active)
      : apiRequest<{ id: number; profileName: string; active: boolean }>(
          `${familyEndpoints.profiles}/${profileId}`,
          {
            method: 'PATCH',
            body: JSON.stringify({ active }),
          },
        ),

  /** Remove a family member. */
  removeMember: (userId: number) =>
    useMockApi
      ? mockFamilyRepository.removeMember(userId)
      : apiRequest<void>(`${familyEndpoints.members}/${userId}`, {
          method: 'DELETE',
        }),

  /** Remove a dependant profile. */
  removeDependantProfile: (profileId: number) =>
    useMockApi
      ? mockFamilyRepository.removeDependantProfile(profileId)
      : apiRequest<void>(`${familyEndpoints.profiles}/${profileId}`, {
          method: 'DELETE',
        }),

  /** Get the active profile. */
  getActiveProfile: () =>
    useMockApi
      ? mockFamilyRepository.getActiveProfile()
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile),

  /** Set the active profile. */
  setActiveProfile: (profileId: number) =>
    useMockApi
      ? mockFamilyRepository.setActiveProfile(profileId)
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile, {
          method: 'PUT',
          body: JSON.stringify({ profileId }),
        }),

  /** Get the restriction summary. */
  getRestrictionSummary: () =>
    useMockApi
      ? mockFamilyRepository.getRestrictionSummary()
      : apiRequest<FamilyRestrictionSumRes>(familyEndpoints.restrictionSummary),

  /** Get the scan history. */
  getScanHistory: () =>
    useMockApi
      ? mockFamilyRepository.getScanHistory()
      : apiRequest<ScanRecord[]>(familyEndpoints.scans),
}

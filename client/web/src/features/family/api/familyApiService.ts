import { mockFamilyRepository } from '../../../mocks/mockFamilyRepository'
import { apiRequest, useMockApi } from '../../../shared/api/apiClient'
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

/** Family service endpoints (UC8 / UC9 / UC6). 
 * 
 * @author Amelia
 * @author Khai
*/
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

// Define the family api service
export const familyApiService = {
  // Get the my family
  getMyFamily: () => apiRequest<FamilyMe>(familyEndpoints.me),

  // Create a family
  createFamily: (familyName: string) =>
    apiRequest<FamilyMe>(familyEndpoints.families, {
      method: 'POST',
      body: JSON.stringify({ familyName }),
    }),

  // Get the members
  getMembers: () =>
    useMockApi
      ? mockFamilyRepository.getMembers()
      : apiRequest<FamilyMember[]>(familyEndpoints.members),

  // Search for an existing user
  searchExistingUser: (email: string) =>
    useMockApi
      ? mockFamilyRepository.searchExistingUser(email)
      : apiRequest<ExistingUserSearchResult>(
          `${familyEndpoints.userSearch}?email=${encodeURIComponent(email)}`,
        ),

  // Create an invitation
  createInvitation: (email: string) =>
    useMockApi
      ? mockFamilyRepository.createInvitation(email)
      : apiRequest<InvitationResponse>(familyEndpoints.invitations, {
          method: 'POST',
          body: JSON.stringify({ email }),
        }),

  // Claim an invitation
  claimInvitation: (invitationToken: string) =>
    useMockApi
      ? mockFamilyRepository.claimInvitation(invitationToken)
      : apiRequest<FamilyMe>(familyEndpoints.claimInvitation, {
          method: 'POST',
          body: JSON.stringify({ invitationToken }),
        }),

  // Create a dependant profile
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

  // Update a dependant profile
  updateProfile: (memberId: number, input: FamilyProfileInput) =>
    useMockApi
      ? mockFamilyRepository.updateProfile(memberId, input)
      : apiRequest<FamilyMember>(`${familyEndpoints.profiles}/${memberId}`, {
          method: 'PUT',
          body: JSON.stringify(input),
        }),

  // Get the active profile
  getActiveProfile: () =>
    useMockApi
      ? mockFamilyRepository.getActiveProfile()
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile),

  // Set the active profile
  setActiveProfile: (memberId: number) =>
    useMockApi
      ? mockFamilyRepository.setActiveProfile(memberId)
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile, {
          method: 'PUT',
          body: JSON.stringify({ memberId }),
        }),

  // Get the restriction summary
  getRestrictionSummary: () =>
    useMockApi
      ? mockFamilyRepository.getRestrictionSummary()
      : apiRequest<FamilyRestrictionSumRes>(familyEndpoints.restrictionSummary),

  // Get the scan history
  getScanHistory: () =>
    useMockApi
      ? mockFamilyRepository.getScanHistory()
      : apiRequest<ScanRecord[]>(familyEndpoints.scans),
}

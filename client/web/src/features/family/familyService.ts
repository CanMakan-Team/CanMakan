import { mockFamilyRepository } from '../../mocks/mockFamilyRepository'
import { apiRequest, useMockApi } from '../../shared/api/apiClient'
import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMe,
  FamilyMember,
  FamilyProfileInput,
  ScanRecord,
} from '../../shared/api/types'

/** UC8 Family service endpoints
 * 
 * @author Amelia
 * 
 * This is a constant object that contains the endpoints for the family service.
 * The endpoints are used to make requests to the family service.
 * The endpoints are used to get the family information, members, and profiles.
 * 
 */
export const familyEndpoints = {
  families: '/api/families',
  me: '/api/families/me',
  members: '/api/families/me/members',
  userSearch: '/api/families/me/user-search',
  linkMember: '/api/families/me/members/link',
  profiles: '/api/families/me/profiles',
  activeProfile: '/api/families/me/active-profile',
  restrictionSummary: '/api/families/me/restriction-summary',
  scans: '/api/families/me/scans',
} as const

/** UC8 Family service
 * 
 * @author Amelia
 */

export const familyService = {

  // Retrieve the current user's family
  getMyFamily: () => apiRequest<FamilyMe>(familyEndpoints.me),

  // Create a new family
  createFamily: (familyName: string) =>
    apiRequest<FamilyMe>(familyEndpoints.families, {
      method: 'POST',
      body: JSON.stringify({ familyName }),
    }),

  // Retrieve the current user's family members
  getMembers: () =>
    apiRequest<FamilyMember[]>(familyEndpoints.members),

  // Search for an existing user
  searchExistingUser: (email: string) =>
    useMockApi
      ? mockFamilyRepository.searchExistingUser(email)
      : apiRequest<ExistingUserSearchResult | null>(
          `${familyEndpoints.userSearch}?email=${encodeURIComponent(email)}`,
        ),

  // Link an existing user to the current user's family
  linkExistingUser: (userId: number) =>
    useMockApi
      ? mockFamilyRepository.linkExistingUser(userId)
      : apiRequest<FamilyMember>(familyEndpoints.linkMember, {
          method: 'POST',
          body: JSON.stringify({ userId }),
        }),

  // Create a new profile for the current user
  createProfile: (input: FamilyProfileInput) =>
    useMockApi
      ? mockFamilyRepository.createProfile(input)
      : apiRequest<FamilyMember>(familyEndpoints.profiles, {
          method: 'POST',
          body: JSON.stringify(input),
        }),

  // Update a profile for the current user
  updateProfile: (memberId: number, input: FamilyProfileInput) =>
    useMockApi
      ? mockFamilyRepository.updateProfile(memberId, input)
      : apiRequest<FamilyMember>(`${familyEndpoints.profiles}/${memberId}`, {
          method: 'PUT',
          body: JSON.stringify(input),
        }),

  // Retrieve the current user's active profile
  getActiveProfile: () =>
    useMockApi
      ? mockFamilyRepository.getActiveProfile()
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile),

  // Set the current user's active profile
  setActiveProfile: (memberId: number) =>
    useMockApi
      ? mockFamilyRepository.setActiveProfile(memberId)
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile, {
          method: 'PUT',
          body: JSON.stringify({ memberId }),
        }),

  // Retrieve the current user's restriction summary
  getRestrictionSummary: () =>
    useMockApi
      ? mockFamilyRepository.getMembers()
      : apiRequest<FamilyMember[]>(familyEndpoints.restrictionSummary),

  // Retrieve the current user's scan history
  getScanHistory: () =>
    useMockApi
      ? mockFamilyRepository.getScanHistory()
      : apiRequest<ScanRecord[]>(familyEndpoints.scans),
}

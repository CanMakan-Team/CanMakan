import { mockFamilyRepository } from '../../../mocks/mockFamilyRepository'
import { apiRequest, useMockApi } from '../../../shared/api/apiClient'
import { ApiError } from '../../../shared/api/apiErrors'
import type {
  FamilyMe,
  FamilyProfileInput,
  Relationship,
} from '../../../shared/api/types'
import { httpFamilyDataApi } from './familyApiHttp'
import { familyEndpoints } from './familyEndpoints'
import type { FamilyProfileSummary } from './familyTypes'

export { familyEndpoints }
export type { FamilyProfileSummary }

const familyDataApi = useMockApi ? mockFamilyRepository : httpFamilyDataApi

/** Family service API. */
export const familyApiService = {
  getMyFamily: () => apiRequest<FamilyMe>(familyEndpoints.me),

  async getMyFamilyOrNull(): Promise<FamilyMe | null> {
    try {
      return await apiRequest<FamilyMe>(familyEndpoints.me)
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null
      throw error
    }
  },

  createFamily: (familyName: string) =>
    apiRequest<FamilyMe>(familyEndpoints.families, {
      method: 'POST',
      body: JSON.stringify({ familyName }),
    }),

  getMembers: () => familyDataApi.getMembers(),

  getProfiles: () => familyDataApi.getProfiles(),

  getAccountProfiles: () =>
    apiRequest<FamilyProfileSummary[]>(familyEndpoints.profiles),

  searchExistingUser: (email: string) => familyDataApi.searchExistingUser(email),

  createInvitation: (email: string, relationship: Exclude<Relationship, 'SELF'>) =>
    familyDataApi.createInvitation(email, relationship),

  claimInvitation: (invitationToken: string, profileName?: string) =>
    familyDataApi.claimInvitation(invitationToken, profileName),

  previewInvitation: (invitationToken: string) =>
    familyDataApi.previewInvitation(invitationToken),

  createProfile: (input: FamilyProfileInput) => familyDataApi.createProfile(input),

  updateProfile: (
    profileId: number,
    input: FamilyProfileInput,
    options?: { includeRestrictions?: boolean },
  ) => familyDataApi.updateProfile(profileId, input, options),

  setProfileActive: (profileId: number, active: boolean) =>
    familyDataApi.setProfileActive(profileId, active),

  removeMember: (userId: number) => familyDataApi.removeMember(userId),

  removeDependantProfile: (profileId: number) =>
    familyDataApi.removeDependantProfile(profileId),

  getActiveProfile: () => familyDataApi.getActiveProfile(),

  setActiveProfile: (profileId: number) => familyDataApi.setActiveProfile(profileId),

  getRestrictionSummary: () => familyDataApi.getRestrictionSummary(),

  getScanHistory: () => familyDataApi.getScanHistory(),
}

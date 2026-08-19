import { apiRequest } from '../../../shared/api/apiClient'
import type {
  ActiveProfile,
  DependantProfileResponse,
  ExistingUserSearchResult,
  FamilyMe,
  FamilyMember,
  FamilyProfileInput,
  InvitationResponse,
  InvitationPreviewResponse,
  Relationship,
  ScanRecord,
  FamilyRestrictionSumRes,
} from '../../../shared/api/types'
import { familyEndpoints } from './familyEndpoints'
import type { FamilyProfileSummary } from './familyTypes'

/** HTTP-backed family data operations (no mock branching). */
export const httpFamilyDataApi = {
  getMembers: () => apiRequest<FamilyMember[]>(familyEndpoints.members),

  getProfiles: () =>
    apiRequest<FamilyProfileSummary[]>(familyEndpoints.profiles),

  searchExistingUser: (email: string) =>
    apiRequest<ExistingUserSearchResult>(
      `${familyEndpoints.userSearch}?email=${encodeURIComponent(email)}`,
    ),

  createInvitation: (email: string, relationship: Exclude<Relationship, 'SELF'>) =>
    apiRequest<InvitationResponse>(familyEndpoints.invitations, {
      method: 'POST',
      body: JSON.stringify({ email, relationship }),
    }),

  claimInvitation: (invitationToken: string, profileName?: string) =>
    apiRequest<FamilyMe>(familyEndpoints.claimInvitation, {
      method: 'POST',
      body: JSON.stringify({ invitationToken, profileName }),
    }),

  previewInvitation: (invitationToken: string) =>
    apiRequest<InvitationPreviewResponse>(
      familyEndpoints.invitationPreview(invitationToken),
      { authentication: 'none' },
    ),

  createProfile: (input: FamilyProfileInput) =>
    apiRequest<DependantProfileResponse>(familyEndpoints.profiles, {
      method: 'POST',
      body: JSON.stringify({
        profileName: input.profileName,
        relationship: input.relationship,
        commonRequirements: input.commonRequirements,
        restrictions: input.restrictions,
      }),
    }),

  updateProfile: (
    profileId: number,
    input: FamilyProfileInput,
    options?: { includeRestrictions?: boolean },
  ) =>
    apiRequest<FamilyMember>(`${familyEndpoints.profiles}/${profileId}`, {
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

  setProfileActive: (profileId: number, active: boolean) =>
    apiRequest<{ id: number; profileName: string; active: boolean }>(
      `${familyEndpoints.profiles}/${profileId}`,
      {
        method: 'PATCH',
        body: JSON.stringify({ active }),
      },
    ),

  removeMember: (userId: number) =>
    apiRequest<void>(`${familyEndpoints.members}/${userId}`, {
      method: 'DELETE',
    }),

  removeDependantProfile: (profileId: number) =>
    apiRequest<void>(`${familyEndpoints.profiles}/${profileId}`, {
      method: 'DELETE',
    }),

  getActiveProfile: () =>
    apiRequest<ActiveProfile>(familyEndpoints.activeProfile),

  setActiveProfile: (profileId: number) =>
    apiRequest<ActiveProfile>(familyEndpoints.activeProfile, {
      method: 'PUT',
      body: JSON.stringify({ profileId }),
    }),

  getRestrictionSummary: () =>
    apiRequest<FamilyRestrictionSumRes>(familyEndpoints.restrictionSummary),

  getScanHistory: () => apiRequest<ScanRecord[]>(familyEndpoints.scans),
}

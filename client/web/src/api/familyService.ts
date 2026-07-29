import { mockFamilyRepository } from '../mocks/mockFamilyRepository'
import { apiRequest, useMockApi } from './apiClient'
import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMember,
  FamilyProfileInput,
  ScanRecord,
} from './types'

export const familyEndpoints = {
  members: '/api/families/me/members',
  userSearch: '/api/families/me/user-search',
  linkMember: '/api/families/me/members/link',
  profiles: '/api/families/me/profiles',
  activeProfile: '/api/families/me/active-profile',
  restrictionSummary: '/api/families/me/restriction-summary',
  scans: '/api/families/me/scans',
} as const

export const familyService = {
  getMembers: () =>
    useMockApi
      ? mockFamilyRepository.getMembers()
      : apiRequest<FamilyMember[]>(familyEndpoints.members),
  searchExistingUser: (email: string) =>
    useMockApi
      ? mockFamilyRepository.searchExistingUser(email)
      : apiRequest<ExistingUserSearchResult | null>(
          `${familyEndpoints.userSearch}?email=${encodeURIComponent(email)}`,
        ),
  linkExistingUser: (userId: number) =>
    useMockApi
      ? mockFamilyRepository.linkExistingUser(userId)
      : apiRequest<FamilyMember>(familyEndpoints.linkMember, {
          method: 'POST',
          body: JSON.stringify({ userId }),
        }),
  createProfile: (input: FamilyProfileInput) =>
    useMockApi
      ? mockFamilyRepository.createProfile(input)
      : apiRequest<FamilyMember>(familyEndpoints.profiles, {
          method: 'POST',
          body: JSON.stringify(input),
        }),
  updateProfile: (memberId: number, input: FamilyProfileInput) =>
    useMockApi
      ? mockFamilyRepository.updateProfile(memberId, input)
      : apiRequest<FamilyMember>(`${familyEndpoints.profiles}/${memberId}`, {
          method: 'PUT',
          body: JSON.stringify(input),
        }),
  getActiveProfile: () =>
    useMockApi
      ? mockFamilyRepository.getActiveProfile()
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile),
  setActiveProfile: (memberId: number) =>
    useMockApi
      ? mockFamilyRepository.setActiveProfile(memberId)
      : apiRequest<ActiveProfile>(familyEndpoints.activeProfile, {
          method: 'PUT',
          body: JSON.stringify({ memberId }),
        }),
  getRestrictionSummary: () =>
    useMockApi
      ? mockFamilyRepository.getMembers()
      : apiRequest<FamilyMember[]>(familyEndpoints.restrictionSummary),
  getScanHistory: () =>
    useMockApi
      ? mockFamilyRepository.getScanHistory()
      : apiRequest<ScanRecord[]>(familyEndpoints.scans),
}

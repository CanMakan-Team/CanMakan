import { apiRequest } from '../../../shared/api/apiClient'

export type ProfileRestrictionSeverity = 'STRICT_AVOID' | 'INTOLERANCE'

export interface DietaryRestrictionOption {
  id: number
  code: string
  displayName: string
  category: string
  description?: string | null
}

export interface SelfProfileResponse {
  profileId: number
  profileName: string
  relationship: 'SELF'
  active: boolean
  restrictions: Record<string, ProfileRestrictionSeverity>
}

export const selfProfileEndpoints = {
  catalog: '/api/restrictions',
  create: '/api/profiles/me',
  me: '/api/profiles/me',
} as const

export const selfProfileApiService = {
  getCatalog: () =>
    apiRequest<DietaryRestrictionOption[]>(selfProfileEndpoints.catalog),

  createSelfProfile: (
    profileName: string,
    restrictions: Record<number, ProfileRestrictionSeverity>,
  ) =>
    apiRequest<SelfProfileResponse>(selfProfileEndpoints.create, {
      method: 'POST',
      body: JSON.stringify({ profileName, restrictions }),
      retryAuthentication: false,
    }),

  // Fetches the caller's existing SELF profile so an edit form can be
  // pre-populated. Callers should expect a 404 ApiError when no SELF
  // profile has been created yet.
  getSelfProfile: () =>
    apiRequest<SelfProfileResponse>(selfProfileEndpoints.me),

  updateSelfProfile: (
    profileName: string,
    restrictions: Record<number, ProfileRestrictionSeverity>,
  ) =>
    apiRequest<SelfProfileResponse>(selfProfileEndpoints.me, {
      method: 'PUT',
      body: JSON.stringify({ profileName, restrictions }),
      retryAuthentication: false,
    }),
}

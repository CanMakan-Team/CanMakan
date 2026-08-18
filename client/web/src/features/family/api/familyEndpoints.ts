/** Family service HTTP paths (UC8 / UC9 / UC6 / UC11 / UC12). */
export const familyEndpoints = {
  families: '/api/families',
  me: '/api/families/me',
  members: '/api/families/me/members',
  userSearch: '/api/families/me/user-search',
  invitations: '/api/families/me/invitations',
  claimInvitation: '/api/families/me/invitations/claim',
  invitationPreview: (token: string) =>
    `/api/invitations/${encodeURIComponent(token)}/preview`,
  profiles: '/api/families/me/profiles',
  activeProfile: '/api/families/me/active-profile',
  restrictionSummary: '/api/families/me/restriction-summary',
  scans: '/api/families/me/scans',
} as const

export interface PendingRegistrationOnboarding {
  email: string
  profileName: string
  invitationToken?: string
}

let pending: PendingRegistrationOnboarding | null = null

function normalizeEmail(email: string) {
  return email.trim().toLowerCase()
}

export const pendingRegistrationOnboardingStore = {
  request(value: PendingRegistrationOnboarding) {
    pending = {
      email: normalizeEmail(value.email),
      profileName: value.profileName.trim(),
      invitationToken: value.invitationToken?.trim() || undefined,
    }
  },

  peekForEmail(email: string): PendingRegistrationOnboarding | null {
    if (pending?.email !== normalizeEmail(email)) {
      pending = null
      return null
    }
    return pending
  },

  clear() {
    pending = null
  },
}

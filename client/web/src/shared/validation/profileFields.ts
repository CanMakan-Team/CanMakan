export const MAX_PROFILE_NAME_LENGTH = 100

export function getProfileNameError(value: string): string {
  const normalized = value.trim()
  if (!normalized) return 'Enter a profile name.'
  if (normalized.length > MAX_PROFILE_NAME_LENGTH) {
    return 'Profile name must not exceed 100 characters.'
  }
  return ''
}

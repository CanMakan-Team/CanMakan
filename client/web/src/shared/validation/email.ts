import { MAX_EMAIL_LENGTH } from './authFields'

/**
 * Shared email format rules for auth forms.
 * Requires a dotted domain (rejects `user@host` without a TLD-like segment),
 * matching mobile `RegistrationViewModel` and backend `RegistrationRequest` / `LoginRequest`.
 */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(value: string): boolean {
  return getEmailValidationError(value) === null
}

/** Client-side email checks that mirror backend auth DTOs. */
export function getEmailValidationError(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return 'Email is required.'
  }
  if (trimmed.length > MAX_EMAIL_LENGTH) {
    return 'Email must not exceed 255 characters.'
  }
  if (!EMAIL_PATTERN.test(trimmed)) {
    return 'Enter a valid email address.'
  }
  return null
}

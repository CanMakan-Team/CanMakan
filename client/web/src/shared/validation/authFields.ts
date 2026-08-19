/**
 * Auth field limits shared with backend RegistrationRequest / LoginRequest
 * and mobile RegistrationViewModel — checked on the client to avoid round-trips.
 */

export const MAX_EMAIL_LENGTH = 255
export const MIN_PASSWORD_LENGTH = 8
/** BCrypt truncates / rejects beyond this UTF-8 byte length. */
export const MAX_PASSWORD_UTF8_BYTES = 72

/** Same wording as backend `RegistrationRequest` password `@Pattern` message. */
export const PASSWORD_STRENGTH_MESSAGE =
  'Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character.'

const textEncoder = new TextEncoder()

export function utf8ByteLength(value: string): number {
  return textEncoder.encode(value).length
}

export function isPasswordWithinBcryptLimit(password: string): boolean {
  return utf8ByteLength(password) <= MAX_PASSWORD_UTF8_BYTES
}

export function meetsRegistrationPasswordPolicy(password: string): boolean {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return false
  }
  let hasUpper = false
  let hasLower = false
  let hasDigit = false
  let hasSpecial = false
  for (const character of password) {
    if (character >= 'A' && character <= 'Z') {
      hasUpper = true
    } else if (character >= 'a' && character <= 'z') {
      hasLower = true
    } else if (character >= '0' && character <= '9') {
      hasDigit = true
    } else {
      hasSpecial = true
    }
  }
  return hasUpper && hasLower && hasDigit && hasSpecial
}

/** Registration-only password checks (login does not enforce strength). */
export function getRegistrationPasswordError(password: string): string | null {
  if (!password) {
    return 'Password is required.'
  }
  if (password.length < MIN_PASSWORD_LENGTH) {
    return 'Password must be at least 8 characters.'
  }
  if (!isPasswordWithinBcryptLimit(password)) {
    return 'Password must not exceed 72 UTF-8 bytes.'
  }
  if (!meetsRegistrationPasswordPolicy(password)) {
    return PASSWORD_STRENGTH_MESSAGE
  }
  return null
}

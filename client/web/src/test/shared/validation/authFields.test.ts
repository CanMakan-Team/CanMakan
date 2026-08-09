import { describe, expect, it } from 'vitest'
import {
  getRegistrationPasswordError,
  PASSWORD_STRENGTH_MESSAGE,
} from '../../../shared/validation/authFields'
import { getEmailValidationError } from '../../../shared/validation/email'

describe('auth field validation', () => {
  it('requires a dotted email domain', () => {
    expect(getEmailValidationError('test1@abc')).toBe('Enter a valid email address.')
    expect(getEmailValidationError('person@example.com')).toBeNull()
  })

  it('enforces registration password strength', () => {
    expect(getRegistrationPasswordError('Password1')).toBe(PASSWORD_STRENGTH_MESSAGE)
    expect(getRegistrationPasswordError('Password1!')).toBeNull()
  })
})

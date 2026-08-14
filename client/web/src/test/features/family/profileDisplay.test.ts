import { describe, expect, it } from 'vitest'
import { profileDisplayCaption } from '../../../features/family/lib/profileDisplay'

describe('profileDisplayCaption', () => {
  const admin = {
    profileId: 77,
    relationship: 'SELF' as const,
    memberRole: 'PRIMARY_ADMIN',
  }
  const invitee = {
    profileId: 99,
    relationship: 'SELF' as const,
    memberRole: 'MEMBER',
  }
  const child = {
    profileId: 88,
    relationship: 'CHILD' as const,
    memberRole: null,
  }

  it('shows Admin and Self on the signed-in admin row', () => {
    expect(
      profileDisplayCaption(admin, { selfProfileId: 77, isPrimaryAdmin: true }),
    ).toBe('Admin · Self')
  })

  it('hides invitee SELF from the admin', () => {
    expect(
      profileDisplayCaption(invitee, { selfProfileId: 77, isPrimaryAdmin: true }),
    ).toBe('')
  })

  it('shows dependant relationship only to the admin', () => {
    expect(
      profileDisplayCaption(child, { selfProfileId: 77, isPrimaryAdmin: true }),
    ).toBe('Child')
    expect(
      profileDisplayCaption(child, { selfProfileId: 99, isPrimaryAdmin: false }),
    ).toBe('')
  })

  it('shows Self without Admin on the signed-in invitee row', () => {
    expect(
      profileDisplayCaption(invitee, { selfProfileId: 99, isPrimaryAdmin: false }),
    ).toBe('Self')
    expect(
      profileDisplayCaption(admin, { selfProfileId: 99, isPrimaryAdmin: false }),
    ).toBe('Admin')
  })
})

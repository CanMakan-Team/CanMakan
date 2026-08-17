import { describe, expect, it } from 'vitest'
import { isCurrentAdminProfile } from '../../../features/family/lib/familyRoles'

describe('isCurrentAdminProfile', () => {
  it('matches the signed-in admin self profile', () => {
    expect(
      isCurrentAdminProfile({ profileId: 77, memberRole: 'PRIMARY_ADMIN' }, 77),
    ).toBe(true)
  })

  it('matches a primary admin row even if selfProfileId is missing', () => {
    expect(
      isCurrentAdminProfile({ profileId: 77, memberRole: 'PRIMARY_ADMIN' }, null),
    ).toBe(true)
  })

  it('does not match other household profiles', () => {
    expect(
      isCurrentAdminProfile({ profileId: 88, memberRole: 'MEMBER' }, 77),
    ).toBe(false)
    expect(
      isCurrentAdminProfile({ profileId: 88, memberRole: null }, 77),
    ).toBe(false)
  })
})

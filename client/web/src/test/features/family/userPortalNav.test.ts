import { describe, expect, it } from 'vitest'
import { userPortalSections } from '../../../features/family/lib/userPortalNav'

describe('userPortalSections', () => {
  it('hides family admin links for members', () => {
    const labels = userPortalSections({ hasFamily: true, isPrimaryAdmin: false })
      .flatMap((section) => section.items)
      .map((item) => item.label)

    expect(labels).toEqual(['Home', 'Dietary Profile', 'Account Settings'])
    expect(labels).not.toContain('Family Members')
  })

  it('offers create-circle only when the user has no family', () => {
    const labels = userPortalSections({ hasFamily: false, isPrimaryAdmin: false })
      .flatMap((section) => section.items)
      .map((item) => item.label)

    expect(labels).toContain('Create Family Circle')
    expect(labels).not.toContain('Family Members')
  })

  it('shows family admin tools for PRIMARY_ADMIN', () => {
    const labels = userPortalSections({ hasFamily: true, isPrimaryAdmin: true })
      .flatMap((section) => section.items)
      .map((item) => item.label)

    expect(labels).toContain('Family Members')
    expect(labels).toContain('Family Overview')
    expect(labels).not.toContain('Create Family Circle')
  })
})

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

  it('uses home, person, gear, and people icons for personal and create-circle links', () => {
    const items = userPortalSections({ hasFamily: false, isPrimaryAdmin: false })
      .flatMap((section) => section.items)
    const byLabel = Object.fromEntries(items.map((item) => [item.label, item.icon]))

    expect(byLabel.Home).toBe('home')
    expect(byLabel['Dietary Profile']).toBe('person')
    expect(byLabel['Account Settings']).toBe('gear')
    expect(byLabel['Create Family Circle']).toBe('people')
  })

  it('hides create-circle while membership is still loading', () => {
    const labels = userPortalSections({
      hasFamily: false,
      isPrimaryAdmin: false,
      loading: true,
    })
      .flatMap((section) => section.items)
      .map((item) => item.label)

    expect(labels).not.toContain('Create Family Circle')
  })

  it('offers create-circle only when the user has no family', () => {
    const labels = userPortalSections({ hasFamily: false, isPrimaryAdmin: false })
      .flatMap((section) => section.items)
      .map((item) => item.label)

    expect(labels).toContain('Create Family Circle')
    expect(labels).not.toContain('Family Members')
  })

  it('shows family admin tools for PRIMARY_ADMIN after Personal', () => {
    const sections = userPortalSections({ hasFamily: true, isPrimaryAdmin: true })
    expect(sections.map((section) => section.label)).toEqual(['Personal', 'Family'])

    const labels = sections.flatMap((section) => section.items).map((item) => item.label)

    expect(labels).toContain('Family Members')
    expect(labels).toContain('Family Overview')
    expect(labels).not.toContain('Create Family Circle')

    const byLabel = Object.fromEntries(
      sections.flatMap((section) => section.items).map((item) => [item.label, item.icon]),
    )
    expect(byLabel['Family Overview']).toBe('overview')
    expect(byLabel['Restriction Summary']).toBe('restrictions')
    expect(byLabel['Family Scan History']).toBe('history')
    expect(byLabel['Verdict Trends']).toBe('trends')
  })
})

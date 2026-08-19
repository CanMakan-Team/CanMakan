export interface FamilyProfileSummary {
  id: number
  profileName: string
  familyId: number | null
  relationship: string
  initials: string
  isPrimary: boolean
  active: boolean
}

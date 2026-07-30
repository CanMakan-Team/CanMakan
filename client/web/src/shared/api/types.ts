export type Role =
  | 'ROLE_APP_USER'
  | 'ROLE_FAMILY_ADMIN'
  | 'ROLE_SYSTEM_ADMIN'

export type Portal = 'FAMILY' | 'SYSTEM'
export type Relationship =
  | 'SELF'
  | 'SPOUSE'
  | 'CHILD'
  | 'PARENT'
  | 'DEPENDANT'
  | 'OTHER'
export type AgeGroup = 'CHILD' | 'TEEN' | 'ADULT' | 'SENIOR' | 'UNSPECIFIED'
export type RestrictionCode =
  | 'HALAL'
  | 'KOSHER'
  | 'PEANUT_ALLERGY'
  | 'TREE_NUT_ALLERGY'
  | 'DAIRY_FREE'
  | 'LACTOSE_INTOLERANT'
  | 'EGG_ALLERGY'
  | 'GLUTEN_FREE'
  | 'SHELLFISH_ALLERGY'
  | 'SESAME_ALLERGY'
  | 'VEGAN'
  | 'VEGETARIAN'
  | 'LOW_SUGAR'
  | 'LOW_SALT'
  | 'LOW_CHOLESTEROL'
  | 'KETO'

export interface AuthenticatedSession {
  accessToken?: string
  userId: number
  displayName: string
  roles: Role[]
  portal: Portal
  prototype: boolean
}

export interface FamilyMember {
  memberId: number
  profileName: string
  relationship: Relationship
  ageGroup: AgeGroup
  commonRequirements: RestrictionCode[]
  restrictions: RestrictionCode[]
  source: 'REGISTERED_USER' | 'DEPENDANT_PROFILE'
  maskedEmail?: string
}

export interface FamilyProfileInput {
  profileName: string
  relationship: Relationship
  ageGroup: AgeGroup
  commonRequirements: RestrictionCode[]
  restrictions: RestrictionCode[]
}

export interface ActiveProfile {
  memberId: number
  profileName: string
  activatedAt: string
}

export interface ExistingUserSearchResult {
  userId: number
  displayName: string
  maskedEmail: string
  accountStatus: 'ACTIVE' | 'INACTIVE'
  familyLinkStatus: 'NOT_LINKED' | 'ALREADY_LINKED' | 'PENDING'
}

export type Verdict = 'SAFE' | 'WARNING' | 'AVOID' | 'INCOMPLETE'
export type DataCompleteness = 'COMPLETE' | 'PARTIAL' | 'PRODUCT_NOT_FOUND'

export interface ScanRecord {
  scanId: number
  product: string
  brand: string
  memberId: number
  evaluatedProfile: string
  verdict: Verdict
  detectedIngredient: string
  resolvedIngredient: string
  matchedRestriction: string
  explanation: string
  dataCompleteness: DataCompleteness
  dataSource: string
  scannedAt: string
  suggestedAlternative?: string
}

export interface ConsumerTrendResponse {
  period: { from: string; to: string }
  verdictDistribution: Array<{ verdict: Verdict; count: number }>
  flaggedIngredients: Array<{ resolvedIngredient: string; count: number }>
  productCategories?: Array<{
    category: string
    safeCount: number
    warningCount: number
    avoidCount: number
    incompleteCount: number
  }>
  partial: boolean
}

export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING' | 'DISABLED'
export interface UserAccessSummary {
  userId: number
  displayName: string
  maskedEmail: string
  roles: Role[]
  accountStatus: AccountStatus
  familyMembershipStatus?: 'NONE' | 'LINKED' | 'PENDING'
  lastActiveAt?: string
}

export interface AccessUpdate {
  roles?: Role[]
  accountStatus?: AccountStatus
}

export interface AuditEntry {
  auditId: number
  actor: string
  action: string
  targetUserId: number
  createdAt: string
}

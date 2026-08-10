/**
 * Setting up the DTO interfaces to match the backend payloads,
 * {RegistrationResponse, AuthLoginResponse, FamilyMe, FamilyMember, FamilyProfileInput, ActiveProfile, ExistingUserSearchResult, InvitationResponse, DependantProfileResponse, Verdict, DataCompleteness, ScanRecord, ConsumerTrendResponse}
 * from backend/auth/dto
 * 
 * @author Amelia
 * @author Khai
 */

// Define the role type
export type Role =
  | 'ROLE_APP_USER'
  | 'ROLE_FAMILY_ADMIN'
  | 'ROLE_SYSTEM_ADMIN'

// Define the portal type
export type Portal = 'FAMILY' | 'SYSTEM'

// Define the relationship type
export type Relationship =
  | 'SELF'
  | 'SPOUSE'
  | 'CHILD'
  | 'PARENT'
  | 'DEPENDANT'
  | 'OTHER'

// Define the age group type
export type AgeGroup = 'CHILD' | 'TEEN' | 'ADULT' | 'SENIOR' | 'UNSPECIFIED'

// Define the restriction code type
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

// Define the authenticated session type
export interface AuthenticatedSession {
  accessToken?: string
  userId: number
  displayName: string
  roles: Role[]
  portal: Portal
  prototype: boolean
}

  /** POST /api/auth/register success body (UC18). */
export interface RegistrationResponse {
  userId: number
  profileId: number
  name: string
  email: string
  active: boolean
}

/** POST /api/auth/login success body (UC19 JWT). */
export interface AuthLoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: {
    userId: number
    email: string
    role: 'USER' | 'ADMIN'
  }
}

/** Current family context from GET /api/families/me (UC8). */
export interface FamilyMe {
  familyId: number
  familyName: string
  memberRole: string
  selfProfileId: number | null
  createdByUserId: number
}

// Define the family member type
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

// Define the family profile input type
export interface FamilyProfileInput {
  profileName: string
  relationship: Relationship
  ageGroup: AgeGroup
  commonRequirements: RestrictionCode[]
  restrictions: RestrictionCode[]
}

// Define the active profile type
export interface ActiveProfile {
  memberId: number
  profileName: string
  activatedAt: string
}

// Define the existing user search result type
export interface ExistingUserSearchResult {
  userId?: number | null
  displayName?: string | null
  maskedEmail: string
  accountStatus: 'ACTIVE' | 'INACTIVE' | 'NOT_REGISTERED'
  familyLinkStatus: 'NOT_LINKED' | 'ALREADY_LINKED' | 'PENDING'
}

// Define the invitation response type
export interface InvitationResponse {
  invitationId: number
  invitedEmail: string
  invitationToken: string
  inviteCode: string
  inviteUrl: string
  status: string
  expiresAt: string
  inviteeRegistered: boolean
}

// Define the dependant profile response type
export interface DependantProfileResponse {
  profileId: number
  profileName: string
  relationship: string
  familyId: number
}

// Define the verdict type
export type Verdict = 'SAFE' | 'WARNING' | 'AVOID' | 'INCOMPLETE'
export type DataCompleteness = 'COMPLETE' | 'PARTIAL' | 'PRODUCT_NOT_FOUND'

// Define the scan record type
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

// Define the consumer trend response type
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

/**
 * UC6 Setting up the DTO interfaces to match the backend payloads,
 * {FamilyMeRestrictionDetail, FamilyMeRestrictionSum, FamilyRestrictionSumRes}
 * from backend/family/dto
 */

// Define the family me restriction detail type
export interface FamilyMeRestrictionDetail {
  code: string,
  displayName: string,
  severity: string
}

// Define the family me restriction sum type
export interface FamilyMeRestrictionSum {
  userId: number,
  profileId?: number | null,
  name: string,
  isActive: boolean,
  restrictions: FamilyMeRestrictionDetail[]
}

// Define the family restriction sum response type
export interface FamilyRestrictionSumRes {
  familyMembers: FamilyMeRestrictionSum[]
}

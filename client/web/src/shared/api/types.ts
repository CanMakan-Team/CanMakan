/**
 * Setting up the DTO interfaces to match the backend payloads,
 * {RegistrationResponse, AuthLoginResponse, FamilyMe, FamilyMember, FamilyProfileInput, ActiveProfile, ExistingUserSearchResult, InvitationResponse, DependantProfileResponse, ScanVerdict, Verdict, DataCompleteness, ScanRecord, ConsumerTrendResponse}
 * from backend/auth/dto
 * 
 * @author Amelia
 * @author Khai
 */

// Define the role type
export type Role =
  | 'ROLE_APP_USER'
  | 'ROLE_SYSTEM_ADMIN'

// Define the portal type
export type Portal = 'FAMILY' | 'SYSTEM'

// Define the relationship type
export type Relationship =
  | 'SELF' // Self
  | 'SPOUSE' // Spouse
  | 'CHILD' // Child
  | 'PARENT' // Parent
  | 'DEPENDANT' // Dependant
  | 'OTHER' // Other

// Define the age group type
export type AgeGroup = 'CHILD' | 'TEEN' | 'ADULT' | 'SENIOR' | 'UNSPECIFIED'

// Define the restriction code type.
// These values must match server/backend dietary_restrictions.code exactly
// (not just resemble it) -- the backend resolves a selection by looking up
// this literal string via findByCodeIgnoreCase and throws if it doesn't
// find a row, so e.g. 'PEANUT' here, not 'PEANUT_ALLERGY'. Labels shown to
// users live separately in profileOptions.ts and can read however is best
// for the UI regardless of this code.
export type RestrictionCode =
  | 'HALAL'
  | 'KOSHER'
  | 'PEANUT'
  | 'TREE_NUT'
  | 'DAIRY'
  | 'LACTOSE_INTOLERANT'
  | 'EGG'
  | 'GLUTEN'
  | 'SHELLFISH'
  | 'SESAME'
  | 'FISH'
  | 'SOY'
  | 'VEGAN'
  | 'VEGETARIAN'
  | 'LOW_SUGAR'
  | 'LOW_FAT'
  | 'LOW_TRANS_FAT'
  | 'LOW_SODIUM'
  | 'LOW_CHOLESTEROL'
  | 'KETO'

// Define the authenticated session type
export interface AuthenticatedSession {
  accessToken: string // short-lived access credential kept in memory only
  userId: number // id of the user
  email: string // backend-authoritative account email
  active: boolean // backend-authoritative account status
  displayName: string // display name of the user
  roles: Role[] // roles of the user
  portal: Portal // portal of the user
  prototype: boolean // true if the user is a prototype user
}

/** POST /api/auth/register success body (UC18). */
export interface RegistrationResponse {
  userId: number // id of the user
  email: string // email of the user
  active: boolean // true if the user is active
}

/** POST /api/auth/login success body (UC19 JWT). */
export interface AuthLoginResponse {
  accessToken: string // JWT access token
  tokenType: string // type of the token
  expiresIn: number // expiration time in seconds
  user: {
    userId: number // id of the user
    email: string // email of the user
    role: 'USER' | 'ADMIN' // role of the user
    active: boolean // active status reloaded by the backend
  }
}

/** GET /api/auth/me safe account identity. */
export interface CurrentUserResponse {
  userId: number
  email: string
  role: 'USER' | 'ADMIN'
  active: boolean
}

/** Current family context from GET /api/families/me (UC8). */
export interface FamilyMe {
  familyId: number // id of the family
  familyName: string // name of the family
  memberRole: string // role of the member
  selfProfileId: number | null // id of the self profile
  createdByUserId: number // id of the user who created the family
}

// Define the family member type
export interface FamilyMember {
  memberId: number // id of the member
  profileId: number // id of the profile
  linkedUserId?: number | null // null if there is no linked user (i.e. dependant profile)
  profileName: string // name of the profile
  relationship: Relationship // relationship of the profile
  ageGroup: AgeGroup // age group of the profile
  commonRequirements: RestrictionCode[] // common requirements of the profile
  restrictions: RestrictionCode[] // restrictions of the profile
  source: 'REGISTERED_USER' | 'DEPENDANT_PROFILE' // source of the profile
  maskedEmail?: string // masked email of the user
  memberRole?: string | null // role of the member
  profileActive: boolean // true if the profile is active
}

// Define the family profile input type
export interface FamilyProfileInput {
  profileName: string // name of the profile
  relationship: Relationship // relationship of the profile
  ageGroup: AgeGroup // age group of the profile
  commonRequirements: RestrictionCode[] // common requirements of the profile
  restrictions: RestrictionCode[] // restrictions of the profile
}

// Define the active profile type (matches GET/PUT /families/me/active-profile)
export interface ActiveProfile {
  profileId: number // id of the profile
  profileName: string // name of the profile
  relationship?: string | null // relationship of the profile
  familyId?: number | null // id of the family
  isPrimary?: boolean // true if the profile is the primary profile
}

// Define the existing user search result type
export interface ExistingUserSearchResult {
  userId?: number | null // id of the user
  displayName?: string | null // display name of the user
  maskedEmail: string // masked email of the user
  accountStatus: 'ACTIVE' | 'INACTIVE' | 'NOT_REGISTERED' // account status of the user
  familyLinkStatus: 'NOT_LINKED' | 'ALREADY_LINKED' | 'PENDING' // family link status of the user
}

// Define the invitation response type
export interface InvitationResponse {
  invitationId: number // id of the invitation
  invitedEmail: string // email of the invited user
  relationship: Relationship // relationship of the invitee to the family admin
  invitationToken: string // token of the invitation
  inviteCode: string // code of the invitation
  inviteUrl: string // url of the invitation
  status: string // status of the invitation
  expiresAt: string // timestamp of the expiration
  inviteeRegistered: boolean // true if the invited user is registered
  emailSent: boolean // true if Resend accepted the invitation email
}

export interface InvitationPreviewResponse {
  invitedEmail: string
  familyName: string
  expired: boolean
}

// Define the dependant profile response type
export interface DependantProfileResponse {
  profileId: number // id of the profile
  profileName: string // name of the profile
  relationship: string // relationship of the profile
  familyId: number // id of the family
}

// scans.verdict (DB / assessment wire)
export type ScanVerdict = 'SAFE' | 'WARNING' | 'UNSAFE'

// UC6 matrix badge tones (maps DB severity_level for display; includes legacy AVOID)
export type Verdict = ScanVerdict | 'AVOID' | 'INCOMPLETE'

// The family restriction summary grid reports whether a restriction is
// present on a profile, not how severe it is, so cells read "Selected"
// rather than a severity-derived label like "Avoid".
export type RestrictionCellStatus = Verdict | 'SELECTED'

export type DataCompleteness = 'COMPLETE' | 'PARTIAL' | 'PRODUCT_NOT_FOUND'

// Define the scan record type
export interface ScanRecord {
  scanId: number // id of the scan
  product: string // name of the product
  brand: string // name of the brand
  memberId: number // id of the member
  evaluatedProfile: string // name of the evaluated profile
  verdict: ScanVerdict // wire verdict from assessment / family scans API
  detectedIngredient: string // name of the detected ingredient
  resolvedIngredient: string // name of the resolved ingredient
  matchedRestriction: string // name of the matched restriction
  explanation: string // explanation of the scan
  dataCompleteness: DataCompleteness // data completeness of the scan
  dataSource: string // source of the scan
  scannedAt: string // timestamp of the scan
  suggestedAlternative?: string // suggested alternative of the scan
}

// Define the consumer trend response type
export interface ConsumerTrendResponse {
  period: { from: string; to: string } // period of the trend (start and end dates)
  verdictDistribution: Array<{ verdict: ScanVerdict; count: number }> // distribution of the verdicts
  flaggedIngredients: Array<{ resolvedIngredient: string; count: number }> // distribution of the flagged ingredients
  productCategories?: Array<{
    category: string // category of the product
    safeCount: number // count of the safe products
    warningCount: number // count of the warning products
    avoidCount: number // count of the avoid products
    incompleteCount: number // count of the incomplete products
  }>
  partial: boolean // true if the trend is partial
}

/**
 * UC6 Setting up the DTO interfaces to match the backend payloads,
 * {FamilyMeRestrictionDetail, FamilyMeRestrictionSum, FamilyRestrictionSumRes}
 * from backend/family/dto
 */

// Define the family me restriction detail type
export interface FamilyMeRestrictionDetail {
  code: string, // code of the restriction
  displayName: string, // display name of the restriction
  severity: string // severity of the restriction
}

// Define the family me restriction sum type
export interface FamilyMeRestrictionSum {
  userId: number, // id of the user
  profileId?: number | null, // null if there is no profile
  name: string, // name of the profile
  isActive: boolean, // true if the profile is active
  restrictions: FamilyMeRestrictionDetail[] // restrictions of the profile
}

// Define the family restriction sum response type
export interface FamilyRestrictionSumRes {
  familyMembers: FamilyMeRestrictionSum[] // family members with their restrictions
}

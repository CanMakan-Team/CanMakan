import type {
  ActiveProfile,
  AuditEntry,
  ConsumerTrendResponse,
  ExistingUserSearchResult,
  FamilyMember,
  ScanRecord,
  UserAccessSummary,
} from '../api/types'

export interface MockFamilyState {
  members: FamilyMember[]
  activeProfile: ActiveProfile
}

export const initialFamilyState: MockFamilyState = {
  members: [
    {
      memberId: 101,
      profileName: 'Alicia',
      relationship: 'SELF',
      ageGroup: 'ADULT',
      commonRequirements: ['HALAL'],
      restrictions: ['SHELLFISH_ALLERGY'],
      source: 'REGISTERED_USER',
      maskedEmail: 'a***@example.com',
    },
    {
      memberId: 102,
      profileName: 'Marcus',
      relationship: 'SPOUSE',
      ageGroup: 'ADULT',
      commonRequirements: ['HALAL'],
      restrictions: ['LOW_SUGAR'],
      source: 'REGISTERED_USER',
      maskedEmail: 'm***@example.com',
    },
    {
      memberId: 103,
      profileName: 'Noah',
      relationship: 'CHILD',
      ageGroup: 'CHILD',
      commonRequirements: ['HALAL'],
      restrictions: ['PEANUT_ALLERGY', 'DAIRY_FREE'],
      source: 'DEPENDANT_PROFILE',
    },
  ],
  activeProfile: {
    memberId: 101,
    profileName: 'Alicia',
    activatedAt: '2026-07-29T08:30:00+08:00',
  },
}

export const existingUsers: Record<string, ExistingUserSearchResult> = {
  'jamie@example.com': {
    userId: 205,
    displayName: 'Jamie Tan',
    maskedEmail: 'j***@example.com',
    accountStatus: 'ACTIVE',
    familyLinkStatus: 'NOT_LINKED',
  },
  'alicia@example.com': {
    userId: 101,
    displayName: 'Alicia',
    maskedEmail: 'a***@example.com',
    accountStatus: 'ACTIVE',
    familyLinkStatus: 'ALREADY_LINKED',
  },
  'pending@example.com': {
    userId: 206,
    displayName: 'Pending User',
    maskedEmail: 'p***@example.com',
    accountStatus: 'ACTIVE',
    familyLinkStatus: 'PENDING',
  },
}

export const scanRecords: ScanRecord[] = [
  {
    scanId: 501,
    product: 'Crunchy Peanut Bar',
    brand: 'Good Day',
    memberId: 103,
    evaluatedProfile: 'Noah',
    verdict: 'AVOID',
    detectedIngredient: 'Peanut pieces',
    resolvedIngredient: 'Peanut',
    matchedRestriction: 'Peanut allergy',
    explanation:
      'The supplied assessment matched peanut to this profile’s peanut allergy.',
    dataCompleteness: 'COMPLETE',
    dataSource: 'Mock Open Food Facts record and backend assessment',
    scannedAt: '2026-07-28T18:42:00+08:00',
    suggestedAlternative: 'Oat & Seed Snack Bar',
  },
  {
    scanId: 502,
    product: 'Wholegrain Crackers',
    brand: 'Grain House',
    memberId: 101,
    evaluatedProfile: 'Alicia',
    verdict: 'SAFE',
    detectedIngredient: 'None flagged',
    resolvedIngredient: 'No supplied match',
    matchedRestriction: 'No supplied match',
    explanation:
      'The backend assessment did not identify a match for the selected profile.',
    dataCompleteness: 'COMPLETE',
    dataSource: 'Mock Open Food Facts record and backend assessment',
    scannedAt: '2026-07-27T10:15:00+08:00',
  },
  {
    scanId: 503,
    product: 'Creamy Mushroom Soup',
    brand: 'Kitchen Table',
    memberId: 103,
    evaluatedProfile: 'Noah',
    verdict: 'WARNING',
    detectedIngredient: 'Milk solids',
    resolvedIngredient: 'Milk',
    matchedRestriction: 'Dairy free',
    explanation:
      'The supplied assessment identified a dairy ingredient relevant to this profile.',
    dataCompleteness: 'PARTIAL',
    dataSource: 'Mock product record; ingredient list marked partial',
    scannedAt: '2026-07-25T12:08:00+08:00',
    suggestedAlternative: 'Plant-based Mushroom Soup',
  },
  {
    scanId: 504,
    product: 'Imported Rice Snack',
    brand: 'Unknown',
    memberId: 102,
    evaluatedProfile: 'Marcus',
    verdict: 'INCOMPLETE',
    detectedIngredient: 'Ingredient data unavailable',
    resolvedIngredient: 'Not resolved',
    matchedRestriction: 'Assessment incomplete',
    explanation:
      'The product record was incomplete, so no definitive assessment was supplied.',
    dataCompleteness: 'PRODUCT_NOT_FOUND',
    dataSource: 'Mock product-not-found response',
    scannedAt: '2026-07-23T20:19:00+08:00',
  },
  {
    scanId: 505,
    product: 'Honey Oat Cereal',
    brand: 'Morning Bowl',
    memberId: 101,
    evaluatedProfile: 'Alicia',
    verdict: 'WARNING',
    detectedIngredient: 'May contain shellfish (facility notice)',
    resolvedIngredient: 'Shellfish advisory',
    matchedRestriction: 'Shellfish allergy',
    explanation:
      'The supplied assessment flagged an advisory statement for this profile.',
    dataCompleteness: 'COMPLETE',
    dataSource: 'Mock product record and backend assessment',
    scannedAt: '2026-07-20T08:02:00+08:00',
  },
]

export const consumerTrends: ConsumerTrendResponse = {
  period: { from: '2026-07-01', to: '2026-07-29' },
  verdictDistribution: [
    { verdict: 'SAFE', count: 824 },
    { verdict: 'WARNING', count: 286 },
    { verdict: 'AVOID', count: 154 },
    { verdict: 'INCOMPLETE', count: 92 },
  ],
  flaggedIngredients: [
    { resolvedIngredient: 'Peanut', count: 148 },
    { resolvedIngredient: 'Milk', count: 131 },
    { resolvedIngredient: 'Wheat / gluten', count: 96 },
    { resolvedIngredient: 'Shellfish', count: 71 },
    { resolvedIngredient: 'Egg', count: 58 },
  ],
  productCategories: [
    {
      category: 'Snacks',
      safeCount: 248,
      warningCount: 91,
      avoidCount: 68,
      incompleteCount: 21,
    },
    {
      category: 'Beverages',
      safeCount: 214,
      warningCount: 43,
      avoidCount: 18,
      incompleteCount: 14,
    },
    {
      category: 'Ready meals',
      safeCount: 173,
      warningCount: 86,
      avoidCount: 45,
      incompleteCount: 32,
    },
  ],
  partial: true,
}

export const initialUsers: UserAccessSummary[] = [
  {
    userId: 9001,
    displayName: 'System Administrator',
    maskedEmail: 'a***@canmakan.demo',
    roles: ['ROLE_SYSTEM_ADMIN'],
    accountStatus: 'ACTIVE',
    familyMembershipStatus: 'NONE',
    lastActiveAt: '2026-07-29T09:12:00+08:00',
  },
  {
    userId: 101,
    displayName: 'Alicia Lim',
    maskedEmail: 'a***@example.com',
    roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
    accountStatus: 'ACTIVE',
    familyMembershipStatus: 'LINKED',
    lastActiveAt: '2026-07-29T08:42:00+08:00',
  },
  {
    userId: 205,
    displayName: 'Jamie Tan',
    maskedEmail: 'j***@example.com',
    roles: ['ROLE_APP_USER'],
    accountStatus: 'ACTIVE',
    familyMembershipStatus: 'NONE',
    lastActiveAt: '2026-07-27T14:20:00+08:00',
  },
  {
    userId: 207,
    displayName: 'Priya Nair',
    maskedEmail: 'p***@example.com',
    roles: ['ROLE_APP_USER'],
    accountStatus: 'PENDING',
    familyMembershipStatus: 'PENDING',
  },
  {
    userId: 208,
    displayName: 'Daniel Wong',
    maskedEmail: 'd***@example.com',
    roles: ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN'],
    accountStatus: 'SUSPENDED',
    familyMembershipStatus: 'LINKED',
    lastActiveAt: '2026-07-12T16:05:00+08:00',
  },
]

export const initialAudit: AuditEntry[] = []

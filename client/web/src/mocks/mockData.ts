import type {
  ActiveProfile,
  ConsumerTrendResponse,
  ExistingUserSearchResult,
  FamilyMember,
  ScanRecord,
} from '../shared/api/types'

/**
 * Mock data for the family state
 * With mock on you get: real JWT + real /me, but fake members/scans/admin
 * With mock off you get: real JWT + real /me, real members/scans/admin
 * 
 * @author Amelia
 * @author YangMaowei
 */

export interface MockFamilyState {
  members: FamilyMember[]
  activeProfile: ActiveProfile
}

export const initialFamilyState: MockFamilyState = {
  members: [
    {
      memberId: 101,
      profileId: 101,
      linkedUserId: 101,
      profileName: 'Alicia',
      relationship: 'SELF',
      ageGroup: 'ADULT',
      commonRequirements: ['HALAL'],
      restrictions: ['SHELLFISH'],
      source: 'REGISTERED_USER',
      maskedEmail: 'a***@example.com',
      memberRole: 'PRIMARY_ADMIN',
      profileActive: true,
    },
    {
      memberId: 102,
      profileId: 102,
      linkedUserId: 102,
      profileName: 'Marcus',
      relationship: 'SPOUSE',
      ageGroup: 'ADULT',
      commonRequirements: ['HALAL'],
      restrictions: ['LOW_SUGAR'],
      source: 'REGISTERED_USER',
      maskedEmail: 'm***@example.com',
      memberRole: 'MEMBER',
      profileActive: true,
    },
    {
      memberId: 103,
      profileId: 103,
      linkedUserId: null,
      profileName: 'Noah',
      relationship: 'CHILD',
      ageGroup: 'CHILD',
      commonRequirements: ['HALAL'],
      restrictions: ['PEANUT', 'DAIRY'],
      source: 'DEPENDANT_PROFILE',
      memberRole: null,
      profileActive: true,
    },
  ],
  activeProfile: {
    profileId: 101,
    profileName: 'Alicia',
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
    verdict: 'UNSAFE',
    detectedIngredient: 'Peanut pieces',
    resolvedIngredient: 'Peanut',
    matchedRestriction: 'Peanut Allergy',
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
    matchedRestriction: 'Dairy Free',
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
    verdict: 'WARNING',
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
    matchedRestriction: 'Shellfish Allergy',
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
    { verdict: 'UNSAFE', count: 154 },
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

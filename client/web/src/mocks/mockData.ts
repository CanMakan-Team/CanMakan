import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMember,
  ScanRecord,
} from '../shared/api/types'
import type { ConsumerTrendsResponse } from '../features/analytics/consumerTrendsTypes'

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
    matchedRestriction: 'Lactose Intolerance',
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

export const consumerTrends: ConsumerTrendsResponse = {
  period: {
    from: '2026-07-01',
    to: '2026-07-29',
    timezone: 'Asia/Singapore',
  },
  summary: {
    totalScans: 1264,
    safeCount: 824,
    warningCount: 286,
    unsafeCount: 154,
  },
  dailyTrend: [
    {
      date: '2026-07-27',
      totalCount: 435,
      safeCount: 280,
      warningCount: 100,
      unsafeCount: 55,
    },
    {
      date: '2026-07-28',
      totalCount: 412,
      safeCount: 270,
      warningCount: 92,
      unsafeCount: 50,
    },
    {
      date: '2026-07-29',
      totalCount: 417,
      safeCount: 274,
      warningCount: 94,
      unsafeCount: 49,
    },
  ],
  topFlaggedIngredients: [
    { ingredientName: 'Peanut', flaggedCount: 148 },
    { ingredientName: 'Milk', flaggedCount: 131 },
    { ingredientName: 'Wheat / gluten', flaggedCount: 96 },
    { ingredientName: 'Shellfish', flaggedCount: 71 },
    { ingredientName: 'Egg', flaggedCount: 58 },
  ],
  dataQuality: {
    partial: true,
    skippedMalformedFindings: 3,
  },
  generatedAt: '2026-07-29T10:00:00+08:00',
}

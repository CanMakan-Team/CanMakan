import type {
  ActiveProfile,
  ExistingUserSearchResult,
  FamilyMember,
  ScanRecord,
} from '../shared/api/types'
import type { ConsumerTrendsResponse } from '../features/analytics/api/consumerTrendsTypes'

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

export interface SyntheticConsumerTrendProduct {
  productName: string
  category: string
  share: number
}

export const consumerTrendProductTemplates: SyntheticConsumerTrendProduct[] = [
  { productName: 'Synthetic wholegrain snack bites', category: 'Snacks', share: 0.1 },
  { productName: 'Synthetic oat beverage', category: 'Beverages', share: 0.09 },
  { productName: 'Synthetic rice crackers', category: 'Snacks', share: 0.08 },
  { productName: 'Synthetic tomato pantry sauce', category: 'Pantry staples', share: 0.07 },
  { productName: 'Synthetic sparkling fruit drink', category: 'Beverages', share: 0.065 },
  { productName: 'Synthetic seed and cereal bar', category: 'Snacks', share: 0.06 },
  { productName: 'Synthetic canned chickpeas', category: 'Pantry staples', share: 0.055 },
  { productName: 'Synthetic coconut drink', category: 'Beverages', share: 0.05 },
  { productName: 'Synthetic baked vegetable crisps', category: 'Snacks', share: 0.045 },
  { productName: 'Synthetic cooking paste', category: 'Pantry staples', share: 0.04 },
  { productName: 'Synthetic herbal tea', category: 'Beverages', share: 0.035 },
  { productName: 'Synthetic dried fruit mix', category: 'Snacks', share: 0.03 },
  { productName: 'Synthetic soup base', category: 'Pantry staples', share: 0.025 },
  { productName: 'Synthetic long-name product for accessible wrapping and display verification', category: 'Uncategorised', share: 0.02 },
  { productName: 'Synthetic mixed grain pouch', category: 'Pantry staples', share: 0.018 },
  { productName: 'Synthetic label-incomplete item', category: 'Uncategorised', share: 0.012 },
]

/** Static synthetic example; mockAdminRepository derives requested periods from this vocabulary. */
export const consumerTrends: ConsumerTrendsResponse = {
  period: {
    from: '2026-07-27',
    to: '2026-07-29',
    timezone: 'Asia/Singapore',
  },
  appliedFilters: { category: null },
  summary: {
    totalScans: 27,
    safeCount: 17,
    warningCount: 7,
    unsafeCount: 3,
    uniqueProducts: 14,
    averageScansPerDay: 9,
    peakScanDay: { date: '2026-07-29', scanCount: 10 },
  },
  dailyTrend: [
    {
      date: '2026-07-27',
      totalCount: 8,
      safeCount: 5,
      warningCount: 2,
      unsafeCount: 1,
    },
    {
      date: '2026-07-28',
      totalCount: 9,
      safeCount: 6,
      warningCount: 2,
      unsafeCount: 1,
    },
    {
      date: '2026-07-29',
      totalCount: 10,
      safeCount: 6,
      warningCount: 3,
      unsafeCount: 1,
    },
  ],
  mostScannedProducts: consumerTrendProductTemplates.slice(0, 14).map((product, index) => ({
    rank: index + 1,
    productName: product.productName,
    scanCount: [4, 3, 3, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1][index],
    percentage: Number(
      (([4, 3, 3, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1][index] / 27) * 100).toFixed(2),
    ),
  })),
  categoryOverview: [
    { category: 'Snacks', scanCount: 10, percentage: 37.04 },
    { category: 'Beverages', scanCount: 8, percentage: 29.63 },
    { category: 'Pantry staples', scanCount: 6, percentage: 22.22 },
    { category: 'Uncategorised', scanCount: 3, percentage: 11.11 },
  ],
  topRestrictions: [
    { restrictionCode: 'PEANUT_ALLERGY', flaggedCount: 5 },
    { restrictionCode: 'HIGH_SUGAR_WARNING', flaggedCount: 4 },
    { restrictionCode: 'DAIRY_INTOLERANCE', flaggedCount: 3 },
  ],
  topFlaggedIngredients: [
    { ingredientName: 'Peanut', flaggedCount: 5 },
    { ingredientName: 'Milk solids', flaggedCount: 3 },
    { ingredientName: 'Wheat flour', flaggedCount: 2 },
  ],
  dataQuality: {
    partial: false,
    skippedMalformedFindings: 0,
  },
  generatedAt: '2026-07-29T10:00:00+08:00',
}

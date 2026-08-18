export interface TrendPeriod {
  from: string
  to: string
  timezone: string
}

export interface TrendSummary {
  totalScans: number
  safeCount: number
  warningCount: number
  unsafeCount: number
  uniqueProducts: number
  averageScansPerDay: number
  peakScanDay: PeakScanDay | null
}

export interface PeakScanDay {
  date: string
  scanCount: number
}

export interface DailyTrendPoint {
  date: string
  totalCount: number
  safeCount: number
  warningCount: number
  unsafeCount: number
}

export interface FlaggedIngredientTrend {
  ingredientName: string
  flaggedCount: number
}

export interface ProductScanTrend {
  rank: number
  productName: string
  scanCount: number
  percentage: number
}

export interface CategoryScanTrend {
  category: string
  scanCount: number
  percentage: number
}

export interface RestrictionTrend {
  restrictionCode: string
  flaggedCount: number
}

export interface ConsumerTrendsAppliedFilters {
  category: string | null
}

export interface ConsumerTrendsDataQuality {
  partial: boolean
  skippedMalformedFindings: number
}

export interface ConsumerTrendsResponse {
  period: TrendPeriod
  appliedFilters: ConsumerTrendsAppliedFilters
  summary: TrendSummary
  dailyTrend: DailyTrendPoint[]
  mostScannedProducts: ProductScanTrend[]
  categoryOverview: CategoryScanTrend[]
  topRestrictions: RestrictionTrend[]
  topFlaggedIngredients: FlaggedIngredientTrend[]
  dataQuality: ConsumerTrendsDataQuality
  generatedAt: string
}

export interface ConsumerTrendsQuery {
  from?: string
  to?: string
  limit?: number
  category?: string
}

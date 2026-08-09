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

export interface ConsumerTrendsDataQuality {
  partial: boolean
  skippedMalformedFindings: number
}

export interface ConsumerTrendsResponse {
  period: TrendPeriod
  summary: TrendSummary
  dailyTrend: DailyTrendPoint[]
  topFlaggedIngredients: FlaggedIngredientTrend[]
  dataQuality: ConsumerTrendsDataQuality
  generatedAt: string
}

export interface ConsumerTrendsQuery {
  from?: string
  to?: string
  limit?: number
}

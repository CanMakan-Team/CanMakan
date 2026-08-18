import type {
  CategoryScanTrend,
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
  DailyTrendPoint,
} from '../features/analytics/api/consumerTrendsTypes'
import {
  consumerTrendProductTemplates,
  consumerTrends,
} from './mockData'

const SINGAPORE_DATE_FORMATTER = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Singapore',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})
const DAY_MS = 86_400_000
const CATEGORY_NAMES = ['Snacks', 'Beverages', 'Pantry staples', 'Uncategorised'] as const

const delay = (milliseconds = 500) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

function currentSingaporeDate(): string {
  const values: Record<string, string> = {}
  for (const part of SINGAPORE_DATE_FORMATTER.formatToParts(new Date())) {
    values[part.type] = part.value
  }
  return `${values.year}-${values.month}-${values.day}`
}

function parseDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

function formatDate(value: Date): string {
  return value.toISOString().slice(0, 10)
}

function addDays(value: string, days: number): string {
  return formatDate(new Date(parseDate(value).getTime() + days * DAY_MS))
}

function enumerateDates(from: string, to: string): string[] {
  const dates: string[] = []
  for (let date = from; date <= to; date = addDays(date, 1)) dates.push(date)
  return dates
}

function countsForDate(date: string): Record<(typeof CATEGORY_NAMES)[number], number> {
  const dayNumber = Math.floor(parseDate(date).getTime() / DAY_MS)
  if (dayNumber % 11 === 0) {
    return { Snacks: 0, Beverages: 0, 'Pantry staples': 0, Uncategorised: 0 }
  }
  return {
    Snacks: dayNumber % 4 === 0 ? 0 : (dayNumber % 7) + 5,
    Beverages: (dayNumber % 5) + 3,
    'Pantry staples': dayNumber % 3 === 0 ? 0 : (dayNumber % 4) + 2,
    Uncategorised: dayNumber % 4 === 0 ? 2 : 1,
  }
}

function outcomeCounts(totalCount: number) {
  const safeCount = Math.floor(totalCount * 0.62)
  const warningCount = Math.floor((totalCount - safeCount) * 0.68)
  return {
    safeCount,
    warningCount,
    unsafeCount: totalCount - safeCount - warningCount,
  }
}

function roundPercentage(count: number, total: number): number {
  return total === 0 ? 0 : Number(((count / total) * 100).toFixed(2))
}

function buildMockResponse(query: ConsumerTrendsQuery): ConsumerTrendsResponse {
  const to = query.to ?? currentSingaporeDate()
  const from = query.from ?? addDays(to, -29)
  const selectedCategory = query.category ?? null
  const dates = enumerateDates(from, to)
  const allCounts = dates.map((date) => ({ date, counts: countsForDate(date) }))

  const categoryTotals = Object.fromEntries(CATEGORY_NAMES.map((category) => [category, 0])) as
    Record<(typeof CATEGORY_NAMES)[number], number>
  for (const day of allCounts) {
    for (const category of CATEGORY_NAMES) categoryTotals[category] += day.counts[category]
  }
  const periodTotal = Object.values(categoryTotals).reduce((sum, count) => sum + count, 0)
  const categoryOverview: CategoryScanTrend[] = CATEGORY_NAMES
    .map((category) => ({
      category,
      scanCount: categoryTotals[category],
      percentage: roundPercentage(categoryTotals[category], periodTotal),
    }))
    .filter((item) => item.scanCount > 0)
    .sort((left, right) => right.scanCount - left.scanCount || left.category.localeCompare(right.category))

  const dailyTrend: DailyTrendPoint[] = allCounts.map(({ date, counts }) => {
    const totalCount = selectedCategory === null
      ? Object.values(counts).reduce((sum, count) => sum + count, 0)
      : counts[selectedCategory as keyof typeof counts] ?? 0
    return { date, totalCount, ...outcomeCounts(totalCount) }
  })
  const totalScans = dailyTrend.reduce((sum, point) => sum + point.totalCount, 0)
  const safeCount = dailyTrend.reduce((sum, point) => sum + point.safeCount, 0)
  const warningCount = dailyTrend.reduce((sum, point) => sum + point.warningCount, 0)
  const unsafeCount = dailyTrend.reduce((sum, point) => sum + point.unsafeCount, 0)

  const eligibleProducts = consumerTrendProductTemplates
    .filter((product) => selectedCategory === null || product.category === selectedCategory)
    .map((product) => ({
      productName: product.productName,
      scanCount: totalScans === 0 ? 0 : Math.max(1, Math.floor(totalScans * product.share)),
    }))
    .filter((product) => product.scanCount > 0)
    .sort((left, right) => right.scanCount - left.scanCount || left.productName.localeCompare(right.productName))
    .slice(0, 20)
    .map((product, index) => ({
      rank: index + 1,
      productName: product.productName,
      scanCount: product.scanCount,
      percentage: roundPercentage(product.scanCount, totalScans),
    }))

  const peakPoint = totalScans === 0
    ? null
    : dailyTrend.reduce((peak, point) => point.totalCount >= peak.totalCount ? point : peak)
  const concernFactor = selectedCategory === null ? 1 : 0.55
  const scaleConcern = (count: number) => Math.max(1, Math.round(count * dates.length / 3 * concernFactor))

  return {
    period: { from, to, timezone: 'Asia/Singapore' },
    appliedFilters: { category: selectedCategory },
    summary: {
      totalScans,
      safeCount,
      warningCount,
      unsafeCount,
      uniqueProducts: eligibleProducts.length,
      averageScansPerDay: dates.length === 0
        ? 0
        : Number((totalScans / dates.length).toFixed(2)),
      peakScanDay: peakPoint === null
        ? null
        : { date: peakPoint.date, scanCount: peakPoint.totalCount },
    },
    dailyTrend,
    mostScannedProducts: eligibleProducts,
    categoryOverview,
    topRestrictions: totalScans === 0
      ? []
      : consumerTrends.topRestrictions.map((item) => ({
          ...item,
          flaggedCount: scaleConcern(item.flaggedCount),
        })),
    topFlaggedIngredients: totalScans === 0
      ? []
      : consumerTrends.topFlaggedIngredients.map((item) => ({
          ...item,
          flaggedCount: scaleConcern(item.flaggedCount),
        })),
    dataQuality: { partial: false, skippedMalformedFindings: 0 },
    generatedAt: `${to}T12:00:00+08:00`,
  }
}

export const mockAdminRepository = {
  async getConsumerTrends(
    query: ConsumerTrendsQuery = {},
  ): Promise<ConsumerTrendsResponse> {
    await delay(600)
    return buildMockResponse(query)
  },
}

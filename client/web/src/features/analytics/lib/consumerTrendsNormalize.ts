import { ApiError } from '../../../shared/api/apiErrors'
import type { ConsumerTrendsResponse } from '../api/consumerTrendsTypes'

function isCalendarDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/u.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
  )
}

const INCOMPLETE_MESSAGE =
  'The consumer trends data is incomplete. Please refresh and try again.'

/** Validates and normalizes a consumer-trends API payload before the UI renders it. */
export function prepareConsumerTrendsResponse(
  response: ConsumerTrendsResponse,
): ConsumerTrendsResponse {
  const incomplete = () => new ApiError(INCOMPLETE_MESSAGE)
  if (
    !response
    || !response.period
    || !isCalendarDate(response.period.from)
    || !isCalendarDate(response.period.to)
    || !response.summary
  ) {
    throw incomplete()
  }

  const summaryValues = [
    response.summary.totalScans,
    response.summary.safeCount,
    response.summary.warningCount,
    response.summary.unsafeCount,
    response.summary.uniqueProducts,
    response.summary.averageScansPerDay,
  ]
  if (
    summaryValues.some((value) => !Number.isFinite(value))
    || (response.summary.peakScanDay !== null
      && (!isCalendarDate(response.summary.peakScanDay?.date)
        || !Number.isFinite(response.summary.peakScanDay?.scanCount)))
  ) {
    throw incomplete()
  }

  const dailyTrend = Array.isArray(response.dailyTrend) ? response.dailyTrend : []
  const products = Array.isArray(response.mostScannedProducts)
    ? response.mostScannedProducts
    : []
  const categories = Array.isArray(response.categoryOverview)
    ? response.categoryOverview
    : []
  const restrictions = Array.isArray(response.topRestrictions)
    ? response.topRestrictions
    : []
  const ingredients = Array.isArray(response.topFlaggedIngredients)
    ? response.topFlaggedIngredients
    : []
  if (
    dailyTrend.some(
      (item) =>
        !isCalendarDate(item?.date)
        || [item?.totalCount, item?.safeCount, item?.warningCount, item?.unsafeCount].some(
          (value) => !Number.isFinite(value),
        ),
    )
    || products.some(
      (item) =>
        typeof item?.productName !== 'string'
        || [item?.rank, item?.scanCount, item?.percentage].some(
          (value) => !Number.isFinite(value),
        ),
    )
    || categories.some(
      (item) =>
        typeof item?.category !== 'string'
        || [item?.scanCount, item?.percentage].some((value) => !Number.isFinite(value)),
    )
    || restrictions.some(
      (item) =>
        typeof item?.restrictionCode !== 'string' || !Number.isFinite(item?.flaggedCount),
    )
    || ingredients.some(
      (item) =>
        typeof item?.ingredientName !== 'string' || !Number.isFinite(item?.flaggedCount),
    )
    || (response.appliedFilters !== null
      && response.appliedFilters !== undefined
      && response.appliedFilters.category !== null
      && typeof response.appliedFilters.category !== 'string')
    || (response.dataQuality !== null
      && response.dataQuality !== undefined
      && (typeof response.dataQuality.partial !== 'boolean'
        || !Number.isFinite(response.dataQuality.skippedMalformedFindings)))
  ) {
    throw incomplete()
  }

  return {
    ...response,
    appliedFilters: response.appliedFilters ?? { category: null },
    dailyTrend,
    mostScannedProducts: products,
    categoryOverview: categories,
    topRestrictions: restrictions,
    topFlaggedIngredients: ingredients,
    dataQuality: response.dataQuality ?? {
      partial: false,
      skippedMalformedFindings: 0,
    },
  }
}

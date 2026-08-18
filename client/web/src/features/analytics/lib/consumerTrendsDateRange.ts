import type { ConsumerTrendsQuery } from "../api/consumerTrendsTypes"

export const CONSUMER_TRENDS_MAX_PERIOD_DAYS = 90
export const PERIOD_OPTIONS = [7, 30, 90] as const
export type PeriodPresetDays = (typeof PERIOD_OPTIONS)[number]

function toSingaporeDate(date: Date): string {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Singapore",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
  const parts = Object.fromEntries(
    formatter
      .formatToParts(date)
      .filter((part) => part.type === "year" || part.type === "month" || part.type === "day")
      .map((part) => [part.type, part.value]),
  )
  return `${parts.year}-${parts.month}-${parts.day}`
}

export function singaporeToday(now = new Date()): string {
  return toSingaporeDate(now)
}

/** Calendar day key (YYYY-MM-DD) for an instant in Asia/Singapore. */
export function singaporeDayKeyFromInstant(instantMs: number): string {
  return toSingaporeDate(new Date(instantMs))
}

export function addCalendarDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split("-").map(Number)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

export function inclusiveDayCount(from: string, to: string): number {
  const start = Date.parse(`${from}T00:00:00Z`)
  const end = Date.parse(`${to}T00:00:00Z`)
  return Math.round((end - start) / 86_400_000) + 1
}

export function buildPeriodQuery(
  days: number,
  category?: string,
  now = new Date(),
): ConsumerTrendsQuery {
  const to = toSingaporeDate(now)
  const from = addCalendarDays(to, -(days - 1))
  return {
    from,
    to,
    category,
  }
}

export function describeRangeError(
  from: string,
  to: string,
  today: string,
): string | null {
  if (!from || !to) return "Choose both a start date and an end date."
  if (from > to) return "The start date must not be after the end date."
  if (to > today) return "The end date cannot be after today."
  if (inclusiveDayCount(from, to) > CONSUMER_TRENDS_MAX_PERIOD_DAYS) {
    return "The reporting period must not exceed 90 days."
  }
  return null
}

export function matchingPresetDays(
  from: string | undefined,
  to: string | undefined,
  today: string,
): PeriodPresetDays | null {
  if (!from || !to || to !== today) return null
  const days = inclusiveDayCount(from, to)
  return PERIOD_OPTIONS.includes(days as PeriodPresetDays)
    ? (days as PeriodPresetDays)
    : null
}

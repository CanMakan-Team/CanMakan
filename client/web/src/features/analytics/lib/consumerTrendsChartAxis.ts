/**
 * Builds a rounded y-axis so daily totals sit on a calm scale instead of stretching to the peak.
 * The ceiling never drops below 10, matching the verdict-trend chart used on the family pages.
 */
export const CONSUMER_TRENDS_CHART_MIN_AXIS = 10

export function niceChartStep(target: number): number {
  const rough = target / 5
  const power = Math.pow(10, Math.floor(Math.log10(Math.max(rough, 1))))
  const normalised = rough / power
  const stepFactor = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10
  return stepFactor * power
}

export function consumerTrendsChartAxis(dataMax: number): { axisMax: number; ticks: number[] } {
  const target = Math.max(dataMax, CONSUMER_TRENDS_CHART_MIN_AXIS)
  const step = niceChartStep(target)
  const axisMax = Math.ceil(target / step) * step
  const ticks: number[] = []
  for (let tick = 0; tick <= axisMax; tick += step) {
    ticks.push(tick)
  }
  return { axisMax, ticks }
}

export function chartEndLabelIndexes(length: number): number[] {
  if (length <= 0) return []
  if (length === 1) return [0]
  if (length === 2) return [0, 1]
  return [0, Math.floor((length - 1) / 2), length - 1]
}

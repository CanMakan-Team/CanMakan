import { describe, expect, it } from 'vitest'
import {
  chartEndLabelIndexes,
  consumerTrendsChartAxis,
} from '../../../features/analytics/lib/consumerTrendsChartAxis'

describe('consumerTrendsChartAxis', () => {
  it('keeps a minimum ceiling so small daily counts do not fill the panel', () => {
    const axis = consumerTrendsChartAxis(8)
    expect(axis.axisMax).toBe(10)
    expect(axis.ticks[0]).toBe(0)
    expect(axis.ticks[axis.ticks.length - 1]).toBe(10)
    expect(axis.ticks.length).toBeGreaterThanOrEqual(4)
  })

  it('rounds a large peak to a whole-number ceiling', () => {
    const axis = consumerTrendsChartAxis(90)
    expect(axis.axisMax).toBeGreaterThanOrEqual(90)
    expect(axis.axisMax % 10).toBe(0)
    expect(axis.ticks[axis.ticks.length - 1]).toBe(axis.axisMax)
  })

  it('labels only the start, middle and end of a long range', () => {
    expect(chartEndLabelIndexes(7)).toEqual([0, 3, 6])
    expect(chartEndLabelIndexes(90)).toEqual([0, 44, 89])
  })
})

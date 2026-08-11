import { describe, expect, it } from 'vitest'
import { getGreetingPeriod } from '../../../../features/family/lib/greeting'

describe('getGreetingPeriod', () => {
  it('returns morning from midnight up to (not including) noon', () => {
    expect(getGreetingPeriod(new Date(2026, 0, 1, 0, 0))).toBe('morning')
    expect(getGreetingPeriod(new Date(2026, 0, 1, 6, 30))).toBe('morning')
    expect(getGreetingPeriod(new Date(2026, 0, 1, 11, 59))).toBe('morning')
  })

  it('returns afternoon from noon up to (not including) 6pm', () => {
    expect(getGreetingPeriod(new Date(2026, 0, 1, 12, 0))).toBe('afternoon')
    expect(getGreetingPeriod(new Date(2026, 0, 1, 15, 0))).toBe('afternoon')
    expect(getGreetingPeriod(new Date(2026, 0, 1, 17, 59))).toBe('afternoon')
  })

  it('returns evening from 6pm up to midnight', () => {
    expect(getGreetingPeriod(new Date(2026, 0, 1, 18, 0))).toBe('evening')
    expect(getGreetingPeriod(new Date(2026, 0, 1, 20, 30))).toBe('evening')
    expect(getGreetingPeriod(new Date(2026, 0, 1, 23, 59))).toBe('evening')
  })
})

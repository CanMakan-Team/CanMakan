import { describe, expect, it } from 'vitest'
import { buildRestrictionPayload } from '../../../../features/account/lib/buildRestrictionPayload'

describe('buildRestrictionPayload', () => {
  it('keeps persisted severity for untouched restrictions', () => {
    const payload = buildRestrictionPayload(
      { 1: 'STRICT_AVOID', 2: 'STRICT_AVOID' },
      { 1: 'PREFERENCE', 2: 'INTOLERANCE' },
      new Set(),
    )
    expect(payload).toEqual({ 1: 'PREFERENCE', 2: 'INTOLERANCE' })
  })

  it('uses selected severity when the user toggled a restriction', () => {
    const payload = buildRestrictionPayload(
      { 1: 'STRICT_AVOID' },
      { 1: 'PREFERENCE' },
      new Set([1]),
    )
    expect(payload).toEqual({ 1: 'STRICT_AVOID' })
  })
})

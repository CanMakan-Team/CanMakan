import { describe, expect, it } from 'vitest'
import {
  buildRestrictionMatrixRows,
  buildHouseholdCodes,
  isHouseholdRestriction,
  selectionTone,
} from '../../../../features/family/lib/restrictionMatrix'

describe('restrictionMatrix', () => {
  it('collapses dairy-family codes into one lactose row', () => {
    const rows = buildRestrictionMatrixRows([
      {
        restrictions: [
          { code: 'LACTOSE', displayName: 'Lactose', severity: 'INTOLERANCE' },
        ],
      },
    ])

    expect(rows).toHaveLength(1)
    expect(rows[0].key).toBe('DAIRY_FAMILY')
    expect(rows[0].label).toBe('Lactose Intolerance')
  })

  it('maps dairy reference option when any dairy-family code is present', () => {
    const codes = buildHouseholdCodes([
      { restrictions: [{ code: 'DAIRY_FREE', displayName: 'Dairy free', severity: 'PREFERENCE' }] },
    ])
    expect(isHouseholdRestriction('DAIRY', codes)).toBe(true)
  })

  it('uses allergy default tone when severity is missing', () => {
    expect(selectionTone(undefined, 'allergy')).toBe('severe')
    expect(selectionTone('PREFERENCE', 'diet')).toBe('preference')
  })
})

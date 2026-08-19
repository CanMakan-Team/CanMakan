import { restrictionGroups } from './profileOptions'
import type { FamilyMeRestrictionDetail } from '../../../shared/api/types'

export type RestrictionGroupKey = 'religious' | 'allergy' | 'diet' | 'other'
export type MatrixFilter = 'all' | RestrictionGroupKey
export type SelectionTone = 'severe' | 'caution' | 'preference'

export interface RestrictionMatrixRow {
  key: string
  label: string
  matchCodes: Set<string>
  groupKey: RestrictionGroupKey
}

export interface RestrictionMatrixMember {
  profileId?: number | null
  userId?: number | null
  name: string
  restrictions: FamilyMeRestrictionDetail[]
}

function groupKeyFromIndex(groupIndex: number): RestrictionGroupKey {
  if (groupIndex === 0) return 'religious'
  if (groupIndex === 1) return 'allergy'
  return 'diet'
}

const restrictionGroupIndex = new Map<string, number>()
const restrictionGroupKey = new Map<string, RestrictionGroupKey>()
restrictionGroups.forEach((group, groupIndex) => {
  const key = groupKeyFromIndex(groupIndex)
  for (const option of group.options) {
    restrictionGroupIndex.set(option.value, groupIndex)
    restrictionGroupKey.set(option.value, key)
  }
})

export const DAIRY_FAMILY_CODES = new Set([
  'DAIRY',
  'LACTOSE_INTOLERANT',
  'DAIRY_FREE',
  'LACTOSE',
])

export const REFERENCE_GROUPS = restrictionGroups.map((group, groupIndex) => ({
  ...group,
  groupKey: groupKeyFromIndex(groupIndex),
}))

export const MATRIX_FILTER_OPTIONS: Array<{ value: MatrixFilter; label: string }> = [
  { value: 'all', label: 'All' },
  { value: 'allergy', label: 'Allergies' },
  { value: 'religious', label: 'Religious' },
  { value: 'diet', label: 'Diets & preferences' },
  { value: 'other', label: 'Other' },
]

export function groupKeyForCode(code: string): RestrictionGroupKey {
  return restrictionGroupKey.get(code) ?? 'other'
}

export function selectionTone(
  severity: string | undefined,
  groupKey: RestrictionGroupKey,
): SelectionTone {
  const normalized = (severity ?? '').trim().toUpperCase()
  if (normalized === 'STRICT_AVOID' || normalized === 'AVOID') return 'severe'
  if (normalized === 'INTOLERANCE') return 'caution'
  if (normalized === 'PREFERENCE') return 'preference'
  if (groupKey === 'allergy') return 'severe'
  if (groupKey === 'religious') return 'caution'
  return 'preference'
}

export function matchingRestriction(
  restrictions: FamilyMeRestrictionDetail[],
  matchCodes: Set<string>,
) {
  return restrictions.find((restriction) =>
    matchCodes.has(restriction.code.trim().toUpperCase()),
  )
}

export function isHouseholdRestriction(
  optionCode: string,
  householdCodes: Set<string>,
): boolean {
  const code = optionCode.trim().toUpperCase()
  if (householdCodes.has(code)) return true
  if (code === 'DAIRY') {
    for (const dairyCode of DAIRY_FAMILY_CODES) {
      if (householdCodes.has(dairyCode)) return true
    }
  }
  return false
}

export function buildHouseholdCodes(
  activeMembers: Array<{ restrictions: FamilyMeRestrictionDetail[] }>,
): Set<string> {
  const codes = new Set<string>()
  for (const member of activeMembers) {
    for (const restriction of member.restrictions) {
      codes.add(restriction.code.trim().toUpperCase())
    }
  }
  return codes
}

export function buildRestrictionMatrixRows(
  activeMembers: Array<{ restrictions: FamilyMeRestrictionDetail[] }>,
): RestrictionMatrixRow[] {
  const seen = new Set<string>()
  const result: RestrictionMatrixRow[] = []

  for (const member of activeMembers) {
    for (const restriction of member.restrictions) {
      const code = restriction.code.trim().toUpperCase()
      if (DAIRY_FAMILY_CODES.has(code)) {
        if (seen.has('DAIRY_FAMILY')) continue
        seen.add('DAIRY_FAMILY')
        result.push({
          key: 'DAIRY_FAMILY',
          label: 'Lactose Intolerance',
          matchCodes: DAIRY_FAMILY_CODES,
          groupKey: 'allergy',
        })
        continue
      }
      if (seen.has(code)) continue
      seen.add(code)
      result.push({
        key: code,
        label: restriction.displayName,
        matchCodes: new Set([code]),
        groupKey: groupKeyForCode(code),
      })
    }
  }

  return result.sort((a, b) => {
    const groupKeyA = a.key === 'DAIRY_FAMILY' ? 'DAIRY' : a.key
    const groupKeyB = b.key === 'DAIRY_FAMILY' ? 'DAIRY' : b.key
    const groupIndexA = restrictionGroupIndex.get(groupKeyA) ?? Number.MAX_SAFE_INTEGER
    const groupIndexB = restrictionGroupIndex.get(groupKeyB) ?? Number.MAX_SAFE_INTEGER
    if (groupIndexA !== groupIndexB) return groupIndexA - groupIndexB
    return a.label.localeCompare(b.label)
  })
}

export function filterMatrixRows(
  rows: RestrictionMatrixRow[],
  matrixFilter: MatrixFilter,
): RestrictionMatrixRow[] {
  if (matrixFilter === 'all') return rows
  return rows.filter((row) => row.groupKey === matrixFilter)
}

export function defaultOpenReferenceGroups(householdCodes: Set<string>): Set<string> {
  const open = new Set<string>()
  for (const group of REFERENCE_GROUPS) {
    const usedInFamily = group.options.some((option) =>
      isHouseholdRestriction(option.value, householdCodes),
    )
    if (usedInFamily) open.add(group.label)
  }
  if (open.size === 0) {
    open.add('Allergies and intolerances')
  }
  return open
}

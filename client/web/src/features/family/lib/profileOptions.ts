import type {
  AgeGroup,
  Relationship,
  RestrictionCode,
} from '../../../shared/api/types'

export const relationshipOptions: Array<{
  value: Relationship
  label: string
}> = [
  { value: 'SELF', label: 'Self' },
  { value: 'SPOUSE', label: 'Spouse' },
  { value: 'CHILD', label: 'Child' },
  { value: 'PARENT', label: 'Parent' },
  { value: 'DEPENDANT', label: 'Dependant' },
  { value: 'OTHER', label: 'Other' },
]

export const ageGroupOptions: Array<{ value: AgeGroup; label: string }> = [
  { value: 'CHILD', label: 'Child' },
  { value: 'TEEN', label: 'Teen' },
  { value: 'ADULT', label: 'Adult' },
  { value: 'SENIOR', label: 'Senior' },
  { value: 'UNSPECIFIED', label: 'Unspecified' },
]

export const restrictionGroups: Array<{
  label: string
  type: 'common' | 'individual'
  options: Array<{ value: RestrictionCode; label: string }>
}> = [
  {
    label: 'Religious requirements',
    type: 'common',
    options: [
      { value: 'HALAL', label: 'Halal' },
      { value: 'KOSHER', label: 'Kosher' },
    ],
  },
  {
    label: 'Allergies and intolerances',
    type: 'individual',
    options: [
      { value: 'PEANUT_ALLERGY', label: 'Peanut Allergy' },
      { value: 'TREE_NUT_ALLERGY', label: 'Tree Nut Allergy' },
      { value: 'DAIRY_FREE', label: 'Dairy Free' },
      { value: 'LACTOSE_INTOLERANT', label: 'Lactose Intolerant' },
      { value: 'EGG_ALLERGY', label: 'Egg Allergy' },
      { value: 'GLUTEN_FREE', label: 'Gluten Free' },
      { value: 'SHELLFISH_ALLERGY', label: 'Shellfish Allergy' },
      { value: 'SESAME_ALLERGY', label: 'Sesame Allergy' },
    ],
  },
  {
    label: 'Specific diets and health preferences',
    type: 'individual',
    options: [
      { value: 'VEGAN', label: 'Vegan' },
      { value: 'VEGETARIAN', label: 'Vegetarian' },
      { value: 'LOW_SUGAR', label: 'Low Sugar' },
      { value: 'LOW_SALT', label: 'Low Salt' },
      { value: 'LOW_CHOLESTEROL', label: 'Low Cholesterol' },
      { value: 'KETO', label: 'Keto' },
    ],
  },
]

export const summaryRestrictions: Array<{
  value: RestrictionCode
  shortLabel: string
}> = [
  { value: 'HALAL', shortLabel: 'Halal' },
  { value: 'KOSHER', shortLabel: 'Kosher' },
  { value: 'PEANUT_ALLERGY', shortLabel: 'Peanut' },
  { value: 'DAIRY_FREE', shortLabel: 'Dairy Free' },
  { value: 'EGG_ALLERGY', shortLabel: 'Egg' },
  { value: 'GLUTEN_FREE', shortLabel: 'Gluten Free' },
  { value: 'SHELLFISH_ALLERGY', shortLabel: 'Shellfish' },
  { value: 'VEGAN', shortLabel: 'Vegan' },
  { value: 'VEGETARIAN', shortLabel: 'Vegetarian' },
  { value: 'LOW_SUGAR', shortLabel: 'Low Sugar' },
]

export const formatCode = (code: string) =>
  code
    .toLowerCase()
    .split('_')
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(' ')

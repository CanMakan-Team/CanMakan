import type {
  AgeGroup,
  Relationship,
  RestrictionCode,
} from '../../shared/api/types'

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
      { value: 'PEANUT_ALLERGY', label: 'Peanut allergy' },
      { value: 'TREE_NUT_ALLERGY', label: 'Tree nut allergy' },
      { value: 'DAIRY_FREE', label: 'Dairy free' },
      { value: 'LACTOSE_INTOLERANT', label: 'Lactose intolerant' },
      { value: 'EGG_ALLERGY', label: 'Egg allergy' },
      { value: 'GLUTEN_FREE', label: 'Gluten free' },
      { value: 'SHELLFISH_ALLERGY', label: 'Shellfish allergy' },
      { value: 'SESAME_ALLERGY', label: 'Sesame allergy' },
    ],
  },
  {
    label: 'Specific diets and health preferences',
    type: 'individual',
    options: [
      { value: 'VEGAN', label: 'Vegan' },
      { value: 'VEGETARIAN', label: 'Vegetarian' },
      { value: 'LOW_SUGAR', label: 'Low sugar' },
      { value: 'LOW_SALT', label: 'Low salt' },
      { value: 'LOW_CHOLESTEROL', label: 'Low cholesterol' },
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
  { value: 'DAIRY_FREE', shortLabel: 'Dairy free' },
  { value: 'EGG_ALLERGY', shortLabel: 'Egg' },
  { value: 'GLUTEN_FREE', shortLabel: 'Gluten free' },
  { value: 'SHELLFISH_ALLERGY', shortLabel: 'Shellfish' },
  { value: 'VEGAN', shortLabel: 'Vegan' },
  { value: 'VEGETARIAN', shortLabel: 'Vegetarian' },
  { value: 'LOW_SUGAR', shortLabel: 'Low sugar' },
]

export const formatCode = (code: string) =>
  code
    .toLowerCase()
    .split('_')
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(' ')

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

// Descriptions mirror the `description` column seeded for each restriction
// code in 05_household_dietary_data.sql. Keep the two in sync by hand, the
// same way the `label` values here are already kept in sync with the
// backend's `display_name` column.
export const restrictionGroups: Array<{
  label: string
  type: 'common' | 'individual'
  options: Array<{ value: RestrictionCode; label: string; description: string }>
}> = [
  {
    label: 'Religious requirements',
    type: 'common',
    options: [
      { value: 'HALAL', label: 'Halal', description: 'Requires Halal-certified ingredients and no pork or alcohol.' },
      { value: 'KOSHER', label: 'Kosher', description: 'Requires kosher-certified ingredients; forbids pork and shellfish, and does not mix meat with dairy.' },
    ],
  },
  {
    label: 'Allergies and intolerances',
    type: 'individual',
    options: [
      { value: 'PEANUT', label: 'Peanut Allergy', description: 'Severe reaction to peanuts and peanut derivatives.' },
      { value: 'TREE_NUT', label: 'Tree Nut Allergy', description: 'Avoid almonds, cashews, hazelnuts, walnuts, and other tree nuts.' },
      {
        value: 'DAIRY',
        label: 'Lactose Intolerance',
        description:
          'Avoid milk solids, lactose, whey, and dairy fats.',
      },
      { value: 'EGG', label: 'Egg Allergy', description: 'Avoid eggs and egg powder.' },
      { value: 'GLUTEN', label: 'Gluten Intolerance', description: 'Strictly avoid wheat, barley, rye, and oat gluten.' },
      { value: 'SHELLFISH', label: 'Shellfish Allergy', description: 'Avoid crab, shrimp, lobster, and shellfish extracts.' },
      { value: 'SESAME', label: 'Sesame Allergy', description: 'Avoid sesame seeds, tahini, and sesame oil.' },
      { value: 'FISH', label: 'Fish Allergy', description: 'Avoid bony fish, anchovies, bonito, and fish surimi.' },
      { value: 'SOY', label: 'Soy Allergy', description: 'Avoid soy lecithin, miso, and soybean derivatives.' },
    ],
  },
  {
    label: 'Specific diets and health preferences',
    type: 'individual',
    options: [
      { value: 'VEGAN', label: 'Vegan', description: 'Avoids animal-derived ingredients.' },
      { value: 'VEGETARIAN', label: 'Vegetarian', description: 'Does not consume meat, poultry, or seafood.' },
      { value: 'LOW_SUGAR', label: 'Low Sugar', description: 'Checks sugar per 100 g.' },
      { value: 'LOW_FAT', label: 'Low Fat', description: 'Checks total fat per 100 g.' },
      { value: 'LOW_TRANS_FAT', label: 'Low Trans Fat', description: 'Checks trans fat per 100 g.' },
      { value: 'LOW_SODIUM', label: 'Low Salt', description: 'Checks sodium per 100 g.' },
      { value: 'LOW_CHOLESTEROL', label: 'Low Cholesterol', description: 'Checks cholesterol per 100 g.' },
      { value: 'KETO', label: 'Keto', description: 'Very low carbohydrate, high fat diet.' },
    ],
  },
]

export const summaryRestrictions: Array<{
  value: RestrictionCode
  shortLabel: string
}> = [
  { value: 'HALAL', shortLabel: 'Halal' },
  { value: 'KOSHER', shortLabel: 'Kosher' },
  { value: 'PEANUT', shortLabel: 'Peanut' },
  { value: 'DAIRY', shortLabel: 'Lactose' },
  { value: 'EGG', shortLabel: 'Egg' },
  { value: 'GLUTEN', shortLabel: 'Gluten Intolerance' },
  { value: 'SHELLFISH', shortLabel: 'Shellfish' },
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

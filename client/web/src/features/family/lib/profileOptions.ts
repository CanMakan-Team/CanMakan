import type {
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

export const RESTRICTION_CATEGORY_ORDER = ['RELIGIOUS', 'ALLERGEN', 'DIET']

// Reader-friendly headings for the catalog's raw category values, shared by
// the personal and family dietary editors.
const restrictionCategoryLabels: Record<string, string> = {
  RELIGIOUS: 'Religious requirements',
  ALLERGEN: 'Allergies and intolerances',
  DIET: 'Specific diets and health preferences',
}

export function restrictionCategoryLabel(category: string) {
  return (
    restrictionCategoryLabels[category] ??
    category
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')
  )
}

export function groupCatalogByCategory<T extends { category: string }>(
  catalog: T[],
): Array<[string, T[]]> {
  return Object.entries(
    catalog.reduce<Record<string, T[]>>((groups, option) => {
      const category = option.category || 'OTHER'
      groups[category] = [...(groups[category] ?? []), option]
      return groups
    }, {}),
  ).sort(([categoryA], [categoryB]) => {
    const indexA = RESTRICTION_CATEGORY_ORDER.indexOf(categoryA)
    const indexB = RESTRICTION_CATEGORY_ORDER.indexOf(categoryB)
    if (indexA === -1 && indexB === -1) return 0
    if (indexA === -1) return 1
    if (indexB === -1) return -1
    return indexA - indexB
  })
}

// Descriptions mirror the `description` column seeded for each restriction
// code in 05_household_dietary_data.sql. Keep the two in sync by hand, the
// same way the `label` values here are already kept in sync with the
// backend's `display_name` column.
// Option order matches the mobile edit dietary profile screen.
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
      { value: 'EGG', label: 'Egg Allergy', description: 'Avoid eggs and egg powder.' },
      { value: 'FISH', label: 'Fish Allergy', description: 'Avoid bony fish, anchovies, bonito, and fish surimi.' },
      { value: 'GLUTEN', label: 'Gluten Intolerance', description: 'Strictly avoid wheat, barley, rye, and oat gluten.' },
      {
        value: 'DAIRY',
        label: 'Lactose Intolerance',
        description:
          'Avoid milk solids, lactose, whey, and dairy fats.',
      },
      { value: 'PEANUT', label: 'Peanut Allergy', description: 'Severe reaction to peanuts and peanut derivatives.' },
      { value: 'SESAME', label: 'Sesame Allergy', description: 'Avoid sesame seeds, tahini, and sesame oil.' },
      { value: 'SHELLFISH', label: 'Shellfish Allergy', description: 'Avoid crab, shrimp, lobster, and shellfish extracts.' },
      { value: 'SOY', label: 'Soy Allergy', description: 'Avoid soy lecithin, miso, and soybean derivatives.' },
      { value: 'TREE_NUT', label: 'Tree Nut Allergy', description: 'Avoid almonds, cashews, hazelnuts, walnuts, and other tree nuts.' },
    ],
  },
  {
    label: 'Specific diets and health preferences',
    type: 'individual',
    options: [
      { value: 'KETO', label: 'Keto', description: 'Very low carbohydrate, high fat diet.' },
      { value: 'LOW_CHOLESTEROL', label: 'Low Cholesterol', description: 'Checks cholesterol per 100 g.' },
      { value: 'LOW_FAT', label: 'Low Fat', description: 'Checks total fat per 100 g.' },
      { value: 'LOW_SODIUM', label: 'Low Salt', description: 'Checks sodium per 100 g.' },
      { value: 'LOW_SUGAR', label: 'Low Sugar', description: 'Checks sugar per 100 g.' },
      { value: 'LOW_TRANS_FAT', label: 'Low Trans Fat', description: 'Checks trans fat per 100 g.' },
      { value: 'VEGAN', label: 'Vegan', description: 'Avoids animal-derived ingredients.' },
      { value: 'VEGETARIAN', label: 'Vegetarian', description: 'Does not consume meat, poultry, or seafood.' },
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

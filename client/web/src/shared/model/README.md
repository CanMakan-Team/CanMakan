# shared/model

Truly shared data types only.

## Purpose
Holds types used by more than one feature.

## Contains
- Cross-feature types (e.g. User, ApiError, common enums)
- Types that cannot cleanly belong to a single feature

## Does not contain
- Feature-specific models  
  Those belong in `features/<name>/model` (or next to the feature code)

## Rule
If only one feature uses it, don’t put it here.
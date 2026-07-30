# shared/api

HTTP client and shared API utilities.

## Purpose
Holds the technical networking foundation used by all features.

## Contains
- `apiClient` (axios/fetch instance)
- Error helpers (`apiErrors`)
- Truly shared API types only

## Does not contain
- Feature-specific services  
  Those live in their features, e.g.:
  - `features/auth/api/`
  - `features/family/api/`
  - `features/admin/api/`

## Notes
- Features import the shared client/errors from here
- Keep this folder thin — no business/feature API logic
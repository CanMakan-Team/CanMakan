# pages

Top-level route entry pages.

## Purpose
Holds standalone pages that sit outside feature folders (mainly login screens for now).

## Contains
- Family login page
- System admin login page

## Notes
- Temporary/lightweight location
- Prefer moving pages into their features when the structure stabilises
  (e.g. login → `features/auth`)
- Keep these files thin; compose feature UI where possible
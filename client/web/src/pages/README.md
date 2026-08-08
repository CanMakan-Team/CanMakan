# pages

Top-level route entry pages.

## Purpose
Holds standalone pages that sit outside feature folders (login/register screens).

## Contains
- Family login page (`/family-login`) — live credentials
- Family register page (`/family-register`) — UC18
- System admin login page (`/system-admin-login`) — live credentials

## Notes
- Temporary/lightweight location
- Prefer moving pages into their features when the structure stabilises
  (e.g. login → `features/auth`)
- Keep these files thin; compose feature UI where possible

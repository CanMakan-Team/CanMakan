# pages

Top-level route entry pages.

## Purpose
Holds standalone pages that sit outside feature folders (login/register screens).

## Contains
- User login page (`/login`) — live credentials; after sign-in, USER accounts
  (including family PRIMARY_ADMIN) go to `/me`. `/family-login` redirects here
- User register page (`/register`) — UC18; `/family-register` redirects here
- System admin login page (`/system-admin-login`) — live credentials

## Notes
- Temporary/lightweight location
- Prefer moving pages into their features when the structure stabilises
  (e.g. login → `features/auth`)
- Keep these files thin; compose feature UI where possible

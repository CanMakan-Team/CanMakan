# mocks

Prototype mock data and repositories for unfinished or demo surfaces.

## Purpose
Provides browser-only mock data when `VITE_USE_MOCK_API=true` for family/admin
features. Default is **live** (`VITE_USE_MOCK_API=false`).

## Auth / UC8
Auth (`/api/auth/register`, `/api/auth/login`) and UC8 family create/`/me` are
**always live**. They are not served by this mock repository.

## Hybrid mode warning
With mock on, identity is real (JWT + `/me`) but roster/invite/manage/scans are
fake localStorage data. Prefer mock **off** when verifying UC9–UC12 against Spring.

## Still mockable
Members, invites, profiles, active profile, scans, restriction summary, and admin
pages when `VITE_USE_MOCK_API=true`.

## Notes
- Not used in production
- Live family manage APIs exist; mock exists for offline demos only

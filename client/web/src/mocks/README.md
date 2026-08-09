# mocks

Prototype mock data and repositories for unfinished surfaces.

## Purpose
Provides browser-only mock data when `VITE_USE_MOCK_API=true` for family/admin
features that do not yet have live Spring Boot endpoints.

## Auth / UC8
Auth (`/api/auth/register`, `/api/auth/login`) and UC8 family create/`/me` are
**always live**. They are not served by this mock repository.

## Still mockable
Members, link, profiles, active profile, scans, and admin pages when
`VITE_USE_MOCK_API=true`.

## Notes
- Default is live (`VITE_USE_MOCK_API=false`)
- Not used in production

# features/auth

Authentication and session management.

## Purpose
Handles login state, session context, and route protection.

## Contains
- Session context / provider
- `useSession` hook
- Protected route helpers
- Access denied UI

## Notes
- Aligns with backend `auth` package
- API calls may live in `shared/api` for now
- Does not own admin/family business pages
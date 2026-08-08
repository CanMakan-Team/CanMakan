# features/auth

Authentication and session management.

## Purpose
Handles live login/register, session context, and route protection.

## Contains
- Session context / provider (`loginWithCredentials`, `registerAndLogin`)
- `authService` — always live `POST /api/auth/register` and `/api/auth/login`
- `useSession` hook
- Protected route helpers
- Access denied UI

## UC18 web registration
- Public route `/family-register`
- After success, session is stored and the user lands on `/family` (UC8 gate)

## Platform role vs family portal access (interim, pre-JWT)

Do **not** confuse these three ideas:

1. **Platform role** on `users.role_id` — `USER` (app account) vs `ADMIN` (system staff).
2. **Web session roles** from login — used by `ProtectedRoute` / login forms.
3. **Family membership role** on `family_members.member_role` — `PRIMARY_ADMIN` vs `MEMBER` (only after the user is in a circle).

| Account | Login web roles | `/family-login` | `/system-admin-login` | After entering `/family` |
| --- | --- | --- | --- | --- |
| Platform **USER** (normal registrant) | `ROLE_APP_USER` + `ROLE_FAMILY_ADMIN` | Allowed | Blocked (wrong portal) | No circle → **Create your family circle**; has circle → family portal |
| Platform **ADMIN** (system staff) | `ROLE_SYSTEM_ADMIN` only | Blocked: “This account cannot access this portal.” | Allowed | N/A |

Notes:
- Web `ROLE_FAMILY_ADMIN` today means “may use the family portal,” **not** “is `PRIMARY_ADMIN` in the DB.” Every platform `USER` gets it so they can complete UC8 create-circle.
- True family admin vs member is `family_members.member_role` after membership exists.
- Login form checks the expected portal role and clears the session if it does not match.

## Notes
- Pre-JWT: `apiClient` sends `X-User-Id` from session `userId`
- JWT / Spring Security filter chain remain UC19
- No prototype/demo one-click login

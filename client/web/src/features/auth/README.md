# features/auth

Authentication and session management.

## Purpose
Handles live login/register, session context, and route protection against UC19 JWT login.

## Contains
- Session context / provider (`loginWithCredentials`, `register`)
- `authService` — live `POST /api/auth/register` and `/api/auth/login` (`AuthResponse`)
- `useSession` hook
- Protected route helpers
- Access denied UI

## UC18 web registration
- Public route `/family-register`
- After success, client returns to `/family-login`; registration does not create a session

## Platform role vs family portal access

Do **not** confuse these three ideas:

1. **Platform role** on `users.role_id` / JWT `SystemRole` — `USER` vs `ADMIN`.
2. **Web session roles** mapped client-side from JWT role — used by `ProtectedRoute`.
3. **Family membership role** on `family_members.member_role` — `PRIMARY_ADMIN` vs `MEMBER`.

| Account | Mapped web roles | `/family-login` | `/system-admin-login` |
| --- | --- | --- | --- |
| Platform **USER** | `ROLE_APP_USER` + `ROLE_FAMILY_ADMIN` | Allowed | Blocked |
| Platform **ADMIN** | `ROLE_SYSTEM_ADMIN` | Blocked | Allowed |

## Testing

Vitest suites live under `src/test/` (mirroring features/shared). Coverage includes
`authService`, `SessionProvider`, `ProtectedRoute`, `CredentialLoginForm`, and
`FamilyRegisterPage`.

## Notes
- `apiClient` sends `Authorization: Bearer <accessToken>` from session
- Family/scan APIs no longer use `X-User-Id`
- No prototype/demo one-click login

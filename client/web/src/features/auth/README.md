# features/auth

Authentication and session management.

## Purpose
Handles live login/register, session context, and route protection against UC19 JWT login.

## Contains
- Session context / provider (`loginWithCredentials`, `register`, restore, logout)
- `authService` — live register/login/refresh/logout and `/api/auth/me`
- `useSession` hook
- Protected route helpers
- Access denied UI

## UC18 web registration
- Public route `/family-register`
- Collects only durable account credentials (`email`, `password`); profile name
  is deferred to authenticated SELF-profile setup
- After success, client returns to `/family-login`; registration does not create a session
- Invitation links preserve their token through registration so the existing
  authenticated login flow can claim the invitation after sign-in
- Invitation login remains on the page while claiming and exposes a retry when
  the authenticated claim fails

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
- The access token and mapped portal roles are memory-only. The browser does not
  restore identity from `localStorage`.
- On startup the provider uses the HttpOnly refresh cookie, then verifies the
  account through `/api/auth/me` before a protected route renders.
- `apiClient` includes credentials, sends the current in-memory access token,
  and retries one safe GET/HEAD/OPTIONS request after a coordinated refresh.
  POST/PUT/PATCH/DELETE requests are not replayed. Auth endpoints and `/me`
  never enter recursive authentication recovery, and 403 is not expiry.
- Web Locks serialise login, refresh, and logout across same-origin tabs for the
  full HTTP response lifecycle. Browsers without Web Locks fail closed.
- Logout clears local account-bound state immediately, waits for an overlapping
  refresh, revokes the server session, and remains locally signed out on failure.
- Explicit logout and account switching publish a credential-free
  BroadcastChannel event, with a transient storage-event fallback, so other
  open tabs discard their in-memory identity and account data.
- Family/scan APIs no longer use `X-User-Id`
- No prototype/demo one-click login

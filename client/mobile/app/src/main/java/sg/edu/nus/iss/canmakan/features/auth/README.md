# features/auth

User registration, authentication and session management.

## Responsibilities
- UC18 two-screen account registration flow
- Registration request/response mapping and backend error handling
- Optional dietary-setup intent hand-off to authenticated onboarding
- Single post-login continuation ordering profile setup before invitation claim
- Login screen
- Logout
- Token storage and session restoration
- Handling unauthenticated state

## Notes
- Uses security utilities from `core`
- Does not own role/permission logic (handled by backend)
- Registration uses the shared Retrofit/Hilt client and sends only `email` and
  `password`. Deprecated compatibility fields such as `name` and
  `invitationToken` are not part of the Android account contract.
- Invitation tokens are offered to `PendingInvitationStore` from deep links,
  registration, and login routes. Registration never claims or consumes them.
  `LoginViewModel` also only offers; `PostLoginContinuationViewModel` is the sole
  authenticated claimer (after deferred dietary setup when requested).
- Registration Screen 2 records only whether the user wants dietary setup after
  sign-in. It never loads the authenticated restriction catalog. Durable profile
  name belongs to authenticated SELF-profile setup.
- `PendingOnboardingStore` retains only the normalized registration email and an
  opaque in-memory request version for normal navigation. The email binds the
  setup intent to the account that registered; the version prevents old
  same-account work from clearing a newer intent. The authenticated setup UI
  collects the profile name after sign-in. A different authenticated email
  invalidates the intent. The store deliberately does not survive process death
  and stores no password, backend user id, access token, refresh token, or
  session material.
- After UC19 Login persists the session, `PostLoginContinuationViewModel` routes
  requested dietary setup first and owns the invitation-claim attempt. Both
  setup and invitation work snapshot the initiating authenticated account and
  reject stale completion after logout or an account transition. Pending-token
  clearing is conditional, so completion of an older claim cannot erase a newer
  deep link for the same account.
- `AppAuthViewModel` is the account boundary for mobile-only transient state: it
  resets `ActiveProfileManager` to `UNSET_PROFILE_ID` and clears or rejects
  account-bound onboarding on logout, confirmed session invalidation, and an
  authenticated-account change.
- `AuthSessionStore.accountKey` combines authenticated `userId` with an
  in-memory session generation. It remains stable for same-user token refresh,
  but changes across logout, invalidation, or identity replacement so old A work
  is rejected even after an A-to-B-to-A cycle.
- UC18 Registration remains separate from authentication and never auto-logs in.

## UC19 implementation status

- The 7.2 Bearer-client surface models login and `/me` only. Refresh and logout
  use the dedicated cookie-only `RefreshApiService` (not `AuthRepository`).
- `AuthRepository` maps HTTP, network and invalid-response failures without
  exposing backend or credential details.
- Drawer / family identity uses `AuthSessionStore` (no separate `CurrentUserStore`).
- The 7.3 session foundation stores the access token and authenticated-user
  summary in Keystore-backed encrypted preferences with a synchronized memory
  snapshot for future request authentication.
- The shared OkHttp client now uses an encrypted persistent `CookieJar` limited
  to the `canmakan_refresh` cookie. OkHttp host, path, Secure and expiry matching
  remain authoritative; rotation replaces cookies by name/domain/path.
- OkHttp 5.4's cookie model preserves SameSite, but `CookieJar` request matching
  has no browser-style site context and therefore cannot enforce SameSite itself.
- Shared preferences are already excluded from cloud backup and device transfer.
- The 7.4 `LoginViewModel` validates and normalizes email input, preserves the
  password exactly, prevents duplicate submissions, calls `AuthRepository`, and
  considers Login successful only after `AuthSessionStore` persists the session.
  It may re-offer a pending invitation token into `PendingInvitationStore` but
  does not claim invitations itself.
- The Compose Login route exposes only a safe authenticated-user success callback
  and links to the separate UC18 Registration route.
- The 7.5 Bearer interceptor reads the current access token from
  `AuthSessionStore` for each eligible request, restricts it to the configured
  API scheme/host/port, and excludes exact public auth endpoints.
- `AuthRepository.getCurrentUser()` now reaches `GET auth/me` through the shared
  authenticated OkHttp client. It returns the Backend user without rewriting
  stored user metadata.
- The 7.6 refresh path uses a dedicated synchronous Retrofit service sharing the
  persistent refresh `CookieJar`, with no Bearer interceptor, Authenticator,
  generic retry, or HTTP logging. Its endpoint declares no internal control header.
- `AuthRefreshCoordinator` serializes rotating refresh calls, double-checks the
  current token while holding its refresh lock, persists a valid replacement
  session before releasing waiters, and fails closed after confirmed invalidation,
  invalid rotated responses, or session-persistence failure. Network and 5xx
  failures preserve the existing session and cookie for later recovery.
- `BearerAuthenticator` handles one eligible first-party 401 recovery only;
  public auth endpoints, foreign origins, requests without Bearer credentials,
  403 responses, and an already retried request never trigger refresh.
- The shared generic retry layer now treats all HTTP 4xx responses as terminal;
  its existing transient 5xx and I/O retry behavior remains for ordinary
  requests, while an authentication follow-up response is never retried again.
- `AuthSessionRestorer` provides token-safe `Authenticated`, `Unauthenticated`,
  `TemporarilyUnavailable`, and `Forbidden` results. Backend `/me` metadata
  replaces the stored user summary while preserving the current access token.
- The 7.7 root `AppAuthViewModel` begins in `Restoring` and validates persisted
  credentials before allowing either auth or consumer UI. Its public StateFlow
  contains only safe user metadata; `AuthSessionStore` exposes a matching
  token-free user signal for session invalidation and backend-authoritative role
  changes.
- USER accounts enter the existing consumer mobile graph. Canonical ADMIN
  accounts never map to family `PRIMARY_ADMIN` and instead receive a small
  unsupported-mobile-account state with Sign Out.
- Login/Registration and consumer-main graphs have separate root compositions,
  so successful Login, confirmed session expiry and Logout destroy the opposite
  navigation back stack.
- User Logout runs synchronously on an IO worker through the same lock as refresh.
  It clears access state, attempts the dedicated non-recursive cookie-authenticated
  backend logout, then clears access state and refresh cookies in every outcome.
  The dedicated client has no Bearer interceptor, Authenticator, generic retry or
  HTTP logging.

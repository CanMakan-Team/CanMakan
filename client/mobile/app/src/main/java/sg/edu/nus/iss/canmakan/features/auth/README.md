# features/auth

User registration, authentication and session management.

## Responsibilities
- UC18 two-screen account registration flow
- Registration request/response mapping and backend error handling
- Optional dietary selection capture without inventing a profile-creation API
- Login screen
- Logout
- Token storage and session restoration
- Handling unauthenticated state

## Notes
- Uses security utilities from `core`
- Does not own role/permission logic (handled by backend)
- Registration uses the shared Retrofit/Hilt client and sends only email/password.
- The current backend has no compatible primary-profile creation endpoint, so
  selected dietary information is deferred after account creation instead of
  creating an empty or family/dependant profile.
- Login, logout, tokens and session restoration remain deferred to UC19.

## UC19 implementation status

- The 7.2 data/network foundation models login, refresh, logout and `/me`.
- `AuthRepository` maps HTTP, network and invalid-response failures without
  exposing backend or credential details.
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
- The standalone Compose Login route exposes only a safe authenticated-user
  success callback and is not yet connected to root Navigation.
- Bearer authentication, automatic refresh and authentication navigation remain
  intentionally deferred.

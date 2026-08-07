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

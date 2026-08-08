# auth

User registration, authentication and session management.

## Purpose
UC18 public account registration and the UC19 login, refresh, and current-session
logout flow are implemented.

## Responsibilities
- Public user registration (`POST /api/auth/register`)
- Server-owned normal `USER` role assignment
- BCrypt password encoding
- Login, refresh, and logout endpoints
- Access-JWT issuance
- Opaque refresh-session persistence and one-time rotation
- Current refresh-session invalidation and cookie clearing
- Password handling (if applicable)

## Registration boundary
- Accepts only email and password.
- Creates only a `UserAccount`; it does not create profiles, families or sessions.
- Registration does not create a login refresh session; login owns that lifecycle.

## Related packages
- Uses `shared/security` for the underlying JWT and filter machinery
- Does **not** contain role/permission definitions (those stay in `shared/security`)

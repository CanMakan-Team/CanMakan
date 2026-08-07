# auth

User registration, authentication and session management.

## Purpose
UC18 public account registration is implemented. Login, logout, token issuance
and session lifecycle remain planned.

## Responsibilities
- Public user registration (`POST /api/auth/register`)
- Server-owned normal `USER` role assignment
- BCrypt password encoding
- Login / logout endpoints
- JWT token creation and refresh
- Session invalidation
- Password handling (if applicable)

## Registration boundary
- Accepts only email and password.
- Creates only a `UserAccount`; it does not create profiles, families or sessions.
- Login, logout and JWT behavior are not implemented by UC18.

## Related packages
- Uses `common/security` for the underlying JWT and filter machinery
- Does **not** contain role/permission definitions (those stay in `common/security`)

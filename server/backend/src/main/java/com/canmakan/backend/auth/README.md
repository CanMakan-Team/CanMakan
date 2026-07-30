# auth

User authentication and session management.

## Purpose
Handles login, logout, token issuance and session lifecycle.

## Responsibilities
- Login / logout endpoints
- JWT token creation and refresh
- Session invalidation
- Password handling (if applicable)

## Related packages
- Uses `common/security` for the underlying JWT and filter machinery
- Does **not** contain role/permission definitions (those stay in `common/security`)
# shared/security

Security infrastructure (not the login feature).

## Purpose
Implements authentication and authorization infrastructure used by the whole application.

## Current foundation
- `SystemRole` supports only the database roles `USER` and `ADMIN` and maps them
  to `ROLE_USER` and `ROLE_ADMIN`.
- `AuthUserDetailsService` loads the account and current role in one query.
- `AuthenticatedPrincipal` and `AuthUserDetails` keep persistence entities out
  of the Spring Security principal.
- `SecurityConfig` exposes the standard DAO authentication provider and manager
  using the same BCrypt encoder as UC18 registration.
- `JwtService` issues HS256 access tokens containing only standard identity
  claims; `JwtAuthenticationFilter` validates them and reloads the current
  account status and role before populating the security context.

The current stateless filter chain protects `/api/auth/me`, restricts
`/api/admin/**` to `ADMIN`, and keeps registration, login, refresh, and health
public at the bearer-authorization layer. The refresh endpoint authenticates an
opaque, hashed-at-rest, one-time refresh session from an HttpOnly,
SameSite=Strict cookie. Other existing business routes remain temporarily
permitted until their owning Use Cases adopt resource authorization. It is not
the final application security policy.

## Note
The login and refresh HTTP flows live in the `auth` package. Logout is not yet
implemented. This package only provides the shared security machinery.

Family create/`/me` currently accept a temporary `X-User-Id` header on the controller
until UC19 (Spring Security + JWT) replaces it with `@AuthenticationPrincipal`.

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
## Typical contents
- JWT filter / token provider (UC19)
- `SecurityFilterChain` configuration (UC19)
- Role hierarchy and permission constants
- Method security expressions
- Current user extraction utilities
- **CORS** (`CorsConfig` / `CorsProperties`) for browser clients

## CORS (current)

Browser clients (Vite web on `:5173` / preview `:4173`) call `http://localhost:8080`
cross-origin. `CorsFilter` allows:

| Client | How it is covered |
| --- | --- |
| Web Vite / preview | Exact origins `localhost` + `127.0.0.1` on ports 5173 and 4173 |
| LAN / physical device browser | Origin patterns `10.*`, `192.168.*`, `172.*` any port |
| Android Retrofit (emulator `10.0.2.2`, device LAN IP) | Usually **no** `Origin` header — CORS does not apply; server already binds `0.0.0.0:8080` |

Allowed request headers include `Authorization`, `Content-Type`, `Accept`, and
temporary `X-User-Id`. Credentials mode is off (session is localStorage, not cookies).

### Configuration (local defaults + deploy overrides)

Properties live under `canmakan.cors.*` in `application.properties`. Each value
supports an environment override so production can change the allow-list without
a rebuild:

| Property | Environment variable | Local default |
| --- | --- | --- |
| `allowed-origins` | `CANMAKAN_CORS_ALLOWED_ORIGINS` | Vite/preview localhost origins |
| `allowed-origin-patterns` | `CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS` | Private LAN patterns |
| `allow-credentials` | `CANMAKAN_CORS_ALLOW_CREDENTIALS` | `false` |
| `max-age-seconds` | `CANMAKAN_CORS_MAX_AGE_SECONDS` | `3600` |

Example production deploy:

```bash
export CANMAKAN_CORS_ALLOWED_ORIGINS=https://app.example.com,https://www.example.com
export CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS=
```

Empty `CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS` disables LAN wildcards; only exact
origins remain. If web and API share the same origin behind a reverse proxy,
CORS is unused by the browser but these settings remain harmless.

## Note
The actual **login / logout / session** endpoints and flow live in the `auth` package.
This package only provides the underlying security machinery.

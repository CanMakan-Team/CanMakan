# shared/security

Security infrastructure (not the login feature).

## Purpose
Implements authentication/authorization mechanisms used by the whole application.

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

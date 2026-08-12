# API Documentation

## Family circle (UC8 / UC9 / UC10 / UC11)

**Status:** Create + `/me` + invite/dependant (UC9) + invitee inbox (UC10) + active profile (UC11) + restriction summary; JWT principal.

See [`families.md`](families.md) for:

- `POST /api/families` — create circle + PRIMARY_ADMIN + SELF profile
- `GET /api/families/me` — current family context
- `GET /api/families/me/active-profile` — read persisted scan profile (or default)
- `PUT /api/families/me/active-profile` — persist scan profile selection
- `GET /api/families/me/members` — roster of linked members + dependants
- `GET /api/families/me/user-search` — PRIMARY_ADMIN email search (incl. NOT_REGISTERED)
- `POST /api/families/me/invitations` — PENDING invite with `inviteUrl` + `inviteCode` (+ optional Resend email)
- `POST /api/families/me/invitations/claim` — join family from token while authenticated
- `GET /api/invitations/me` — invitee pending inbox
- `POST /api/invitations/{token}/accept` — accept (MEMBER + SELF profile)
- `POST /api/invitations/{token}/decline` — decline (DECLINED)
- `POST /api/families/me/profiles` — dependant profile (`linked_user_id` NULL)
- Bearer JWT / `@AuthenticationPrincipal` on family and invitation routes
- Invite → join workflow diagram (register-login-claim / deep-link claim / inbox accept)

## UC18 user registration

`POST /api/auth/register`

Request:

```json
{
  "email": "person@example.com",
  "password": "a-password-of-at-least-8-characters"
}
```

Two deprecated optional fields remain accepted temporarily for older clients:
`name` and `invitationToken`. Neither has a registration side effect. `name` is
not durable account state; the durable `profileName` belongs to authenticated
SELF-profile setup. New clients preserve invitation tokens until explicit login
and then call the authenticated UC9 claim endpoint.

The backend normalizes email, hashes the password with BCrypt, assigns the
existing `USER` role, and creates only an active account. It does not create a
dietary profile, family membership, access token, refresh session, or login
session.

The current `users` schema has no name column. Legacy clients may still send an
optional `name`, but registration does not store or return it.

Success: `201 Created`

```json
{
  "userId": 14,
  "email": "person@example.com",
  "active": true
}
```

Errors use `{"message":"..."}`:

- `400 Bad Request` for invalid input or unsupported request fields.
- `409 Conflict` when the normalized email already exists.
- `500 Internal Server Error` with a generic message for unexpected failures.

UC8 create-circle and UC9 invitation acceptance continue to create a missing
SELF profile when needed.

## Authenticated SELF profile setup

`POST /api/profiles/me`

Requires a Bearer JWT for a platform `USER`; `ADMIN` receives `403`. The caller
identity is taken only from the authenticated principal. Supplying `userId` or
other unsupported fields returns `400`.

Request:

```json
{
  "profileName": "Person Name",
  "restrictions": {
    "2": "STRICT_AVOID",
    "5": "INTOLERANCE"
  }
}
```

`restrictions` is optional. Profile creation and all supplied restriction
selections commit atomically in a transaction separate from account
registration. Supported severity values are currently `STRICT_AVOID` and
`INTOLERANCE`; other values, including `PREFERENCE`, return `400` until their
engine semantics are implemented.

Success: `201 Created`

```json
{
  "profileId": 77,
  "profileName": "Person Name",
  "relationship": "SELF",
  "active": true,
  "restrictions": {
    "2": "STRICT_AVOID",
    "5": "INTOLERANCE"
  }
}
```

Errors use `{"message":"..."}`:

- `400 Bad Request` for invalid names, restrictions, severities, or unsupported fields.
- `401 Unauthorized` without a valid authenticated account.
- `403 Forbidden` for a platform `ADMIN`.
- `409 Conflict` when the caller already has a linked SELF profile.

Failure of this endpoint rolls back only profile setup. The previously
committed account remains valid.

## Login (UC19 JWT)

`POST /api/auth/login`

Request:

```json
{
  "email": "person@example.com",
  "password": "a-password-of-at-least-8-characters"
}
```

Success: `200 OK` with refresh cookie and body:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "userId": 14,
    "email": "person@example.com",
    "role": "USER",
    "active": true
  }
}
```

Invalid or inactive accounts return `401` with
`{"message":"Invalid credentials or account unavailable."}`.

### Role distinction

| Concept | Meaning |
| --- | --- |
| Platform `USER` / JWT `role: USER` | Normal registered app account |
| Platform `ADMIN` / JWT `role: ADMIN` | System staff |
| Web `ROLE_FAMILY_ADMIN` | Portal gate mapped from JWT `USER` on the web client |
| Web `ROLE_SYSTEM_ADMIN` | Portal gate mapped from JWT `ADMIN` on the web client |
| DB `PRIMARY_ADMIN` / `MEMBER` | Real family-circle role on `family_members` after join/create |

Web clients reject the wrong portal (e.g. `ADMIN` on `/family-login`) with a client-side
message and clear the session. A platform `USER` with no circle still enters `/family`
and sees create-circle (`GET /api/families/me` → 404).

Web clients keep the access token and mapped portal roles in memory only. On
startup they call `POST /api/auth/refresh` with the path-scoped HttpOnly cookie,
then verify `GET /api/auth/me` before rendering a protected route. `/me` returns
`userId`, `email`, `role`, and `active`. Web Locks serialise refresh-cookie
mutations across tabs. A protected safe request may retry once after refresh;
mutating requests and auth endpoints are never automatically replayed.

Allowed headers include `Content-Type` and `Authorization`.

## CORS (browser clients)

Browser web (Vite → Spring on `:8080`) is cross-origin. Backend
`canmakan.cors.*` defaults allow:

- Exact: `http://localhost:5173`, `http://127.0.0.1:5173`, preview `:4173`
- Patterns: empty by default; local LAN browser testing must opt in explicitly

Override at deploy time (comma-separated) without rebuilding:

- `CANMAKAN_CORS_ALLOWED_ORIGINS` — exact web origins (e.g. `https://app.example.com`)
- `CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS` — optional patterns; set empty to disable LAN wildcards in prod
- `CANMAKAN_CORS_ALLOW_CREDENTIALS` (default `true`) / `CANMAKAN_CORS_MAX_AGE_SECONDS`

The default refresh cookie is `HttpOnly; SameSite=Lax`. Separately hosted HTTPS
web and API deployments must explicitly choose `SameSite=None`, retain
`Secure=true`, enable credentialed CORS, configure only exact HTTPS origins, and
leave origin patterns empty. Login, refresh, and logout require the API's
session-intent request header; browsers must pass its preflight and exact-origin
check, while native clients send the same header without an Origin. Invalid
`SameSite=None` deployment combinations fail startup validation.

Allowed headers include `Content-Type` and `Authorization`.

Native Android Retrofit (emulator `http://10.0.2.2:8080/api/` or device LAN IP)
typically sends no `Origin`, so CORS does not apply; the API still must listen on
`0.0.0.0` (default `server.address`).

# API Documentation

## Family circle (UC8 / UC9)

**Status:** Create + `/me` + invite/dependant (UC9) + restriction summary; JWT principal.

See [`families.md`](families.md) for:

- `POST /api/families` — create circle + PRIMARY_ADMIN + SELF profile
- `GET /api/families/me` — current family context
- `GET /api/families/me/members` — roster of linked members + dependants
- `GET /api/families/me/user-search` — PRIMARY_ADMIN email search (incl. NOT_REGISTERED)
- `POST /api/families/me/invitations` — PENDING invite with `inviteUrl` + `inviteCode`
- `POST /api/families/me/invitations/claim` — join family from token while authenticated
- `POST /api/families/me/profiles` — dependant profile (`linked_user_id` NULL)
- Bearer JWT / `@AuthenticationPrincipal` on family routes

## UC18 user registration

`POST /api/auth/register`

Request:

```json
{
  "name": "Person Name",
  "email": "person@example.com",
  "password": "a-password-of-at-least-8-characters",
  "invitationToken": "optional-uc9-invite-token"
}
```

`invitationToken` is optional. When present (or when a single PENDING invite
matches the email), registration auto-claims the invite: MEMBER membership,
SELF profile attached to the family, invitation `ACCEPTED`.

The backend normalizes email, hashes the password with BCrypt, assigns the
existing `USER` role, creates an active standalone account, and creates a
family-less SELF dietary profile (`family_id` NULL) named from `name` (then
attaches it if an invite is claimed).

Success: `201 Created`

```json
{
  "userId": 14,
  "profileId": 77,
  "name": "Person Name",
  "email": "person@example.com",
  "active": true
}
```

Errors use `{"message":"..."}`:

- `400 Bad Request` for invalid input or unsupported request fields.
- `409 Conflict` when the normalized email already exists.
- `500 Internal Server Error` with a generic message for unexpected failures.

Registration does not issue a JWT or create a family circle. UC8 create-circle
reuses the SELF profile when the user later creates a household.

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
    "role": "USER"
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

Web clients store `canmakan.session` (including `accessToken` and portal roles) and
send `Authorization: Bearer <accessToken>` on API calls.

Allowed headers include `Content-Type` and `Authorization`.

## CORS (browser clients)

Browser web (Vite → Spring on `:8080`) is cross-origin. Backend
`canmakan.cors.*` defaults allow:

- Exact: `http://localhost:5173`, `http://127.0.0.1:5173`, preview `:4173`
- Patterns: private LAN hosts (`10.*`, `192.168.*`, `172.*`) any port

Override at deploy time (comma-separated) without rebuilding:

- `CANMAKAN_CORS_ALLOWED_ORIGINS` — exact web origins (e.g. `https://app.example.com`)
- `CANMAKAN_CORS_ALLOWED_ORIGIN_PATTERNS` — optional patterns; set empty to disable LAN wildcards in prod
- `CANMAKAN_CORS_ALLOW_CREDENTIALS` / `CANMAKAN_CORS_MAX_AGE_SECONDS`

Allowed headers include `Content-Type` and `Authorization`.

Native Android Retrofit (emulator `http://10.0.2.2:8080/api/` or device LAN IP)
typically sends no `Origin`, so CORS does not apply; the API still must listen on
`0.0.0.0` (default `server.address`).

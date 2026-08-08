# API Documentation

## Family circle (UC8)

**Status:** Partial — create + `/me` + D2 + web empty-state shipped; JWT auth → UC19; mobile `/me` → UC11.

See [`families.md`](families.md) for:

- `POST /api/families` — create circle + PRIMARY_ADMIN + SELF profile
- `GET /api/families/me` — current family context
- Temporary `X-User-Id` header on the controller (not real authentication)

## UC18 user registration

`POST /api/auth/register`

Request:

```json
{
  "name": "Person Name",
  "email": "person@example.com",
  "password": "a-password-of-at-least-8-characters"
}
```

The backend normalizes email, hashes the password with BCrypt, assigns the
existing `USER` role, creates an active standalone account, and creates a
family-less SELF dietary profile (`family_id` NULL) named from `name`.

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

## Pre-JWT login

`POST /api/auth/login`

Request:

```json
{
  "email": "person@example.com",
  "password": "a-password-of-at-least-8-characters"
}
```

Success: `200 OK`

```json
{
  "userId": 14,
  "displayName": "Person Name",
  "roles": ["ROLE_APP_USER", "ROLE_FAMILY_ADMIN"],
  "prototype": false
}
```

Platform `ADMIN` maps to `["ROLE_SYSTEM_ADMIN"]`. Invalid or inactive accounts
return `401` with `{"message":"Invalid email or password."}`.

### Role distinction (interim, pre-JWT)

| Concept | Meaning |
| --- | --- |
| Platform `USER` | Normal registered app account |
| Platform `ADMIN` | System staff |
| Web `ROLE_FAMILY_ADMIN` | May use the family portal (assigned to all platform `USER` logins today) |
| Web `ROLE_SYSTEM_ADMIN` | May use the system portal |
| DB `PRIMARY_ADMIN` / `MEMBER` | Real family-circle role on `family_members` after join/create |

Web clients reject the wrong portal (e.g. `ADMIN` on `/family-login`) with a client-side
message and clear the session. A platform `USER` with no circle still enters `/family`
and sees create-circle (`GET /api/families/me` → 404).

Web clients store this as `canmakan.session` (plus `portal`) and send
`X-User-Id` until UC19 JWT replaces it.

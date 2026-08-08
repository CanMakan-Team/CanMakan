# API Documentation

## Family circle (UC8)

See [`families.md`](families.md) for:

- `POST /api/families` — create circle + PRIMARY_ADMIN + SELF profile
- `GET /api/families/me` — current family context
- Pre-JWT temporary `X-User-Id` header on the controller (auth under UC19)

## UC18 user registration

`POST /api/auth/register`

Request:

```json
{
  "email": "person@example.com",
  "password": "a-password-of-at-least-8-characters"
}
```

The backend normalizes email, hashes the password with BCrypt, assigns the
existing `USER` role, and creates an active standalone account. The public
request cannot select a role, status, family, profile or dietary data.

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

Registration does not issue a JWT, refresh token or login session, and does not
create a dietary profile, family, dependant profile or subscription.

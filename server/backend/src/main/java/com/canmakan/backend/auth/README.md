# auth

UC18 registration and UC19 JWT authentication lifecycle.

## Purpose
Public account registration plus login, refresh, logout, and `/me` against Spring Security.

## Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/auth/register` | `email`, `password` → active `USER` account only; deprecated optional `name` accepted |
| `POST` | `/api/auth/login` | `email`, `password` → `AuthResponse` (access JWT + user) + refresh cookie |
| `POST` | `/api/auth/refresh` | Rotate refresh cookie → new access JWT |
| `POST` | `/api/auth/logout` | Revoke refresh session + clear cookie |
| `GET` | `/api/auth/me` | Requires Bearer JWT; returns `userId`, `email`, `role`, and `active` |

Login and register are owned by `AuthController` / `AuthService` (single mapping for each path).

## Registration boundary
- Requires `email` and `password`. Deprecated optional `name` and
  `invitationToken` inputs remain accepted temporarily for older clients.
- Creates only the `users` row; no dietary profile or family membership is created.
- Does **not** create a family circle or issue tokens; clients must login after register.
- `name` is not an account column and is neither stored nor returned. Durable
  `profileName` belongs exclusively to later authenticated SELF profile setup.
- A transitional `invitationToken` is accepted but never claimed during registration;
  the authenticated UC9 claim endpoint owns that side effect after login.

## Authenticated SELF profile setup

`POST /api/profiles/me` is restricted to an authenticated platform `USER`.
The JWT principal supplies the account id; request bodies cannot choose a user.
It creates the caller's one linked, standalone `SELF` profile and optional
restriction selections in a separate transaction. This setup transaction can
roll back without affecting the previously committed account.

## Roles

| Concept | Where | Meaning |
| --- | --- | --- |
| Platform `USER` / `ADMIN` | `roles` / JWT `SystemRole` | Account type (`ROLE_USER` / `ROLE_ADMIN`) |
| `PRIMARY_ADMIN` / `MEMBER` | `family_members.member_role` | Household role after UC8 create / UC10 accept |

Web portals may map `USER` → family-portal access and `ADMIN` → system portal in the client.

## Related packages
- `auth.dto` / `auth.model` / `auth.exception` / `auth.repository` — request/response, entities, errors, persistence
- `user` — `UserAccount` / repository
- `dietaryprofile` — authenticated SELF profile setup and restrictions
- `shared/security` — JWT filter, `AuthUserDetails`, SecurityFilterChain
- `family` — UC8 create-circle / membership (JWT principal)

Controller and service classes stay in `auth` (this package root).

The refresh cookie is HttpOnly and path-scoped to `/api/auth`. Its Secure and
SameSite attributes are deployment configuration; `SameSite=None` is accepted
only with `Secure=true`, credentialed CORS, exact HTTPS origins, and no origin
patterns. `Lax` is the default. Login, refresh, and logout require a non-secret
session-intent request header. Browser requests additionally require an exact
configured Origin; native requests may omit Origin but must retain the header.

## Ops note
With `spring.sql.init.mode=always`, schema/seed reload wipes newly registered users on backend restart.

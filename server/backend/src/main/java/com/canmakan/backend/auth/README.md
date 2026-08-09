# auth

UC18 registration and UC19 JWT authentication lifecycle.

## Purpose
Public account registration plus login, refresh, logout, and `/me` against Spring Security.

## Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/auth/register` | `name`, `email`, `password` → active `USER` + family-less SELF profile |
| `POST` | `/api/auth/login` | `email`, `password` → `AuthResponse` (access JWT + user) + refresh cookie |
| `POST` | `/api/auth/refresh` | Rotate refresh cookie → new access JWT |
| `POST` | `/api/auth/logout` | Revoke refresh session + clear cookie |
| `GET` | `/api/auth/me` | Requires Bearer JWT |

Login and register are owned by `AuthController` / `AuthService` (single mapping for each path).

## Registration boundary
- Accepts `name`, `email`, `password` only.
- Creates `users` row + SELF `dietary_profiles` with `family_id` NULL.
- Does **not** create a family circle or issue tokens; clients must login after register.

## Roles

| Concept | Where | Meaning |
| --- | --- | --- |
| Platform `USER` / `ADMIN` | `roles` / JWT `SystemRole` | Account type (`ROLE_USER` / `ROLE_ADMIN`) |
| `PRIMARY_ADMIN` / `MEMBER` | `family_members.member_role` | Household role after UC8 create / UC10 accept |

Web portals may map `USER` → family-portal access and `ADMIN` → system portal in the client.

## Related packages
- `auth.dto` / `auth.model` / `auth.exception` / `auth.repository` — request/response, entities, errors, persistence
- `user` — `UserAccount` / repository
- `dietaryprofile` — SELF profile on register
- `shared/security` — JWT filter, `AuthUserDetails`, SecurityFilterChain
- `family` — UC8 create-circle / membership (JWT principal)

Controller and service classes stay in `auth` (this package root).

## Ops note
With `spring.sql.init.mode=always`, schema/seed reload wipes newly registered users on backend restart.

# auth

User registration and pre-JWT email/password login.

## Purpose
UC18 public registration and a minimal login endpoint so web clients can obtain
a `userId` for temporary `X-User-Id` identity until UC19 JWT lands.

## Endpoints
| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/auth/register` | `name`, `email`, `password` → active `USER` + family-less SELF profile |
| `POST` | `/api/auth/login` | `email`, `password` → `userId`, `displayName`, web `roles`, `prototype: false` |

## Responsibilities
- Public user registration with BCrypt password encoding
- Server-owned platform `USER` role on register (clients cannot choose role)
- Email/password verification and inactive-account rejection
- Interim web role mapping (see below)
- Does **not** issue JWT/refresh tokens (UC19)
- Does **not** create a family circle (UC8)

## Platform USER vs ADMIN vs family admin (interim)

| Concept | Where | Meaning |
| --- | --- | --- |
| Platform `USER` | `roles` / `users.role_id` | Normal app account (registration default) |
| Platform `ADMIN` | `roles` / `users.role_id` | System staff |
| Web `ROLE_FAMILY_ADMIN` | Login response `roles` | May enter the **family web portal** (interim claim) |
| Web `ROLE_SYSTEM_ADMIN` | Login response `roles` | May enter the **system web portal** |
| `PRIMARY_ADMIN` / `MEMBER` | `family_members.member_role` | Real household role **after** the user belongs to a circle |

`LoginService` mapping today:

- Platform `USER` → `["ROLE_APP_USER", "ROLE_FAMILY_ADMIN"]` so registrants can reach UC8 create-circle.
- Platform `ADMIN` → `["ROLE_SYSTEM_ADMIN"]`.

Web `ROLE_FAMILY_ADMIN` is **not** the same as DB `PRIMARY_ADMIN`. Creating a circle (`POST /api/families`) makes the creator `PRIMARY_ADMIN` in `family_members`.

## Registration boundary
- Accepts `name`, `email`, `password` only.
- Email / password rules live on `RegistrationRequest` (Jakarta `@Email`, `@Pattern`, `@Size`, BCrypt byte `@AssertTrue`).
  Email requires a dotted domain (rejects `test1@abc`). Password needs 8+ chars with upper, lower, digit, and special.
- Creates `users` row + SELF `dietary_profiles` with `family_id` NULL.
- UC8 `POST /api/families` attaches that profile when the user creates a circle.
- Clients cannot choose platform role or create ADMIN via this API.

## Ops note
With `spring.sql.init.mode=always`, schema/seed reload wipes newly registered users on backend restart.

## Related packages
- `user` — `UserAccount` / repository
- `dietaryprofile` — SELF profile on register; attached by family create
- `shared/security` — planned JWT / SecurityFilterChain (not implemented yet)
- `family` — UC8 create-circle / membership roles

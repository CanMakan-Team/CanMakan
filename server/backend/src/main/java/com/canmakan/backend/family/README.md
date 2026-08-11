# family

Family accounts and member relationships.

## Purpose
Manages family grouping, member profiles, and active profile selection.

## UC8 progress — Create Family Circle

**Status:** Partial (feature path implemented; real auth deferred to UC19)

| Story | Status | Notes |
| --- | --- | --- |
| **UC8-S1** | Done | `UNIQUE(family_members.user_id)` via `uq_family_members_user_id` in `00_schema.sql` (D2) |
| **UC8-S2** | Done | `POST /api/families` — transactional `families` + `PRIMARY_ADMIN` + SELF profile |
| **UC8-S3** | Done | `GET /api/families/me` — family context for caller |
| **UC8-S4** | Done (web + mobile) | Web `FamilyMeGate` / `CreateFamilyCirclePage`; mobile drawer + `CreateFamilyCircleScreen` |

**Caller identity:** controller takes `@AuthenticationPrincipal AuthUserDetails` and passes `userId` into `FamilyService`. Family routes require authentication in `SecurityConfig`.

**Also done:** name validation via `@Valid` on `CreateFamilyRequest`; second create → **409** (D2); missing user for header → **401**.

**Create-circle tip:** seeded app users 4–13 already belong to Tan/Lim/Wong families. Register a new account (`POST /api/auth/register` / web `/family-register`) to exercise empty-state create (`GET /families/me` → 404).

**Still open for UC8 ACs / follow-ons:**
- AC8: real unauthenticated → HTTP 401 (UC19)
- Package layout: `dto/`, `model/`, `repository/`, `exception/` under this package

**Family client ownership (product):** create + invite on mobile and web; accept mainly mobile; switch mobile-only; manage web-primary (mobile optional/limited).

## Layout

```
family/
  FamilyController.java
  FamilyService.java
  README.md
  dto/           # API request/response types
  model/         # JPA entities (Family, FamilyMember)
  repository/
  exception/
```

API contract: `docs/api/families.md`

## Still planned (other UCs)
- Invite via shareable link/code (UC9 — mobile + web); dependant create API (web-primary UI)
- Accept invitation (UC10 — mobile primary; web optional)
- Switch active family profile (UC11 — mobile)
- Family allergy summary grid (UC6)
- Manage members / activate profiles (UC12 — web primary)

## Related packages
- Uses `dietaryprofile` for SELF bootstrap on create and profile restrictions
- Temporary caller header documented in `shared/security` README until UC19

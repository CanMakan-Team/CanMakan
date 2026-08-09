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
| **UC8-S4** | Done (web) | Create empty-state lives in `client/web` (`FamilyMeGate` / `CreateFamilyCirclePage`) |

**Caller identity (temporary):** controller takes `X-User-Id` and passes `long userId` into `FamilyService`. This is **not** authentication. UC19 should swap to JWT / `@AuthenticationPrincipal` (AC8 → 401). Service signature can stay.

**Also done:** name validation via `@Valid` on `CreateFamilyRequest`; second create → **409** (D2); missing user for header → **401**.

**Create-circle tip:** seeded app users 4–13 already belong to Tan/Lim/Wong families. Register a new account (`POST /api/auth/register` / web `/family-register`) to exercise empty-state create (`GET /families/me` → 404).

**Still open for UC8 ACs / follow-ons:**
- AC8: real unauthenticated → HTTP 401 (UC19)
- Mobile: resolves `/families/me` + no-family “set up on web” guidance (create UI remains web-only)
- Package layout: `dto/`, `model/`, `repository/`, `exception/` under this package

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
- Invite / dependant create (UC9); accept invitation (UC10)
- Switch active family profile (UC11)
- Family allergy summary grid (UC6)
- Manage members / activate profiles (UC12)

## Related packages
- Uses `dietaryprofile` for SELF bootstrap on create and profile restrictions
- Temporary caller header documented in `shared/security` README until UC19

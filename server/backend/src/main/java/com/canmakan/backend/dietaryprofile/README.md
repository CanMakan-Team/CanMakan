# dietaryprofile

An individual’s dietary needs and restrictions.

## Package layout

| Subpackage | Contents |
| --- | --- |
| `model/` | JPA entities (`DietaryProfile`, `DietaryRestriction`, `ProfileRestriction`) |
| `repository/` | Spring Data repos — one per entity |
| `dto/` | API request/response records |
| `service/` | `DietaryProfileService`, `RestrictionRuleLoader` |
| *(root)* | `DietaryProfileController` |

## Purpose

Manages a person’s dietary profile independently of family relationships.

## Responsibilities

- Create / update / retrieve dietary profiles
- Preset constraints (Halal, peanut-free, dairy-free, low-sugar, keto, etc.)
- Restriction catalog GET and per-profile PUT (UC1)
- Authenticated standalone SELF profile setup (`POST /api/profiles/me`)
- Bridge profile restrictions into scan verdict rules (`RestrictionRuleLoader`)

## Ownership model

- A dietary profile may be standalone (`family_id` NULL) until UC8/UC9 attaches it.
- A dietary profile may be linked to one login account (`linked_user_id`).
- `linked_user_id` is unique, so one user account maps to at most one profile.

## Authorization expectations

- Family admin can manage all profiles in their family.
- Non-admin members can edit only the profile linked to their own user account.
- Dependent profiles can exist without linked login users.
- SELF creation takes identity only from an authenticated platform `USER` and
  persists its optional restrictions atomically.
- SELF setup accepts only the scan engine's current severity vocabulary:
  `STRICT_AVOID` and `INTOLERANCE`.
- Later SELF edits (`PUT /api/profiles/me`) and the mobile restriction editor
  (`PUT /api/profiles/{profileId}/restrictions`) also accept `PREFERENCE`, so
  existing diet-preference rows are not rejected on save.

## Related packages

Family membership and active profile switching live in the `family` package.

**UC8:** creating a family circle bootstraps a SELF `dietary_profiles` row
(`linked_user_id` = creator, `family_id` set, `is_primary = true`) inside the
same transaction as `POST /api/families`, or attaches the standalone SELF
profile created earlier through `POST /api/profiles/me`.

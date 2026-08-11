# dietaryprofile

An individual’s dietary needs and restrictions.

## Package layout

| Subpackage | Contents |
| --- | --- |
| `model/` | JPA entities (`DietaryProfile`, `DietaryRestriction`, `ProfileRestriction`) |
| `repository/` | Spring Data repos — one per entity |
| `dto/` | API response records |
| `service/` | `DietaryProfileService`, `RestrictionRuleLoader` |
| *(root)* | `DietaryProfileController` |

## Purpose

Manages a person’s dietary profile independently of family relationships.

## Responsibilities

- Create / update / retrieve dietary profiles
- Preset constraints (Halal, peanut-free, dairy-free, low-sugar, keto, etc.)
- Restriction catalog GET and per-profile PUT (UC1)
- Bridge profile restrictions into scan verdict rules (`RestrictionRuleLoader`)

## Ownership model

- A dietary profile belongs to one family (`family_id`).
- A dietary profile may be linked to one login account (`linked_user_id`).
- `linked_user_id` is unique, so one user account maps to at most one profile.

## Authorization expectations

- Family admin can manage all profiles in their family.
- Non-admin members can edit only the profile linked to their own user account.
- Dependent profiles can exist without linked login users.

## Related packages

Family membership and active profile switching live in the `family` package.

**UC8:** creating a family circle bootstraps a SELF `dietary_profiles` row
(`linked_user_id` = creator, `family_id` set, `is_primary = true`) inside the
same transaction as `POST /api/families`.

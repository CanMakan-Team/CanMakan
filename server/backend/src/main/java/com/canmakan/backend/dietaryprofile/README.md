# dietaryprofile

An individual’s dietary needs and restrictions.

## Purpose
Manages a person’s dietary profile independently of family relationships.

## Responsibilities
- Create / update / retrieve dietary profiles
- Preset constraints (Halal, peanut-free, dairy-free, low-sugar, keto, etc.)
- Custom constraints
- Validation of dietary rules

## Ownership Model
- A dietary profile belongs to one family (`family_id`).
- A dietary profile may be linked to one login account (`linked_user_id`).
- `linked_user_id` is unique, so one user account maps to at most one profile.

## Authorization Expectations
- Family admin can manage all profiles in their family.
- Non-admin members can edit only the profile linked to their own user account.
- Dependent profiles can exist without linked login users.

## Note
Family membership and “active profile” switching live in the `family` package.

**UC8:** creating a family circle bootstraps a SELF `dietary_profiles` row
(`linked_user_id` = creator, `family_id` set, `is_primary = true`) inside the
same transaction as `POST /api/families`. Restriction catalog GET/PUT remains
this package’s responsibility (UC1).

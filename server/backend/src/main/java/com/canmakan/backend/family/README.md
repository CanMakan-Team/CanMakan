# family

Family accounts and member relationships.

## Purpose
Manages family grouping, member profiles, and active profile selection.

## UC8 (implemented)
- `POST /api/families` — create circle; caller becomes `PRIMARY_ADMIN`; SELF dietary profile bootstrap
- `GET /api/families/me` — current family context for the caller
- Caller id: temporary `X-User-Id` header on the controller (auth comes later under UC19)
- D2: `UNIQUE(family_members.user_id)` — second create → 409

## Still planned
- Add existing user / invitations (UC9–UC10)
- Switch active family profile (UC11)
- Family allergy summary grid (UC6)
- Manage members / activate profiles (UC12)

## Related packages
- Uses `dietaryprofile` for the actual dietary data of each member

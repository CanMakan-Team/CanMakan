# features/family

Family membership context and active profile switching on mobile.

## Responsibilities
- Resolve the caller's family via `GET /api/families/me` (temporary `X-User-Id` from `CurrentUserSession`)
- Create a family circle via `POST /api/families` when the user has a session and no membership
- Load household profiles for switcher (`GET /api/families/{familyId}/profiles`)
- Switch active dietary profile for scan / history

## Create family circle (UC8)

When `/families/me` returns 404 and a user session exists, the drawer shows a short message plus **Create family circle**. That opens `CreateFamilyCircleScreen`, which calls `POST /api/families` and refreshes membership on success.

Create is hidden when the user already has a family. Without a stored session, the drawer asks the user to register (or use the web Family Portal).

## Manage members (UC9 / UC12)

`CreateNewProfileScreen` / `AddProfileToFamilyScreen` are **member profile** flows (not create-circle).
Drawer entry points stay hidden (`showManageFamilyActions = false`) until those APIs exist and the user already has a family.

## Notes
The actual dietary data of each member lives in `dietaryprofile`.
This feature mainly manages relationships and the currently active profile.

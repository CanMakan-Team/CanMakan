# features/family

Family accounts and member management (Family Portal).

## Purpose
Manages family membership, profiles, and family-level views.

## Contains
- Family dashboard
- Member list and profile forms
- Active profile switching
- Restriction summary
- Family scan history
- Related modals and selectors

## UC8 progress — Create Family Circle

**Status:** Done on web for create empty-state (S4); depends on live backend create/`/me`.

| Piece | Notes |
| --- | --- |
| `FamilyMeGate` | Loads `GET /api/families/me`; **404** → create-circle UI |
| `CreateFamilyCirclePage` | Name field + loading / validation / error; `POST /api/families` |
| `familyService.getMyFamily` / `createFamily` | **Always live** (session `X-User-Id`) |
| `apiClient` | Sends temporary `X-User-Id` from session `userId` |
| UC18 register | `/family-register` → live login → this gate |

**Remaining elsewhere:** JWT (UC19); members/invites/history still mock when `VITE_USE_MOCK_API=true`.

## Notes
- Aligns with backend `family` package
- Dietary details may also touch `dietaryprofile`
- Uses shared layout (`PortalLayout`) for the portal shell
Contract: `docs/api/families.md`

# features/family

Family accounts and member management (Family Portal).

## Purpose
Manages family membership, profiles, and family-level views.

## Layout

```
family/
  FamilyMeGate.tsx      # Portal entry: /me → outlet or create-circle
  README.md
  api/
    familyService.ts    # Live + mock family API calls
  lib/
    profileOptions.ts   # Form/option helpers
  pages/
    CreateFamilyCirclePage.tsx
    FamilyAccountPage.tsx
    FamilyDashboardPage.tsx
    FamilyMembersPage.tsx
    FamilyRestrictionSummaryPage.tsx
    FamilyScanHistoryPage.tsx
    FamilyTestPage.tsx
  components/
    ActiveProfileSelector.tsx
    CreateFamilyProfileModal.tsx
    EditFamilyProfileModal.tsx
    LinkExistingUserModal.tsx
    ProfileForm.tsx
```

## UC8 progress — Create Family Circle

**Status:** Done on web for create empty-state (S4); live backend create/`/me`.

| Piece | Notes |
| --- | --- |
| `FamilyMeGate` | Loads `GET /api/families/me`; **404** → create-circle UI |
| `pages/CreateFamilyCirclePage` | Name field + loading / validation / error; `POST /api/families` |
| `api/familyService.getMyFamily` / `createFamily` | **Always live** (Bearer JWT); not mocked |
| `apiClient` | Sends `Authorization: Bearer` from session `accessToken` |
| UC18 register | `/family-register` → live login → this gate |

**Create-circle tip:** seeded users 4–13 already have families; register a new account to see empty-state create.

**Remaining elsewhere:** JWT (UC19); members/invites/history still mock when `VITE_USE_MOCK_API=true`.

## Notes
- Aligns with backend `family` package
- Dietary details may also touch `dietaryprofile`
- Uses shared layout (`PortalLayout`) for the portal shell
- Shared API types live in `shared/api/types.ts`

Contract: `docs/api/families.md`

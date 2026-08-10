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

## UC9 invite + dependant

**Status:** Live when mock is off.

| Piece | Notes |
| --- | --- |
| `LinkExistingUserModal` | Search + create PENDING invite; copy link/code; optional mailto |
| `CreateFamilyProfileModal` | `POST /api/families/me/profiles` dependant create |
| `InviteLandingPage` | `/invite/:token` → register/login + claim |
| Silent `members/link` | Removed from live `familyApiService` |

| Piece | Notes |
| --- | --- |
| `FamilyMeGate` | Loads `GET /api/families/me`; **404** → create-circle UI |
| `pages/CreateFamilyCirclePage` | Name field + loading / validation / error; `POST /api/families` |
| `api/familyService.getMyFamily` / `createFamily` | **Always live** (Bearer JWT); not mocked |
| `apiClient` | Sends `Authorization: Bearer` from session `accessToken` |
| UC18 register | `/family-register` → live login → this gate |

**Create-circle tip:** seeded users 4–13 already have families; register a new account to see empty-state create.

## UC12 manage family circle

**Status:** Live when mock is off (`VITE_USE_MOCK_API=false`).

| Piece | Notes |
| --- | --- |
| `FamilyMembersPage` | Roster with role + inactive badge; manage actions for PRIMARY_ADMIN only |
| `EditFamilyProfileModal` | Live `PUT /me/profiles/{id}`; D3 restricts restriction edits to self + dependants |
| `familyApiService` | `updateProfile`, `setProfileActive`, `removeMember`, `removeDependantProfile`, `getProfiles`, `getScanHistory` |
| Soft-remove | Linked → `DELETE /me/members/{userId}`; dependant → `DELETE /me/profiles/{id}` |
| Activate | `PATCH /me/profiles/{id}` `{active}` — never toggles `users.is_active` |

Contract: `docs/api/families.md`

## Notes
- Aligns with backend `family` package
- Dietary details may also touch `dietaryprofile`
- Uses shared layout (`PortalLayout`) for the portal shell
- Shared API types live in `shared/api/types.ts`

Contract: `docs/api/families.md`

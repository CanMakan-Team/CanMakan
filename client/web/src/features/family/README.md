# features/family

Optional Family Circle management for **PRIMARY_ADMIN**. Personal account
screens live in `features/account`.

## Purpose
Family membership, household profiles, and family-level views. Members and
users without a circle use `/me` instead of these pages.

## Layout

```
family/
  FamilyMeGate.tsx      # PRIMARY_ADMIN only; others go to /me
  FamilyMeContext.tsx   # Provider for GET /families/me (USER portal nav)
  useFamilyMe.ts        # Hook for membership in layout and personal home
  README.md
  api/
    familyApiService.ts
  lib/
    familyRoles.ts
    userPortalNav.ts
    profileOptions.ts
  pages/
    CreateFamilyCirclePage.tsx
    FamilyCirclePage.tsx
    FamilyDashboardPage.tsx
    FamilyMembersPage.tsx
    FamilyRestrictionSummaryPage.tsx
    FamilyScanHistoryPage.tsx
    UserLandingPage.tsx
  components/
    ActiveProfileSelector.tsx
    CreateFamilyProfileModal.tsx
    EditFamilyProfileModal.tsx
    LinkExistingUserModal.tsx
    ProfileCardMenu.tsx
    ProfileForm.tsx
```

## UC9 invite + dependant

**Status:** Live when mock is off.

| Piece | Notes |
| --- | --- |
| `LinkExistingUserModal` | Search + create PENDING invite; copy link/code; optional mailto |
| `CreateFamilyProfileModal` | `POST /api/families/me/profiles` dependant create |
| `InviteLandingPage` | `/invite/:token` → Android opens the app; desktop stays on web `/register`/`/login` + claim. `?web=1` skips the app. |
| Silent `members/link` | Removed from live `familyApiService` |

| Piece | Notes |
| --- | --- |
| `UserLandingPage` | `/family` → `/me` for every USER, including PRIMARY_ADMIN |
| `FamilyMeGate` | Protects family-admin routes; MEMBER and no-circle users go to `/me` |
| `pages/FamilyCirclePage` | Explicit create if 404; existing admin → dashboard; member → `/me` |
| `pages/CreateFamilyCirclePage` | Family name; `POST /api/families` |
| `api/familyApiService.getMyFamily` / `createFamily` | **Always live** (Bearer JWT); not mocked |
| UC18 register | `/register` → live login → `/me/setup-profile` → `/me` |

Creating a Family Circle is an explicit action at `/family/circle`. Registration,
SELF-profile save/skip, and session restoration never open the family form.

## UC12 manage family circle

**Status:** Live when mock is off (`VITE_USE_MOCK_API=false`).

| Piece | Notes |
| --- | --- |
| `FamilyMembersPage` | PRIMARY_ADMIN roster: invite, edit, activate, remove |
| `EditFamilyProfileModal` | Live `PUT /me/profiles/{id}`; D3 restricts restriction edits to self + dependants |
| `familyApiService` | `updateProfile`, `setProfileActive`, `removeMember`, `removeDependantProfile`, `getProfiles`, `getScanHistory` (PRIMARY_ADMIN) |
| Soft-remove | Linked → `DELETE /me/members/{userId}`; dependant → `DELETE /me/profiles/{id}` |
| Activate | `PATCH /me/profiles/{id}` `{active}` — never toggles `users.is_active` |

Contract: `docs/api/families.md`

## Notes
- `FamilyAccountPage` / `features/account` `AccountPage` loads `/api/auth/me` even
  without a family; family and SELF profile rows are omitted when `/families/me`
  is 404. **Delete My Account** calls `DELETE /api/auth/account` for the signed-in
  user only, then signs out.
- Aligns with backend `family` package
- Dietary details may also touch `dietaryprofile`
- Uses shared layout (`PortalLayout`) for the portal shell
- Shared API types live in `shared/api/types.ts`

Contract: `docs/api/families.md`

# features/family

Personal USER onboarding plus optional Family Circle management.

## Purpose
Keeps a USER's optional SELF profile independent from Family Circle membership,
while preserving family membership, profiles, and family-level views.

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
    FamilyCirclePage.tsx
    FamilyAccountPage.tsx
    FamilyDashboardPage.tsx
    FamilyMembersPage.tsx
    FamilyRestrictionSummaryPage.tsx
    FamilyScanHistoryPage.tsx
    PersonalHomePage.tsx
    SelfProfileSetupPage.tsx
    UserLandingPage.tsx
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
| `UserLandingPage` | Resolves optional membership; member → dashboard, **404** → personal home |
| `FamilyMeGate` | Protects family-only routes; **404** offers personal/explicit-family links, never an inline create form |
| `pages/FamilyCirclePage` | Explicit optional family entry; a **404** opens `CreateFamilyCirclePage` |
| `pages/CreateFamilyCirclePage` | Family name + loading / validation / error; `POST /api/families` |
| `api/familyService.getMyFamily` / `createFamily` | **Always live** (Bearer JWT); not mocked |
| `apiClient` | Sends the memory-only access credential and includes the refresh cookie |
| UC18 register | `/family-register` → live login → `/family/setup-profile` → personal home |

Creating a Family Circle is an explicit action at `/family/circle`. Registration,
SELF-profile save/skip, and session restoration never open the family form.

## UC12 manage family circle

**Status:** Live when mock is off (`VITE_USE_MOCK_API=false`).

| Piece | Notes |
| --- | --- |
| `FamilyMembersPage` | Roster with role + inactive badge; manage actions for PRIMARY_ADMIN only |
| `EditFamilyProfileModal` | Live `PUT /me/profiles/{id}`; D3 restricts restriction edits to self + dependants |
| `familyApiService` | `updateProfile`, `setProfileActive`, `removeMember`, `removeDependantProfile`, `getProfiles`, `getScanHistory` (PRIMARY_ADMIN) |
| Soft-remove | Linked → `DELETE /me/members/{userId}`; dependant → `DELETE /me/profiles/{id}` |
| Activate | `PATCH /me/profiles/{id}` `{active}` — never toggles `users.is_active` |

Contract: `docs/api/families.md`

## Notes
- `FamilyAccountPage` loads the authoritative `/api/auth/me`, family context,
  and SELF profile; it does not expose token or request-header details.
- Aligns with backend `family` package
- Dietary details may also touch `dietaryprofile`
- Uses shared layout (`PortalLayout`) for the portal shell
- Shared API types live in `shared/api/types.ts`

Contract: `docs/api/families.md`

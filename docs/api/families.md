# Family circle APIs (UC8 / UC9 / UC6)

## Progress

| Area | Status |
| --- | --- |
| Schema D2 `UNIQUE(family_members.user_id)` | Done |
| `POST /api/families` (PRIMARY_ADMIN + SELF profile) | Done |
| `GET /api/families/me` | Done |
| Request validation (`@Valid` family name) | Done |
| Web create empty-state (`FamilyMeGate`) | Done |
| JWT principal on family routes | Done (UC19) |
| Mobile resolve via `/me` (AC10) | Done (create-when-empty); active-profile persist → UC11 |
| UC9 user-search / invitations / dependant profiles | Done |
| Register auto-claim of PENDING invite | Done (optional `invitationToken`) |
| Spring Data repos (Family / Member / Invitation) | Done |
| `GET /api/families/me/members` roster list | Done (UC12 list; manage CRUD later) |

---

## Identity

Family create/`/me`/invite/dependant/restriction-summary require a Bearer access JWT. Controllers
read the caller from `@AuthenticationPrincipal AuthUserDetails` and pass
`userId` into `FamilyService`. Mutations that change household membership or
dependants require **PRIMARY_ADMIN** membership (not a JWT role claim).

```http
Authorization: Bearer <access-token>
```

---

## Create family circle

`POST /api/families`

Headers: `Authorization`, `Content-Type: application/json`

Request:

```json
{
  "familyName": "Wong Family"
}
```

Success `201` returns the same shape as `GET /families/me`.

| Status | Meaning |
| --- | --- |
| 400 | Blank / invalid family name |
| 401 | Missing/invalid JWT or unknown account |
| 409 | Caller already belongs to a family (D2) |

---

## Current family context

`GET /api/families/me`

Headers: `Authorization`

Success `200` body includes `familyId`, `familyName`, `memberRole`, `selfProfileId`, `createdByUserId`.

| Status | Meaning |
| --- | --- |
| 401 | Missing/invalid JWT |
| 404 | Authenticated user is not a family member |

---

## Family members roster (UC12 list)

`GET /api/families/me/members`

Headers: `Authorization`

Any family member may list. Returns linked users and dependant profiles:

```json
[
  {
    "memberId": 10,
    "profileName": "Admin",
    "relationship": "SELF",
    "ageGroup": "UNSPECIFIED",
    "commonRequirements": ["HALAL"],
    "restrictions": ["PEANUT_ALLERGY"],
    "source": "REGISTERED_USER",
    "maskedEmail": "a***n@example.com"
  },
  {
    "memberId": 2,
    "profileName": "Toddler",
    "relationship": "CHILD",
    "ageGroup": "UNSPECIFIED",
    "commonRequirements": [],
    "restrictions": [],
    "source": "DEPENDANT_PROFILE",
    "maskedEmail": null
  }
]
```

| Field | Notes |
| --- | --- |
| `memberId` | `userId` for `REGISTERED_USER`; dietary `profileId` for `DEPENDANT_PROFILE` |
| `ageGroup` | Always `UNSPECIFIED` until UC12 persists age on profiles |
| `commonRequirements` | Restriction codes whose catalog category is `RELIGIOUS` |
| `restrictions` | All other catalog categories (allergens, diets, etc.) |
| `maskedEmail` | Present for linked users only |

| Status | Meaning |
| --- | --- |
| 401 | Missing/invalid JWT |
| 404 | Authenticated user is not a family member |

---

## Profiles by family id

`GET /api/families/{familyId}/profiles` — authenticated; returns all dietary
profiles for the family, including dependants (`linked_user_id` null).

---

## User search (UC9)

`GET /api/families/me/user-search?email=`

PRIMARY_ADMIN only. Always `200` for a syntactically valid email:

| `accountStatus` | Meaning |
| --- | --- |
| `ACTIVE` / `INACTIVE` | Registered account |
| `NOT_REGISTERED` | No user row — still a valid invite target |

| `familyLinkStatus` | Meaning |
| --- | --- |
| `NOT_LINKED` | Can invite |
| `ALREADY_LINKED` | User already in a family (D2) |
| `PENDING` | Pending invite already exists for this family + email |

| Status | Meaning |
| --- | --- |
| 400 | Invalid email |
| 403 | Caller is not PRIMARY_ADMIN |
| 404 | Caller has no family |

---

## Create invitation (UC9)

`POST /api/families/me/invitations`

```json
{ "email": "invitee@example.com" }
```

Creates a `PENDING` invitation for a **registered or unknown** email. Does **not**
insert `family_members`. Response includes shareable fields:

```json
{
  "invitationId": 1,
  "invitedEmail": "invitee@example.com",
  "invitationToken": "<opaque>",
  "inviteCode": "ABCD1234",
  "inviteUrl": "http://localhost:5173/invite/<opaque>",
  "status": "PENDING",
  "expiresAt": "2026-08-16T00:00:00Z",
  "inviteeRegistered": false
}
```

`inviteUrl` base comes from `canmakan.invites.public-base-url` (default local Vite).

| Status | Meaning |
| --- | --- |
| 201 | Invitation created |
| 400 | Invalid email |
| 403 | Not PRIMARY_ADMIN |
| 409 | Already a member, or duplicate PENDING for this family+email |

There is **no** production `POST /api/families/me/members/link` silent-link endpoint.

---

## Claim invitation (UC9)

`POST /api/families/me/invitations/claim`

```json
{ "invitationToken": "<opaque>" }
```

For an already-registered user whose email matches the PENDING invite: inserts
`MEMBER`, attaches SELF dietary profile to the family, marks invitation `ACCEPTED`.

Also used after login from `/invite/:token` (web) when the invitee already has an account.

Register auto-claim: `POST /api/auth/register` accepts optional `invitationToken`.
After the user row + SELF profile are created, matching PENDING invite (by token
and/or email) is claimed in the same transaction. Full UC10 inbox/decline remains separate.

| Status | Meaning |
| --- | --- |
| 200 | Joined family (same shape as `/families/me`) |
| 409 | Invalid/expired/wrong-email invite, or already in a family |

---

## Create dependant profile (UC9)

`POST /api/families/me/profiles`

PRIMARY_ADMIN only.

```json
{
  "profileName": "Child",
  "relationship": "CHILD",
  "commonRequirements": ["PEANUT"],
  "restrictions": []
}
```

Persists `dietary_profiles` with `family_id` set, `linked_user_id` NULL,
`is_primary=false`. Does **not** insert `family_members`. Optional restriction
codes are applied via the dietary profile service.

| Status | Meaning |
| --- | --- |
| 201 | `{ profileId, profileName, relationship, familyId }` |
| 400 | Validation / unknown restriction code |
| 403 | Not PRIMARY_ADMIN |

Dependants appear in `GET /families/{id}/profiles` and in
`GET /families/me/restriction-summary` (`userId` is `0`; `profileId` is set).

---

## Restriction summary (UC6)

`GET /api/families/me/restriction-summary`

Unions active `family_members` linked profiles **and** dependant profiles
(`linked_user_id IS NULL`) for the caller's family.

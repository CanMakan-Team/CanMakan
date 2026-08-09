# Family circle APIs (UC8)

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

---

## Identity

Family create/`/me`/restriction-summary require a Bearer access JWT. Controllers
read the caller from `@AuthenticationPrincipal AuthUserDetails` and pass
`userId` into `FamilyService`.

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

## Profiles by family id

`GET /api/families/{familyId}/profiles` — authenticated; prefer `/me`-scoped APIs for new work.

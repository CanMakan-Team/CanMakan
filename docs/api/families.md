# Family circle APIs (UC8)

## Progress

| Area | Status |
| --- | --- |
| Schema D2 `UNIQUE(family_members.user_id)` | Done |
| `POST /api/families` (PRIMARY_ADMIN + SELF profile) | Done |
| `GET /api/families/me` | Done |
| Request validation (`@Valid` family name) | Done |
| Web create empty-state (`FamilyMeGate`) | Done |
| Real auth / HTTP 401 (AC8) | Open — UC19 |
| Mobile resolve via `/me` (AC10) | Open — UC11 |

---

## Identity (temporary)

Until UC19 (Spring Security + JWT), create/`/me` take the caller as a request header:

```http
X-User-Id: <numeric users.id>
```

This is **not** authentication. Controllers pass `userId` straight into the service.
Under UC19, replace the header parameter with `@AuthenticationPrincipal` (or equivalent);
`FamilyService` already takes `long userId` and can stay unchanged.

---

## Create family circle

`POST /api/families`

Headers: `X-User-Id`, `Content-Type: application/json`

Request:

```json
{
  "familyName": "Wong Family"
}
```

Rules:

- Name is trimmed; blank → `400`
- Max length 100 → `400` if exceeded
- Caller must not already have a `family_members` row (D2 UNIQUE) → `409` on second create
- On success, creates in one transaction:
  - `families` row (`created_by_user_id` = caller)
  - `family_members` with `member_role = PRIMARY_ADMIN`
  - SELF `dietary_profiles` row (`linked_user_id` = caller, `family_id` set, `is_primary = true`)
  - `profile_name` defaults to the email local-part (before `@`)

Success: `201 Created`

```json
{
  "familyId": 50,
  "familyName": "Wong Family",
  "memberRole": "PRIMARY_ADMIN",
  "selfProfileId": 77,
  "createdByUserId": 14
}
```

Errors (`{"message":"..."}`):

| Status | When |
| --- | --- |
| 400 | Blank or invalid family name |
| 409 | Caller already belongs to a family |

---

## Current family context

`GET /api/families/me`

Headers: `X-User-Id`

Success: `200 OK` — same body shape as create response.

| Status | When |
| --- | --- |
| 404 | Caller has no family membership (web empty-state / create CTA) |

This is the canonical replacement for hardcoded `familyId=1` clients (mobile wiring is UC11).

---

## Existing (unchanged)

`GET /api/families/{familyId}/profiles` — list dietary profiles for a family id (seeded / mobile path). Still public until broader auth lands.

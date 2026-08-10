# features/family

Family membership context and active profile switching on mobile.

## Client ownership (family lifecycle)

| Action | Mobile | Web | Notes |
| --- | --- | --- | --- |
| Create Family Circle (UC8) | Yes | Yes | Very simple |
| Invite Member — link/code + share (UC9) | Yes | Yes | Mobile preferred for native share |
| Accept / Decline Invitation (UC10) | Yes | Optional | Mainly mobile |
| Switch Profile (UC11) | Yes | — | Daily use |
| Manage Family Circle (UC12) | Optional / limited | Primary | Roster, edit, remove, toggle active |

## Responsibilities (current)
- Resolve the caller's family via `GET /api/families/me` (Bearer JWT via auth interceptor)
- Create a family circle via `POST /api/families` when authenticated and no membership (UC8)
- Load household profiles for switcher (`GET /api/families/{familyId}/profiles`)
- Switch active dietary profile for scan / history (UC11 — server persist still open)

## Create family circle (UC8)

When `/families/me` returns 404 and the user is signed in, the drawer shows a short message plus **Create family circle**. That opens `CreateFamilyCircleScreen`, which calls `POST /api/families` and refreshes membership on success.

Create is hidden when the user already has a family. Without a UC19 session, the drawer asks the user to sign in.

## Invite (UC9)

PRIMARY_ADMIN can open **Invite to Family** from the drawer (`showManageFamilyActions`
when `memberRole == PRIMARY_ADMIN`). `AddProfileToFamilyScreen` searches by email,
creates a PENDING invitation, and opens the system share sheet with `inviteUrl` +
`inviteCode`. Invitees join via register/login claim (optional `invitationToken`);
full UC10 inbox remains later.

Dependant profile create UI stays web-primary; dependants still appear in the
mobile profile switcher and UC6 summary once created.

## Manage members (UC12)

`CreateNewProfileScreen` remains a stub. Full roster admin is **web-primary**;
mobile manage stays limited to invite (UC9).

## Notes
The actual dietary data of each member lives in `dietaryprofile`.
Session identity is owned by `AuthSessionStore` (UC19), not a separate prefs store.

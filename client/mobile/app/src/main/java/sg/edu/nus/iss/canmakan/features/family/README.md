# features/family

Family membership context and active profile switching on mobile.

## Client ownership (family lifecycle)

| Action | Mobile | Web | Notes |
| --- | --- | --- | --- |
| Create Family Circle (UC8) | Yes | Yes | Very simple |
| Invite Member — email invite (UC9) | Yes | Yes | Mobile: Cancel/Invite; email when Resend enabled |
| Accept / Decline Invitation (UC10) | Yes | Optional | Mainly mobile |
| Switch Profile (UC11) | Yes | — | Daily use |
| Manage Family Circle (UC12) | Optional / limited | Primary | Roster, edit, remove, toggle active |

## Responsibilities (current)
- Resolve the caller's family via `GET /api/families/me` (Bearer JWT via auth interceptor)
- Create a family circle via `POST /api/families` when authenticated and no membership (UC8)
- Load household profiles for switcher (`GET /api/families/{familyId}/profiles`; inactive omitted)
- Switch active dietary profile for scan / history (UC11 — `GET`/`PUT /api/families/me/active-profile`)

## Create family circle (UC8)

When `/families/me` returns 404 and the user is signed in, the drawer shows a short message plus **Create family circle**. That opens `CreateFamilyCircleScreen`, which calls `POST /api/families` and refreshes membership on success.

Create is hidden when the user already has a family. Without a UC19 session, the drawer asks the user to sign in.

## Manage family hub (limited UC12) + invite (UC9) / dependant create

PRIMARY_ADMIN sees a single drawer item **Manage family** (`showManageFamilyActions`
when `memberRole == PRIMARY_ADMIN`). That opens `ManageFamilyScreen`, which branches to:

| Choice | Screen | Outcome |
| --- | --- | --- |
| Invite someone to join | `InviteFamilyMemberScreen` | Email → Invite → PENDING + email (when Resend enabled) |
| Add dependant profile | `CreateDependantProfileScreen` | Dependant dietary profile with no login |

Invite flow on mobile is one step: enter email, then **Cancel** / **Invite**.
`POST /api/families/me/invitations` rejects users already in a family with **409**.
That POST does not retry on timeout (one attempt, ~15s) so a down host does not
block the screen for three tries. A `PENDING` invite is stored only after Resend accepts the email; a failed send
can be retried. Repeating Invite for the same email after a successful send returns **409**.
Success toasts and returns to Manage Family.

Invitees preserve the token through register/login; claim runs only after
authentication (post-login continuation or **Notifications** inbox).

```mermaid
flowchart TD
  Hub[Manage family hub] --> Invite[InviteFamilyMemberScreen]
  Hub --> Dependant[CreateDependantProfileScreen]
  Invite --> Post[POST invitations]
  Post -->|409 already in family or already emailed| Err[Red error on screen]
  Post -->|201 emailSent false| Stay[Stay on invite, red error]
  Post -->|201 emailSent true| Email[Toast and back to hub]
  Email --> PathA[New user: register, then login with token preserved]
  Email --> PathB[Existing user: open link then login]
  Email --> PathC[Already logged in: Notifications inbox or deep-link claim]
  PathA --> Join[MEMBER + SELF profile + ACCEPTED]
  PathB --> Join
  PathC --> Join
```

| Path | Mobile entry |
| --- | --- |
| Register then claim | Invite landing → Register (token offered) → Login → `PostLoginContinuationViewModel` claim |
| Deep link / login claim | `canmakan://invite/{token}` or HTTPS hosts from `WEB_INVITE_BASE_URLS` → `PendingInvitationStore` → Login offer → post-login claim |
| Inbox accept / decline | Top-bar **Notifications** bell → `features/notifications` (`NotificationsInboxScreen`) |

The inbox is account-wide (not admin-only): family invitations today, with room for
profile-update notices later. It lives under `features/notifications` so any shell screen
can open it; it is not listed under Family in the drawer.

Full API contract and HTTP guards: `docs/api/families.md` (Invite → join workflow).

Dependant profiles appear in the mobile profile switcher and UC6 summary once created.
Dietary Summary empty state can open the same Manage family hub.

## Switch profile (UC11)

On login/startup, `CanMakanNavGraphViewModel` loads `GET /api/families/me/active-profile`
after `/me` and family profiles. Drawer profile selection updates
`ActiveProfileManager` immediately (optimistic), then confirms with
`PUT /api/families/me/active-profile`. Failed PUT (403 outside family, 409 inactive,
or network error) rolls back to the previous profile and shows an inline error.
`ActiveProfileManager` uses `UNSET_PROFILE_ID = 0` until the server resolves a
profile or authenticated optional setup creates one.

## Manage members (UC12)

Full roster admin (edit/remove/toggle active) is **web-primary**. Mobile manage stays
limited to the hub above: invite (UC9) and dependant create.

## Notes
The actual dietary data of each member lives in `dietaryprofile`.
Session identity is owned by `AuthSessionStore` (UC19), not a separate prefs store.

# UC8–UC12 Manual UI QA Sheet

**Owner:** Amelia · **Scope:** Family lifecycle Core MVP  
**Clients:** Web Family Portal (primary for UC8/UC9/UC12) · Mobile (primary for UC10/UC11; UC9 share)  
**Related:** [sprint2-mvp-epics.md](sprint2-mvp-epics.md) (AC checklists) · [docs/api/families.md](../api/families.md)

Use this sheet for **manual UI verification**. Tick each item when verified. Re-run after family/auth changes.

| Field | Value |
| --- | --- |
| Tester | |
| Date | |
| Branch / commit | |
| Web URL | |
| Backend URL | |
| Mobile build | |

---

## Docs / contracts to update (when behaviour changes)

Tick after a family UC change lands — keep these aligned with code.

- [ ] [docs/api/families.md](../api/families.md) — request/response + status codes
- [ ] [sprint2-mvp-epics.md](sprint2-mvp-epics.md) — UC8–UC12 AC Done flags / current code state
- [ ] [sprint2-jira-backlog.md](sprint2-jira-backlog.md) — child-story status if Jira not yet SoT
- [ ] [client/web/src/features/family/README.md](../../client/web/src/features/family/README.md) — live vs mock notes
- [ ] Mobile feature READMEs under `client/mobile/.../family*` / invite / profile switch (if present)
- [ ] This sheet — add/remove steps if UI or AC scope changed
- [ ] [docs/sprint/README.md](README.md) — index link still valid

---

## Preconditions

- [ ] Backend running on `:8080` (health OK; `JWT_SIGNING_SECRET` loaded)
- [ ] Web: `VITE_USE_MOCK_API=false`; Vite on `:5173`
- [ ] Mobile (UC10/UC11/UC9 share): debug build → backend (`local.properties` `BASE_URL`)
- [ ] Prefer **new register** for UC8 (seeded users 4–13 already have families)
- [ ] Two actors ready: **Admin A** (PRIMARY_ADMIN) and **Invitee B**
- [ ] Aware that `spring.sql.init.mode=always` reseeds on restart (new users disappear)

---

## UC8 — Create Family Circle

**Primary UI:** Web `/family-register` → `/family` · Mobile drawer create when `/me` is 404  
**APIs:** `POST /api/families`, `GET /api/families/me`

- [ ] **8.1** Register a **new** user, finish/skip optional profile setup, then explicitly select **Family Circle** → create-circle state *(AC 9)*
- [ ] **8.2** Submit blank family name → validation error; no create *(AC 7, 11)*
- [ ] **8.3** Submit a valid family name → success; family dashboard/context *(AC 1–5, 11)*
- [ ] **8.4** Refresh / reopen → still same family (`GET /me` succeeds) *(AC 5, 10)*
- [ ] **8.5** Create again while already in a family → blocked / 409-style *(AC 6)*
- [ ] **8.6** Family routes while logged out → redirect / login; create blocked *(AC 8)*
- [ ] **8.7** (Mobile) Session + no family → drawer CTA → create succeeds *(AC 9, 11)*

**UC8 result:** Pass / Fail / Partial — notes:

---

## UC9 — Invite Member / Create Dependant

**Primary UI:** Web **Family Members** · Mobile invite share + dependant (optional)  
**APIs:** user-search, invitations, profiles, members list

### As PRIMARY_ADMIN (Admin A)

- [ ] **9.1** Open Family Members → roster loads (self at minimum) *(supports UC12 AC 2)*
- [ ] **9.2** **Add Existing App User** → search registered email → match (or NOT_REGISTERED) *(AC 1, 7)*
- [ ] **9.3** Create PENDING invitation → UI shows **copy link/code** (optional mailto) *(AC 2–4, 14–16)*
- [ ] **9.4** Invite unknown but valid email → allowed (not hard 404) *(AC 7)*
- [ ] **9.5** Invite email already in this family → conflict / clear error (409) *(AC 5)*
- [ ] **9.6** **Create New Profile** (dependant): name + relationship + restrictions → on roster; no login *(AC 9–14)*
- [ ] **9.7** Restriction summary / profiles list → dependant visible by `profileId` *(AC 12)*

### As MEMBER (non-admin)

- [ ] **9.8** Open Family Members → no invite / create CTAs (or 403 if forced) *(AC 6)*

### Invite landing / claim

- [ ] **9.9** Open `/invite/{token}` logged out → register or login, then claim *(AC 4, 15–16)*
- [ ] **9.10** (Mobile) Share invite + open deep link → invite flow; claim after auth *(AC 16)*
- [ ] **9.11** Wrong account / expired / already-used token → clear error (403 / 410 / 409) *(UC10 AC 6–8)*

**UC9 result:** Pass / Fail / Partial — notes:

---

## UC10 — Accept / Decline Invitation

**Primary UI:** Mobile **Notifications** (top-bar bell) · Web claim path optional  
**APIs:** `GET /api/invitations/me`, accept/decline (or UC9 claim)

- [ ] **10.1** As Invitee B, open pending invitations → Admin A’s invite listed (or claim via link) *(AC 1–2, 12)*
- [ ] **10.2** **Accept** valid pending invite → B joins as MEMBER; on Admin A roster *(AC 3–4, 10)*
- [ ] **10.3** Accept same invite again → rejected / already final *(AC 7)*
- [ ] **10.4** New invite → **Decline** → declined; B not in family *(AC 5)*
- [ ] **10.5** User already in another family tries accept → 409 *(AC 9)*
- [ ] **10.6** Expired invitation accept → 410 *(AC 6)*
- [ ] **10.7** Token email ≠ authenticated user → 403 *(AC 8)*
- [ ] **10.8** Empty / loading / error states on invitations UI → no crash *(AC 12)*

**UC10 result:** Pass / Fail / Partial — notes:

---

## UC11 — Switch Active Profile

**Primary UI:** Mobile drawer / profile switcher (required)  
**Web:** Removed from Family Members; mobile remains MVP SoT  
**APIs:** `GET/PUT /api/families/me/active-profile`

- [ ] **11.1** After login, open switcher → eligible in-family profiles; no hard-coded profile `1` *(AC 1, 8)*
- [ ] **11.2** Current active profile matches GET active-profile *(AC 2)*
- [ ] **11.3** Switch to another **active** profile → PUT OK; UI updates *(AC 3, 9)*
- [ ] **11.4** Force-stop / restart app → same active profile restored *(AC 4)*
- [ ] **11.5** Scan / history / restrictions use selected profile context *(AC 5)*
- [ ] **11.6** After UC12 deactivate → inactive profile omitted / not selectable *(AC 7)*
- [ ] **11.7** (Optional) Profile outside family → 403 *(AC 6)*
- [ ] **11.8** Web has no active-profile switcher; mobile remains MVP SoT *(AC 10)*

**UC11 result:** Pass / Fail / Partial — notes:

---

## UC12 — Manage Family Circle

**Primary UI:** Web **Family Members**  
**APIs:** members/profiles GET; PUT profile; PATCH `{active}`; DELETE member/profile

### As PRIMARY_ADMIN

- [ ] **12.1** View roster → name, relationship, role, Active/Inactive (linked + dependants) *(AC 2–6)*
- [ ] **12.2** Edit **self** or **dependant** (name / relationship / restrictions) → persists on reload *(AC 7–9, 19)*
- [ ] **12.3** Edit **another adult’s linked profile** → restrictions read-only (D3) + hint *(AC 8)*
- [ ] **12.4** **Deactivate** a non-self scan profile → confirm; inactive badge; not in UC11 *(AC 15–17, 19)*
- [ ] **12.5** User can still **log in** after profile deactivate (`users.is_active` unchanged) *(AC 1, 15)*
- [ ] **12.6** **Reactivate** → selectable again *(AC 18)*
- [ ] **12.7** **Remove** linked MEMBER (confirm) → gone from roster; soft-remove / history kept *(AC 10–11, 14, 19)*
- [ ] **12.8** **Remove** dependant profile → gone from roster *(AC 10, 14)*
- [ ] **12.9** Remove **last PRIMARY_ADMIN** → blocked (409); family still usable *(AC 13)*

### As MEMBER

- [ ] **12.10** View Family Members → read-only; no manage CTAs *(AC 12)*
- [ ] **12.11** Force manage API if possible → 403 *(AC 12)*

### Mock-off

- [ ] **12.12** All above with `VITE_USE_MOCK_API=false` → live Spring (not mock) *(AC 20)*

**UC12 result:** Pass / Fail / Partial — notes:

---

## End-to-end smoke (happy path)

- [ ] **E2E.1** Register A → Create circle (UC8)
- [ ] **E2E.2** Invite B → B accepts / claims (UC9–UC10)
- [ ] **E2E.3** Create dependant (UC9)
- [ ] **E2E.4** Switch active profile on mobile (UC11)
- [ ] **E2E.5** Edit / deactivate / reactivate on web (UC12)
- [ ] **E2E.6** Remove a test member / dependant (UC12)

---

## Defects / notes

| ID | UC / step | Severity | Notes | Fixed? |
| --- | --- | --- | --- | --- |
| | | | | [ ] |

---

## Sign-off

| Role | Name | Date | Outcome |
| --- | --- | --- | --- |
| Dev | | | |
| QA | | | |

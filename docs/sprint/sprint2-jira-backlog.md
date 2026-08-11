# CanMakan — Sprint 2 Jira-Ready Backlog

| Field | Value |
| --- | --- |
| **Status** | Planning baseline — Epic Owner review required before Ready for Development |
| **Prioritisation** | §0 Core MVP / Enhanced / Nice-to-Have |
| **Task assignment** | §0c |
| **Architecture packages** | §0b |
| **Related narrative** | [`sprint2-mvp-epics.md`](sprint2-mvp-epics.md) |
| **Story points** | Size only in Jira |
| **Tech stack** | Android Kotlin + ML Kit; React; Spring Boot + Security + JWT; AWS RDS MySQL; Open Food Facts; OpenAI; AWS EC2; Resend |

After epics/stories exist in Jira, **Jira is the source of truth** for ownership, sizing, acceptance refinements, and progress.

Detailed acceptance-criteria checklists (one row per criterion) live in [`sprint2-mvp-epics.md`](sprint2-mvp-epics.md). Stories also inherit §8 Shared Definition of Done.

### Governance

- Epic Owners are assigned in §0c (also set as Epic Owner in Jira).
- UC IDs and packages follow §0.
- Shared contracts must not be changed unilaterally (see each epic).
- Cross-cutting work (DevSecOps/CI/CD, Database) follows §0c owner/support roles.
- No epic is Ready for Development until its Owner review checklist is complete.

### Owner review checklist (every epic)

- [ ] Repository baseline verified against the latest branch
- [ ] Database assumptions verified
- [ ] API contract reviewed with affected owners
- [ ] Authorization rules approved
- [ ] Dependencies linked in Jira
- [ ] Open decisions resolved or explicitly blocked
- [ ] Stories appropriately sized
- [ ] Acceptance criteria reviewed by QA
- [ ] Sizing completed in Jira by implementers (not in this document)
- [ ] Epic marked Ready for Development by its owner

---

## 0. Prioritisation strategy — Features and Technology Stack

### Core MVP

| UC ID | Feature | Owner | Notes |
| --- | --- | --- | --- |
| **UC1** | Manage App User Dietary Profile | Kwok Heng | Change personal restrictions, allergens, preferences; create new dietary profile after registration |
| **UC2** | Scan Product Barcode | Khai | Capture barcode → fetch product → call safety verdict agent |
| **UC3** | View Safety Verdicts | Huayuan | Process and display detailed Safe / Warning / Unsafe verdict for a scanned product |
| **UC4** | View Scan History | Kwok Heng | List past scans and verdicts; mobile personal history **and** filterable family-admin history |
| **UC5** | View Alternative Product Recommendation | Chai Lee | Suggest suitable alternatives for Warning/Avoid products based on the active dietary profile |
| **UC6** | View Family Allergy Summary Grid | Khai | Grid/overview of family members and their allergies/restrictions |
| **UC7** | Generate Consumer Trends | Maowei | Aggregate anonymised scan data into consumer trends |
| **UC8** | Create Family Circle | Amelia | Create family circle; creator becomes PRIMARY_ADMIN |
| **UC9** | Invite Family Member to Circle | Amelia | Invite an existing registered user, **or** create an admin-managed dependant dietary profile |
| **UC10** | Accept Family Invitation | Amelia | Invited user accepts or declines an invitation to join a family circle |
| **UC11** | Switch Family Profile | Amelia | On mobile, select which eligible family profile subsequent scans are evaluated against |
| **UC12** | Manage Family Circle | Amelia | View family roster; update member profile; remove member; activate/deactivate member profile |
| **UC13** | Manage User Account Status | Maowei | List existing accounts and suspend/reactivate them; system roles remain read-only |

### Enhanced

| UC ID | Feature | Owner | Notes |
| --- | --- | --- | --- |
| **UC14** | View Scan Verdict Trend | Huayuan | Charts of family Safe / Warning / Unsafe verdict history over time |
| **UC15** | View Application Usage Statistics | *Unassigned* | Overall engagement and usage metrics |
| **UC16** | View System Health Logs | *Unassigned* | Application and infrastructure health events/logs |
| **UC17** | View Recommendation History | Chai Lee | List past product recommendations |
| **UC18** | User Registration | Maowei | Register a new user account |
| **UC19** | User Login / Logout | Maowei | Authenticate via mobile/web; terminate session/token and clear local credentials. **Enhanced package, but Core critical path — schedule before production Core APIs.** |

### Nice-to-Have

| UC ID | Feature | Owner | Notes |
| --- | --- | --- | --- |
| **UC20** | Report Incorrect Product Information | *Unassigned* | Flag incorrect product or ingredient data for review |
| **UC21** | View AI Reasoning Performance and Accuracy Logs | *Unassigned* | View AI analysis/execution logs, accuracy indicators, latency, and model/token usage |
| **UC22** | Export Consumer Trends | *Unassigned* | Export aggregated anonymised trend data, e.g. CSV |
| **UC23** | Manage Subscription/Premium Plans | *Unassigned* | Configure subscription tiers and feature availability |
| **UC24** | Scan Ingredient List with OCR | *Unassigned* | Capture ingredient text via OCR → review/extract → analyse using the verdict pipeline |

---

## 0b. Architecture-centric feature packages

| Feature package | Features / UCs | Implementation technologies | Architecture group |
| --- | --- | --- | --- |
| **Authentication & Account** | UC18 Registration; UC19 Login / Logout (**UC19 = Core critical path**) | Mobile + Web + Backend (Android Kotlin; React; Spring Boot Auth API; Spring Security; JWT; AWS RDS MySQL) | Authentication & Security |
| **Family Management** | UC8 Create Circle; UC9 Invite / Dependant; UC10 Accept; UC11 Switch Profile; UC12 Manage Circle; UC6 Allergy Summary | See rows below | Mixed |
| → Create family circle | UC8 | Mobile + Web + Backend (simple empty-state create) | Shared (Mobile + Web Family) |
| → Invite member (link/code + share) | UC9 | Mobile + Web + Backend; **mobile preferred for native share** | Shared (Mobile + Web Family) |
| → Accept / decline invitation | UC10 | Mobile + Backend (+ optional web); Resend email | **Mobile Client** (primary); Web optional |
| → Switch profile | UC11 | Mobile + Backend (daily use) | **Mobile Client** |
| → Manage family circle | UC12 | Web primary (roster, edit, remove, toggle active); mobile optional/limited | **Web Client (Family)** (primary) |
| → Allergy summary | UC6 | Mobile + Backend *(optional React web parity)* (Android; React; Spring Boot; RDS) | **Mobile Client** (primary); Web Family optional |
| **Dietary Profile** | UC1 | Mobile + Backend (Android; Spring Boot; RDS) | Mobile Client |
| **Scanning & Verdicts** | UC2 Barcode; UC24 OCR; UC3 Verdicts; UC5 Alternatives; UC17 Rec. history; UC4 Scan history; UC20 Report | See stack per UC | Mobile / Shared |
| → Barcode scan | UC2 | Android; ML Kit Barcode; Spring Boot; Open Food Facts | Mobile Client |
| → OCR scan | UC24 | Android; ML Kit Text Recognition; Spring Boot | Mobile Client |
| → Verdicts | UC3 | Android; Spring Boot; Dietary Rule Engine; AI-assisted analysis; RDS | Mobile Client |
| → Alternatives | UC5 | Android; Spring Boot; OFF; recommendation logic | Mobile Client |
| → Recommendation history | UC17 | Android; Spring Boot; RDS | Mobile Client |
| → Scan history | UC4 | Android + React + Spring Boot + RDS | Shared Client |
| → Report incorrect info | UC20 | Android + Admin React + Reporting API + RDS | Shared Client |
| **Analytics & Insights** | UC14 Verdict trend; UC15 Usage; UC7 Consumer trends; UC22 Export | React charts/dashboards; Spring Boot analytics/aggregation; RDS | Web Family / Admin |
| → Verdict trend | UC14 | React chart lib; Analytics API; RDS | Web Client (Family) |
| → Usage / consumer trends / export | UC15, UC7, UC22 | React Admin; aggregation + anonymisation; CSV export; RDS | Web Client (Admin) |
| **Administration** | UC13 Accounts; UC23 Subscriptions | React Admin; Spring Boot; Spring Security; RBAC; RDS | Web Client (Admin) |
| **Monitoring** | UC16 Health logs; UC21 AI performance logs | React Admin; Actuator / app logging; AI trace storage; EC2 monitoring; RDS | Web Client (Admin) |

### Client ownership (derived)

| Client | Primary UCs |
| --- | --- |
| **Mobile** | UC1–UC3, UC5, UC6 (primary), UC8 (create), UC9 (invite + share), UC10 (accept), UC11 (switch), UC17, UC24; shares UC4, UC18–UC20; UC12 optional/limited |
| **Web Family** | UC8 (create), UC9 (invite), UC12 (manage primary), UC14; optional UC10 accept; shares UC4 (family list), UC6 (optional parity) |
| **Web System / Admin** | UC7, UC13, UC15, UC16, UC21–UC23; shares UC20 |
| **Backend** | Source of truth for all mutations and authorization |

### Family lifecycle — mobile vs web (product split)

| Action | Mobile | Web | Notes |
| --- | --- | --- | --- |
| Create Family Circle (UC8) | Yes | Yes | Very simple |
| Invite Member — link/code + share (UC9) | Yes | Yes | Mobile is better for sharing |
| Accept / Decline Invitation (UC10) | Yes | Optional | Mainly mobile |
| Switch Profile (UC11) | Yes | — | Daily use |
| Manage Family Circle (UC12) | Optional / limited | Primary | Roster, edit, remove, toggle active |

---

## 0c. Task assignment

| Assigned to | UC ID / area | Feature / task | Priority |
| --- | --- | --- | --- |
| **Kwok Heng** | UC1 | Manage App User Dietary Profile | Core MVP |
| | UC4 | View Scan History | Core MVP |
| **Khai** | UC2 | Scan Product Barcode | Core MVP |
| | UC6 | View Family Allergy Summary | Core MVP |
| | — | DevSecOps / CI/CD (**owner**) | N/A |
| **Huayuan** | UC3 | View Safety Verdicts | Core MVP |
| | UC14 | View Scan Verdict Trend | Enhanced |
| **Chai Lee** | UC5 | View Alternative Product Recommendation | Core MVP |
| | UC17 | View Recommendation History | Enhanced |
| | — | Database Setup & Maintenance (**owner**) | N/A |
| **Amelia** | UC8 | Create Family Circle | Core MVP |
| | UC9 | Invite Family Member to Circle | Core MVP |
| | UC10 | Accept Family Invitation | Core MVP |
| | UC11 | Switch Family Profile | Core MVP |
| | UC12 | Manage Family Circle | Core MVP |
| | — | DevSecOps / CI/CD (**support**) | N/A |
| | — | Database Setup & Maintenance (**support**) | N/A |
| **Maowei** | UC13 | Manage User Account Status | Core MVP |
| | UC7 | Generate Consumer Trends | Core MVP |
| | UC18 | User Registration | Enhanced |
| | UC19 | User Login / Logout | Enhanced |

### Cross-cutting ownership

| Area | Owner | Support |
| --- | --- | --- |
| DevSecOps / CI/CD | Khai | Amelia |
| Database Setup & Maintenance | Chai Lee | Amelia |

### By person (workload summary)

| Person | Core MVP | Enhanced | Cross-cutting |
| --- | --- | --- | --- |
| Kwok Heng | UC1, UC4 | — | — |
| Khai | UC2, UC6 | — | DevSecOps/CI/CD (owner) |
| Huayuan | UC3 | UC14 | — |
| Chai Lee | UC5 | UC17 | Database (owner) |
| Amelia | UC8–UC12 | — | DevSecOps (support), Database (support) |
| Maowei | UC7, UC13 | UC18, UC19 | — |

Unassigned (no owner yet): UC15, UC16, UC20–UC24.

---

## 0d. Alignment rules (schema & engineering)

| Rule | Resolution |
| --- | --- |
| UC1 “create profile after registration” | `dietary_profiles.family_id` is NOT NULL today → approved path is **UC8** bootstrap SELF profile (or nullable `family_id` migration if owners approve). Do not ship orphan profiles silently. |
| UC9 invite **or** dependant | One epic: **UC9-S2** PENDING invite with shareable link/code (mobile + web); **UC9-S3** admin-managed dependant (`linked_user_id` NULL, web-primary UI). |
| Family client split | Create + invite on **both** clients; accept mainly **mobile**; switch **mobile-only**; manage **web-primary** (mobile optional/limited). |
| UC4 two surfaces | Personal history (mobile) + family-admin filterable list (web). Charts are **UC14**, not UC4. |
| UC5 vs UC17 | UC5 = suggest alternatives at verdict time; UC17 = list **past** recommendations (Enhanced). |
| UC7 vs UC14 vs UC22 | UC7 = anonymised platform trends (Core); UC14 = family verdict chart (Enhanced); UC22 = CSV export of UC7-style aggregates (Nice-to-Have). |
| UC18 / UC19 | Enhanced package; **UC19 JWT mostly shipped** (login/refresh/logout + clients). Finish S3 for remaining public routes (notably `POST /api/scan/validate`). Family Admin stays membership-based. |
| Profile vs account active | `dietary_profiles.is_active` (UC12) ≠ `users.is_active` (UC13 / UC19 login gate). |
| Family Admin | `family_members.PRIMARY_ADMIN` — not a platform JWT role. |

### Open decisions

| ID | Decision | Proposal |
| --- | --- | --- |
| D1 | Platform roles + JWT | Live: `USER` / `ADMIN` in JWT; web portal maps to `ROLE_FAMILY_ADMIN` / `ROLE_SYSTEM_ADMIN`. Rename to `APP_USER` / `SYSTEM_ADMIN` still optional. |
| D2 | One family per user (MVP)? | Yes + `UNIQUE(family_members.user_id)` |
| D3 | Admin edit another adult’s restrictions? | No — self + unlinked dependants only |
| D4 | Invite then accept | PENDING + UC10 (matches UC9/UC10) |
| D5 | Dependant model | Profile-only (`linked_user_id` NULL) |
| D6 | Active profile storage | `user_preferences.active_profile_id` |
| D7 | Profile inactive flag | `dietary_profiles.is_active` |
| D8 | Restriction codes | Web → DB catalog codes |
| D9 | Verdict wire vs UI | Wire `UNSAFE`; UI Unsafe/Avoid |
| D10 | Sprint commitment | Commit Core MVP stories in Jira; sequence UC19 early |
| D11 | UC1 create without family | Prefer UC8 bootstrap; document if schema change approved |

---

## 1. Executive assessment

**Core MVP (UC1–UC13)** covers dietary profile, scan/verdict/history, alternatives, family lifecycle (create, invite/dependant, accept, switch, manage, allergy grid), consumer trends, and system account admin.

**Enhanced (UC14–UC19)** covers family verdict trends, usage and health dashboards, recommendation history, registration, and login/logout.

**Nice-to-Have (UC20–UC24)** covers product reporting, AI admin logs, trend export, subscriptions, and OCR scan.

**Current repo gaps (post-UC19 integration):** `POST /api/scan/validate` still transitional public; web mock still used when `VITE_USE_MOCK_API=true`; UC1 severity picker / unknown-code 400 polish; UC5/UC7/UC13 open. UC12 manage CRUD shipped (web + backend). UC4 history complete.

---

## 2. Repository and database findings (summary)

### Live APIs (reuse / harden)

| Method | Path | Auth today |
| --- | --- | --- |
| POST | `/api/auth/register`, `/login`, `/refresh`, `/logout` | Public (logout cookie-auth) |
| GET | `/api/auth/me` | JWT |
| GET/POST | `/api/families`, `/api/families/me`, `/me/restriction-summary`, `/me/scans` | JWT (`/me/scans` PRIMARY_ADMIN) |
| GET | `/api/families/{familyId}/profiles` | JWT (family subtree) |
| POST | `/api/scan/assess` | JWT + profile ownership |
| GET | `/api/restrictions` | JWT |
| GET/PUT | `/api/profiles/{profileId}/restrictions` | JWT + D3 ownership |
| POST | `/api/scan/validate` | Transitional public |
| GET | `/api/scan/history/{profileId}` | JWT + profile ownership |

Missing: recommendations API; admin list/PATCH APIs; JWT on validate (UC19-S3 residual).

### Required migrations

| ID | Change |
| --- | --- |
| M1 | Canonical platform roles *(live `USER`/`ADMIN`; optional rename)* |
| M2 | `UNIQUE(user_id)` on `family_members` if D2 — **Done** |
| M3 | `user_preferences.active_profile_id` |
| M4 | `dietary_profiles.is_active` — **Done** (UC12) |
| M5 | Invitation constraints / `invited_by` |
| M6 | PREFERENCE severity vs engine |
| M7 | Recommendation history persistence (UC17) if not derivable from scans alone |

### UC status vs repo

| UC | Package | Status (detail) |
| --- | --- | --- |
| UC1 | Core | **Partial** — live `GET/PUT` restrictions + mobile sheet; JWT + D3 ownership shipped; severity fixed `STRICT_AVOID`; unknown-code 400 open |
| UC2 | Core | **Partial** — ML Kit → validate/assess; assess JWT + profile ownership / inactive checks; validate still public |
| UC3 | Core | **Mostly complete** — rule engine + colour-coded verdict (`SAFE`/`WARNING`/`UNSAFE`); Alternatives empty (UC5) |
| UC4 | Core | **Complete** — personal history JWT+authz; family `/me/scans` PRIMARY_ADMIN; wire `SAFE`/`WARNING`/`UNSAFE` |
| UC5 | Core | **Not started** — Alternatives shell; no recommendations API |
| UC6 | Core | **Partial (S1/S2 mostly shipped)** — live `/me/restriction-summary` includes members + dependants; mobile grid; web parity polish (S3) |
| UC7 | Core | **Partial** — admin trends mock; `daily_consumer_trends` unused by Java |
| UC8 | Core | **Complete (MVP)** — create + `/me` + JWT 401 + web/mobile empty-state; diagrams follow-on open |
| UC9 | Core | **Complete (MVP)** — live invite/dependant; web + mobile share/deep links; register/login claim; live `/me/members` list |
| UC10 | Core | **Complete (MVP)** — inbox list/accept/decline + Resend optional; web inbox optional residual |
| UC11 | Core | **Complete (MVP)** — server GET/PUT active-profile; mobile persists; inactive omitted from list |
| UC12 | Core | **Done** — web + backend manage (roster, PUT metadata, D3 restrictions, soft-remove, PATCH active) |
| UC13 | Core | **Complete (MVP)** — live list/search/filter + transactional Suspend/Reactivate with session revocation, audit, and ADMIN protections |
| UC14 | Enhanced | **Not started** |
| UC15–UC16 | Enhanced | **Not started** |
| UC17 | Enhanced | **Not started** |
| UC18 | Enhanced | **Mostly complete** — register API + web/mobile UI; no auto-login |
| UC19 | Enhanced | **Mostly complete** — JWT login/refresh/logout + mobile/web clients; S3 residual (`validate` still public); AC3 distinct 403 polish |
| UC20 | Nice-to-Have | **Not started** / reporting README |
| UC21 | Nice-to-Have | **Partial** — `AiExecutionLogService` write path (flag default off) + seeds; no admin dashboard |
| UC22–UC24 | Nice-to-Have | **Not started** |

**Cross-cutting:** Spring Security + JWT shipped for families, invitations, assess, scan history, profiles/restrictions, and admin; web default mock is **off** (`VITE_USE_MOCK_API=false`). Highest-maturity path: register/login → create circle or seeded family → mobile scan → assess → verdict → history + UC6 summary.

---

## 3. Proposed domain and authorization model

> Epic Owners must validate this model before Ready for Development.

### Concepts

| Concept | Meaning |
| --- | --- |
| User account | `users`; `is_active` gates **login** (UC19/UC13) |
| Family circle | `families`; MVP one membership per user (D2) |
| Membership | `PRIMARY_ADMIN` \| `MEMBER` |
| Dependant profile | `dietary_profiles` with `linked_user_id` NULL (UC9) |
| Active scan profile | `user_preferences.active_profile_id` (UC11) |
| System administrator | Platform `SYSTEM_ADMIN` |

### Permission matrix (proposed)

| Action | Self | Member | PrimAdmin | SysAdmin |
| --- | --- | --- | --- | --- |
| Edit own linked restrictions | Y | Y | Y | N\* |
| Edit other adult linked restrictions | N | N | N† | N\* |
| Manage dependant profile | N | N | Y | N\* |
| View allergy summary | Y | Y | Y | N\* |
| Switch active scan profile | Y | Y | Y | N\* |
| Create family | Y‡ | — | — | N |
| Invite / create dependant | N | N | Y | N |
| Accept/decline invitation | Y | — | — | N |
| Manage circle (view/update/remove/activate) | N | N§ | Y | N |
| Personal scan history | Y | Y | Y | N\* |
| Family scan history list | N | N | Y | N\* |
| Family verdict trends (UC14) | N | N | Y | N\* |
| Consumer trends / usage / health / AI logs | N | N | N | Y |
| Platform user CRUD / subscriptions | N | N | N | Y |

\* SysAdmin does not browse family PII via family APIs in MVP.  
† D3 default.  
‡ If not already in a family.  
§ View-only for members optional for roster — product UC12 is Family Admin; UC6 is the member-facing overview.

---

## 4. Proposed epic hierarchy

### By prioritisation package

| Package | Epics |
| --- | --- |
| **Core MVP** | UC1–UC13 |
| **Enhanced** | UC14–UC19 |
| **Nice-to-Have** | UC20–UC24 |

### By architecture package

| Architecture package | Epics |
| --- | --- |
| Authentication & Security | UC18, UC19 |
| Web Client (Family) | UC8, UC9, UC12 (manage primary), UC14; optional UC10; UC4 family-list surface (coordinate with UC4 owner) |
| Mobile Client | UC1–UC3, UC5, UC6 (primary), UC8, UC9 (invite + share), UC10, UC11, UC17, UC24; UC12 optional/limited |
| Shared Client | UC4 (personal + family list), UC20 |
| Web Client (Admin) | UC7, UC13, UC15, UC16, UC21–UC23 |

### Shared diagrams

- `docs/architecture/domain-family.mmd` — UC1, UC6, UC8–UC12
- `docs/architecture/domain-scan-assess.mmd` — UC2–UC5, UC17, UC24
- `docs/architecture/seq-family-verdict-trends.mmd` — UC14

---

### EPIC UC19 — User Login / Logout

**Owner:** Maowei · **Package:** Enhanced (**Core critical path**) · **Architecture:** Authentication & Security

| | |
| --- | --- |
| **Status** | **Mostly complete** — JWT login/refresh/logout + mobile/web clients; residual S3 (validate) + suspended-403 polish |
| **Stories** | UC19-S1…S5 |
| **Dependencies** | None |
| **In** | Spring Security + JWT; login/logout/refresh; protect business APIs; mobile/web token clients |
| **Out** | OAuth; MFA; polished password-reset (unless added) |
| **Shipped** | `AuthController`/`AuthService`; Bearer + refresh cookie; `AuthSessionStore` + web session; families, invitations, assess, history, profiles/restrictions protected |
| **Open** | UC19-S3 remaining public routes (`POST /api/scan/validate`); distinct 403 for inactive accounts; web auto-refresh |

---

### EPIC UC18 — User Registration

**Owner:** Maowei · **Package:** Enhanced · **Architecture:** Authentication & Security

| | |
| --- | --- |
| **Status** | **Mostly complete** — API + web/mobile register; no auto-login (UC19) |
| **Stories** | UC18-S1 register API; UC18-S2 register UI |
| **Dependencies** | UC19 (login after register); unblocks UC8 empty-state demo for new users |
| **In** | Register with required details; reject duplicates; secure credential storage; proceed to login/onboarding; does **not** create a family circle |
| **Out** | Social sign-up |
| **Shipped** | `POST /api/auth/register`; web `/family-register`; mobile registration flow; email/password validation |

---

### EPIC UC1 — Manage App User Dietary Profile

**Owner:** Kwok Heng · **Package:** Core MVP · **Architecture:** Dietary Profile / Mobile Client

| | |
| --- | --- |
| **Stories** | UC1-S1…S5 (authz, codes, mobile editor, bootstrap path, UI states) |
| **Dependencies** | UC19; UC8 for bootstrap profile |
| **In** | Change restrictions/allergens/preferences; create dietary profile after registration (aligned with schema) |
| **Out** | Free-text allergens beyond catalog unless approved |
| **Shipped** | JWT + D3 ownership on GET/PUT restrictions; mobile editor (severity fixed `STRICT_AVOID`); UC8 SELF bootstrap |
| **Open** | Unknown-code → 400; severity picker; empty-state polish |

---

### EPIC UC2 — Scan Product Barcode

**Owner:** Khai · **Package:** Core MVP · **Architecture:** Scanning & Verdicts / Mobile Client

| | |
| --- | --- |
| **Stories** | UC2-S1…S5 (authz, camera/validate, assess, failure states, no web scan) |
| **Dependencies** | UC19-S3 (finish validate), UC11; UC1 for restriction quality |
| **Note** | Assess JWT identity + `FamilyAuthorizationService` profile ownership / inactive 409 shipped; validate still public |

---

### EPIC UC3 — View Safety Verdicts

**Owner:** Huayuan · **Package:** Core MVP · **Architecture:** Scanning & Verdicts / Mobile Client

| | |
| --- | --- |
| **Stories** | UC3-S1…S4 |
| **Dependencies** | UC2 |
| **In** | Safe / Warning / Unsafe detail; plain-language findings; engine owns verdict |
| **Out** | Complex charts; client-side override; alternatives (UC5) |
| **Note** | Mobile `ProductDetailScreen` shows wire labels `SAFE` / `WARNING` / `UNSAFE` |

---

### EPIC UC4 — View Scan History

**Owner:** Kwok Heng · **Package:** Core MVP · **Architecture:** Shared Client  
**Coordination:** UC4-S2 family list API is Kwok Heng; Family Portal page shell/nav coordinates with Amelia (Web Family).

| | |
| --- | --- |
| **Stories** | UC4-S1…S4 (personal; family API; family web page; verdict wire) |
| **Dependencies** | UC2/UC3; UC8 for family list |
| **Status** | **Complete** — personal history JWT+authz; family `/me/scans` PRIMARY_ADMIN; wire `SAFE`/`WARNING`/`UNSAFE` |
| **In** | Mobile personal history; Family Admin filterable list + row detail |
| **Out** | Trend charts (→ UC14) |

---

### EPIC UC5 — View Alternative Product Recommendation

**Owner:** Chai Lee · **Package:** Core MVP · **Architecture:** Scanning & Verdicts / Mobile Client

| | |
| --- | --- |
| **Stories** | UC5-S1 API; UC5-S2 Alternatives tab; UC5-S3 UC17 boundary |
| **Dependencies** | UC2, UC3 (history of Safe scans helpful; not a hard UI dependency on UC4 list) |

---

### EPIC UC6 — View Family Allergy Summary Grid

**Owner:** Khai · **Package:** Core MVP · **Architecture:** Family Management / **Mobile Client** (primary; optional React web parity)

| | |
| --- | --- |
| **Stories** | UC6-S1 summary API; UC6-S2 mobile grid; UC6-S3 optional web parity |
| **Dependencies** | UC19, UC8, UC1 |
| **Status** | **Partial** — S1/S2 mostly shipped (live summary API + mobile grid); S3 web parity + AC polish open |
| **In** | Matrix of members vs restrictions; family-scoped only; primary client mobile |
| **Out** | Editing (→ UC12) |

---

### EPIC UC7 — Generate Consumer Trends

**Owner:** Maowei · **Package:** Core MVP · **Architecture:** Analytics / Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC7-S1 anonymised API; UC7-S2 admin dashboard |
| **Dependencies** | UC19 |
| **In** | Anonymised aggregates by category |
| **Out** | CSV export (→ UC22); family charts (→ UC14) |

---

### EPIC UC8 — Create Family Circle

**Owner:** Amelia · **Package:** Core MVP · **Architecture:** Shared (Mobile + Web Family)

| | |
| --- | --- |
| **Status** | **Complete (MVP)** — UC8-S1–S4 done; architecture diagrams follow-on open |
| **Stories** | UC8-S1…S4 |
| **Dependencies** | UC19 (JWT shipped for family routes); UC18 helps demo empty-state create |
| **In** | Create circle; creator PRIMARY_ADMIN; bootstrap SELF profile; `GET /families/me`; web + mobile empty-state create |
| **Out** | Invites (UC9); accept inbox (UC10); manage roster (UC12); architecture diagrams still open |
| **Shipped** | D2 UNIQUE; `POST /api/families`; `GET /me`; web `FamilyMeGate` / `CreateFamilyCirclePage`; mobile drawer + `CreateFamilyCircleScreen`; tests for 201/400/409/401; `family/dto` packaging |
| **Caller identity** | Bearer JWT (`@AuthenticationPrincipal`). DB `PRIMARY_ADMIN` vs web portal `ROLE_FAMILY_ADMIN` — document mapping |

---

### EPIC UC9 — Invite Family Member to Circle

**Owner:** Amelia · **Package:** Core MVP · **Architecture:** Shared (Mobile + Web Family) — **mobile preferred for share**

| | |
| --- | --- |
| **Status** | **Complete (MVP)** — S1–S4 shipped (deep links + login claim + live roster list) |
| **Stories** | UC9-S1…S4 (migration; invite+share API; dependant API; mobile+web UI) |
| **Dependencies** | UC19, UC8; UC1 for dependant restrictions |
| **In** | PENDING invite with **shareable link/code**; email/user-search; admin-managed dependant profile (API + web-primary UI; mobile optional); register/login auto-claim |
| **Out** | Silent mock link; full roster manage (UC12) |
| **Shipped** | Schema `invited_by` + `invite_code`; Spring Data family repos; search/invite/claim/dependant APIs; `GET /me/members`; web invite + dependant + `/invite/:token`; mobile invite+share+deep links+login claim+dependant create |
| **Residuals** | Web UC10 inbox optional |

---

### EPIC UC10 — Accept Family Invitation

**Owner:** Amelia · **Package:** Core MVP · **Architecture:** Mobile Client (primary) & Email (Resend); web optional

| | |
| --- | --- |
| **Status** | **Complete (MVP)** — list/accept/decline + Resend optional; web inbox optional residual |
| **Stories** | UC10-S1…S4 (list; accept; decline/guards; Resend) |
| **Dependencies** | UC19, UC9 |
| **In** | List pending invites; accept → MEMBER + linked profile; decline → DECLINED; mobile inbox primary |
| **Out** | Creating invitations (UC9); web accept is optional parity only |
| **Note** | `POST .../invitations/claim` + register `invitationToken` still join without an inbox |

---

### EPIC UC11 — Switch Family Profile

**Owner:** Amelia · **Package:** Core MVP · **Architecture:** Mobile Client

| | |
| --- | --- |
| **Stories** | UC11-S1…S4 |
| **Dependencies** | UC19; UC8-S3 (`/families/me`) or seeded membership for early delivery |
| **Status** | **Complete (MVP)** — server GET/PUT active-profile; mobile persists selection; inactive omitted from list |
| **In** | Daily active-profile switch on mobile (server-persisted) |
| **Out** | Web profile switcher (not required for MVP) |

---

### EPIC UC12 — Manage Family Circle

**Owner:** Amelia · **Package:** Core MVP · **Architecture:** Web Client (Family) primary; mobile optional/limited

| | |
| --- | --- |
| **Stories** | UC12-S1…S7 (migration; view; update metadata; update restrictions; remove; activate; polish) |
| **Dependencies** | UC19, UC8, UC1, UC11 |
| **In** | View; update; remove; activate/deactivate on **web**; mobile may expose limited subset later |
| **Out** | Transfer PRIMARY_ADMIN without process; hard-delete profiles with scans; full mobile admin parity |

**User stories**

1. View all members in my family circle.  
2. Update an existing member’s dietary profile.  
3. Remove a member from the family circle.  
4. Activate or deactivate a member’s dietary profile.

---

### EPIC UC13 — Manage User Account Status

**Owner:** Maowei · **Package:** Core MVP · **Architecture:** Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC13-S1…S3; UC13-T1 docs |
| **Dependencies** | UC19 |
| **In** | Existing-account listing; read-only `USER` / `ADMIN`; Suspend/Reactivate through `users.is_active`; RBAC; refresh-session revocation; transition audit |
| **Out** | `users.role_id` mutation; Family Admin as platform ADMIN; public ADMIN registration; System Admin provisioning; audit-read UI |

---

### EPIC UC14 — View Scan Verdict Trend

**Owner:** Huayuan · **Package:** Enhanced · **Architecture:** Web Client (Family)

| | |
| --- | --- |
| **Stories** | UC14-S1 trends API; UC14-S2 chart page |
| **Dependencies** | UC19, UC8, UC2–UC4 |
| **In** | Daily/weekly Safe / Warning / Unsafe family chart |
| **Out** | UC4 list page charts; UC7 platform trends |

---

### EPIC UC15 — View Application Usage Statistics

**Owner:** *Unassigned* · **Package:** Enhanced · **Architecture:** Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC15-S1 usage dashboard API + page |
| **Dependencies** | UC19 |

---

### EPIC UC16 — View System Health Logs

**Owner:** *Unassigned* · **Package:** Enhanced · **Architecture:** Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC16-S1 health events list/filter (Actuator / infra logs) |
| **Dependencies** | UC19 |

---

### EPIC UC17 — View Recommendation History

**Owner:** Chai Lee · **Package:** Enhanced · **Architecture:** Mobile Client

| | |
| --- | --- |
| **Stories** | UC17-S1 list past recommendations for profile/user |
| **Dependencies** | UC5 (writes/history source) |
| **In** | List past product recommendations |
| **Out** | Generating new alternatives (UC5) |

---

### EPIC UC20 — Report Incorrect Product Information

**Owner:** *Unassigned* · **Package:** Nice-to-Have · **Architecture:** Shared Client

| | |
| --- | --- |
| **Stories** | UC20-S1 mobile flag; UC20-S2 admin review queue |
| **Dependencies** | UC3, UC19 |

---

### EPIC UC21 — View AI Reasoning Performance and Accuracy Logs

**Owner:** *Unassigned* · **Package:** Nice-to-Have · **Architecture:** Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC21-S1 dashboard over AI execution traces |
| **Dependencies** | UC19; `ai_execution_logs` |

---

### EPIC UC22 — Export Consumer Trends

**Owner:** *Unassigned* · **Package:** Nice-to-Have · **Architecture:** Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC22-S1 CSV export of aggregated trends |
| **Dependencies** | UC7, UC19 |

---

### EPIC UC23 — Manage Subscription / Premium Plans

**Owner:** *Unassigned* · **Package:** Nice-to-Have · **Architecture:** Web Client (Admin)

| | |
| --- | --- |
| **Stories** | UC23-S1 tier CRUD + feature availability |
| **Dependencies** | UC19, UC13 |

---

### EPIC UC24 — Scan Ingredient List with OCR

**Owner:** *Unassigned* · **Package:** Nice-to-Have · **Architecture:** Mobile Client

| | |
| --- | --- |
| **Stories** | UC24-S1 OCR capture/extract; UC24-S2 review → assess |
| **Dependencies** | UC1, UC2/UC3 |
| **In** | Unbarcoded products; never false Safe on OCR failure |
| **Out** | Replacing barcode Core path (UC2) |

---

## 5. Proposed Jira child stories

Priority P0–P3 is a planning hint. Every story inherits §8 DoD.  
**AC mapping:** Detailed criteria live in [`sprint2-mvp-epics.md`](sprint2-mvp-epics.md). The **AC #** column lists which checklist rows that story closes (same UC). Sub-tasks stay under the parent UC epic in Jira.

### UC19 / UC18 (Authentication & Security)

| Story | Summary | AC # (mvp) | Priority |
| --- | --- | --- | --- |
| **UC19-S1** | Spring Security + JWT login/refresh — **Mostly done** (AC3 distinct 403 polish) | UC19: 1–3, 7 | P0 |
| **UC19-S2** | Canonical platform roles + authority mapping — **Done** (`USER`/`ADMIN`) | UC19: 5–6 | P0 |
| **UC19-S3** | Protect existing business endpoints — **Partial** (families, invitations, assess, history, profiles/restrictions; finish validate) | UC19: 4 | P0 |
| **UC19-S4** | Logout — invalidate token; clear local credentials — **Done** | UC19: 8–10 | P1 |
| **UC19-S5** | Mobile + web login/logout UX (loading/error) — **Done** | UC19: 11–12 | P1 |
| **UC18-S1** | Register API — duplicates rejected; secure hash + validation — **Done** | UC18: 1–4, 6 | P2 |
| **UC18-S2** | Mobile + web register UI → login/onboarding — **Done** (no auto-login) | UC18: 5, 7–8 | P2 |

### UC1 — Dietary profile

| Story | Summary | AC # | Priority |
| --- | --- | --- | --- |
| **UC1-S1** | Authorize restriction GET/PUT (ownership matrix) — **Mostly done** (AC10 unknown-code → 400 open) | 1–2, 10–12, 16 | P0 |
| **UC1-S2** | Align restriction codes + PREFERENCE (D8/M6) | (supports 3–6) | P0 |
| **UC1-S3** | Mobile editor — add/change/remove + save round-trip — **Mostly done** (severity picker open) | 3–7 | P0 |
| **UC1-S4** | Create-after-registration path — **UC8 create-circle bootstrap shipped**; confirm AC coverage / polish | 8–9 | P0 |
| **UC1-S5** | Mobile loading / empty / error states — **Partial** (empty state polish) | 13–15 | P1 |

### UC8 / UC9 / UC10 — Family lifecycle

| Story | Summary | AC # | Priority |
| --- | --- | --- | --- |
| **UC8-S1** | UNIQUE membership (D2) — **Done** (`uq_family_members_user_id`) | UC8: 6 | P0 |
| **UC8-S2** | POST `/api/families` + PRIMARY_ADMIN + SELF profile — **Done** (incl. JWT 401) | UC8: 1–5, 7–8 | P0 |
| **UC8-S3** | GET `/api/families/me` — **Done** (API + web + mobile resolve) | UC8: 5, 10 | P0 |
| **UC8-S4** | Create CTA + loading/validation/error — **Done** (web + mobile when no family) | UC8: 9, 11 | P1 |
| **UC8 follow-on** | Class/sequence diagrams under `docs/architecture/` — **Open** | Design | P2 |
| **UC9-S1** | Invitation migration / status constraints (M5); share token/code + `InvitationStatus` — **Done** | UC9: (supports 2–4, 15) | P0 · done |
| **UC9-S2** | User search + create PENDING invitation returning shareable code/link; register/login claim — **Done** | UC9: 1–7, 15 | P0 · done |
| **UC9-S3** | Create dependant dietary profile (API; web-primary UI; mobile optional live) — **Done** | UC9: 9–13 | P0 · done |
| **UC9-S4** | Mobile invite+share + deep links + web invite (no silent link); UI states — **Done** | UC9: 8, 14–16 | P1 · done |
| **UC10-S1** | List pending invitations (mobile primary; web optional) | UC10: 1–2, 10, 12 | P0 · done |
| **UC10-S2** | Accept invitation → MEMBER + linked profile | UC10: 3–4, 7–9 | P0 · done |
| **UC10-S3** | Decline + expired/invalid/mismatch guards | UC10: 5–8 | P0 · done |
| **UC10-S4** | Resend invitation email (as designed) | UC10: 11 | P1 · done |

### UC11 / UC12 — Switch & manage

| Story | Summary | AC # | Priority |
| --- | --- | --- | --- |
| **UC11-S1** | Migration `active_profile_id` — **Done** | UC11: (supports 2–4) | P0 |
| **UC11-S2** | GET/PUT active-profile API + authz (family / inactive) — **Done** | UC11: 1–3, 6–7 | P0 |
| **UC11-S3** | Persist across restart; drive assess; remove hardcodes — **Done** | UC11: 4–5, 8 | P0 |
| **UC11-S4** | Mobile switcher UX (web selector not required) — **Done** | UC11: 9–10 | P1 |
| **UC12-S1** | Migration `dietary_profiles.is_active` | UC12: 1 | P0 · **done** |
| **UC12-S2** | View roster — live `GET /me/members` + `GET /me/profiles` + role/active | UC12: 2–6 | P0 · **done** |
| **UC12-S3** | Update profile metadata | UC12: 7, 9, 19 | P0 · **done** |
| **UC12-S4** | Update restrictions via UC1 rules (D3) | UC12: 8–9 | P0 · **done** |
| **UC12-S5** | Remove member + last-admin / confirm / soft-remove | UC12: 10–14 | P0 · **done** |
| **UC12-S6** | Activate/deactivate profile + switcher/assess effects | UC12: 15–18 | P0 · **done** |
| **UC12-S7** | Production path mock-off + UI states polish | UC12: 19–20 | P1 · **done** |

### UC2 / UC3 / UC4 / UC5 — Scan path

| Story | Summary | AC # | Priority |
| --- | --- | --- | --- |
| **UC2-S1** | Authorize assess by family + active/inactive rules; JWT userId — **Done** | UC2: 5–7 | P0 |
| **UC2-S2** | Camera + ML Kit barcode → `POST /scan/validate` | UC2: 1–3 | P0 |
| **UC2-S3** | Assess call + navigate to verdict (UC3) | UC2: 4, 12 | P0 |
| **UC2-S4** | Failure states — unknown / non-food / network (never false Safe) | UC2: 8–11 | P0 |
| **UC2-S5** | No web scan by design (doc + guard if needed) | UC2: 13 | P2 |
| **UC3-S1** | Colour-coded verdict UI + plain-language reason + findings list | UC3: 1–5, 11 | P0 |
| **UC3-S2** | Engine-owned verdict; incomplete data / may-contain → Warning | UC3: 6–9 | P0 |
| **UC3-S3** | Wire `UNSAFE` display alignment (mobile shows UNSAFE) — **Done** | UC3: 2 | P1 |
| **UC3-S4** | Verdict loading/error states after navigation | UC3: 10 | P1 |
| **UC4-S1** | Personal history API authz + mobile list/detail | UC4: 1–4, 12–13 | P0 · **done** |
| **UC4-S2** | Family scans list API (PRIMARY_ADMIN, family-scoped) | UC4: 5–6, 10–11 | P0 · **done** |
| **UC4-S3** | Family history web page — filters + row detail (no chart) | UC4: 7–9, 12–13 | P0 · **done** |
| **UC4-S4** | Web verdict wire alignment (`SAFE` / `WARNING` / `UNSAFE`) | UC4: 14 | P1 · **done** |
| **UC5-S1** | Recommendations API (authorized profile; exclude current) | UC5: 1, 4–5, 7 | P0 |
| **UC5-S2** | Alternatives tab — show Warning/Unsafe; hide Safe; empty state | UC5: 2–3, 6, 8 | P0 |
| **UC5-S3** | Do not build recommendation-history screen here (UC17 boundary) | UC5: 9 | P2 |

### UC6 / UC7 / UC13

| Story | Summary | AC # | Priority |
| --- | --- | --- | --- |
| **UC6-S1** | Restriction-summary API (`/families/me/restriction-summary`) — **Mostly done** | UC6: 1–2, 5–6, 11 | P0 |
| **UC6-S2** | Mobile primary grid + empty/error — **Mostly done** (polish AC4/AC8) | UC6: 3–4, 7–10 | P0 |
| **UC6-S3** | Optional web parity page (same API) | UC6: 12 | P2 |
| **UC7-S1** | Anonymised consumer-trends API (no PII; admin-only) | UC7: 1–2, 4–5 | P0 |
| **UC7-S2** | System Admin trends dashboard UI | UC7: 3, 6–8 | P0 |
| **UC13-S1** | List/search/filter users API + production admin page — **Done** | UC13: 1, 11–12 | P0 |
| **UC13-S2** | Transactional PATCH status + suspension revocation + transition audit — **Done** | UC13: 2–4, 6–7, 10 | P0 |
| **UC13-S3** | Read-only roles; active-ADMIN RBAC; self/last-admin/concurrency guards — **Done** | UC13: 5, 8–9 | P0 |
| **UC13-T1** | Document status-only role model + permission boundary — **Done** | — | P1 |

### Enhanced / Nice-to-Have

| Story | Summary | AC # | Package |
| --- | --- | --- | --- |
| **UC14-S1** | Family verdict-trends API | UC14: 1–2, 6–7 | Enhanced |
| **UC14-S2** | Family chart page (not inside UC4 list) | UC14: 3–5, 8 | Enhanced |
| **UC15-S1** | Usage stats API + admin dashboard | UC15: 1–6 | Enhanced |
| **UC16-S1** | Health events list/filter (admin-only) | UC16: 1–5 | Enhanced |
| **UC17-S1** | Recommendation history API + list UI | UC17: 1–6 | Enhanced |
| **UC20-S1** | Mobile flag incorrect product/ingredient | UC20: 1–2, 5–6 | Nice-to-Have |
| **UC20-S2** | Admin review queue for reports | UC20: 3–4 | Nice-to-Have |
| **UC21-S1** | AI performance logs dashboard + filters | UC21: 1–6 | Nice-to-Have |
| **UC22-S1** | Consumer trends CSV export (no PII) | UC22: 1–6 | Nice-to-Have |
| **UC23-S1** | Subscription tier CRUD + feature flags | UC23: 1–7 | Nice-to-Have |
| **UC24-S1** | Capture + ML Kit OCR extract | UC24: 1–2, 6–8 | Nice-to-Have |
| **UC24-S2** | Review/correct text → same assess pipeline | UC24: 3–5, 7, 9 | Nice-to-Have |

---

## 5b. Recommended delivery sequence

**Core MVP target:** UC1–UC13.  
**Canonical sequence** (also used by mvp-epics build order).

**Already shipped:** UC18-S1/S2; UC19-S1/S2/S4/S5 (JWT + clients); UC8–UC12 (family lifecycle + switch + manage); UC6-S1/S2; **UC4-S1–S4**; UC2 assess authz; UC3 wire UNSAFE; UC1 JWT + D3 ownership. Remaining auth: UC19-S3 close-out (`validate`) + AC3 polish.

| Sprint | Focus | Stretch |
| --- | --- | --- |
| **Sprint 2** | UC19-S3 residual + UC1 polish; UC4/UC11/UC12/authz **done** | UC1-S2 severity |
| **Sprint 3** | UC5-S1/S2; UC7-S1/S2 | UC6-S3 |

**Remaining Core MVP (next):** UC5-S1/S2; UC7-S1/S2; UC1 severity/empty polish; UC19-S3 validate + AC3. UC13-S1…S3 are shipped.

**Seeded-family exception:** Scan work may still use Tan/Lim/Wong seeds for demo data. New users create a circle via UC8 after UC18 register + UC19 login; active profile persists via UC11.

**Enhanced / Nice-to-Have:** UC14–UC24 after Core commitment. UC19 foundation is in place; finish S3 (`validate`) before treating all Core APIs as production-authz complete.
---

## 6. Dependency map

```text
UC18 (shipped) ──► UC8 empty-state demo (after UC19 login)
UC19 (mostly shipped) ──► UC8 AC8 done; protects families + invitations + assess + history + profiles/restrictions
UC19-S3 (residual) ──► protect validate (+ leftovers)
UC8-S1…S4 (shipped) ──► UC9 (shipped) ──► UC10 (shipped; web inbox optional)
                       └──► UC9-S3 dependant (shipped; web roster manage → UC12)
UC8-S3 (/me) ──► UC11 (shipped) ──► UC2 ──► UC3 ──► UC4 (shipped)
UC6-S1/S2 (mostly shipped) ──► UC6-S3 web parity
UC19 + FamilyAuthorizationService ──► UC1 ownership authz (mostly shipped) ──► UC6 / UC12
UC8 + UC1 + UC11 ──► UC12 (shipped)
UC3 ──► UC5 ──► UC17
UC2/UC3 ──► UC14, UC24
UC19 ──► UC7 ──► UC22
UC19 ──► UC13 ──► UC23
UC19 ──► UC15, UC16, UC21
UC3 ──► UC20
```

---

## 7. Milestone summary

| Milestone | Focus |
| --- | --- |
| Core MVP | UC1–UC13 end-to-end family + scan + admin accounts |
| Enhanced | UC14–UC19 trends, usage, health, rec history, register/login polish |
| Nice-to-Have | UC20–UC24 reporting, AI admin, export, subscriptions, OCR |

---

## 8. Shared Definition of Done (proposed)

- [ ] Acceptance criteria pass
- [ ] §3 authorization enforced and tested
- [ ] Migrations reviewed; seeds updated
- [ ] API documented under `docs/api/`
- [ ] Production path does not require `VITE_USE_MOCK_API=true`
- [ ] No hardcoded family/profile IDs in touched paths
- [ ] Loading/empty/error states handled
- [ ] Unit + API/integration tests for new server behavior
- [ ] Client tests or manual QA checklist updated
- [ ] Architecture diagrams updated when domain shape changes
- [ ] Logs omit secrets and raw dietary dumps
- [ ] Lint/format/CI pass
- [ ] Wire verdict/restriction types match backend for touched APIs
- [ ] `users.is_active` never used as profile activation
- [ ] Blocking open decisions resolved or story blocked

---

## Appendix A — Canonical API surface (target)

### Auth (UC18 / UC19)

- `POST /api/auth/register` — **live**
- `POST /api/auth/login` — **live** (JWT + refresh cookie)
- `POST /api/auth/refresh` — **live**
- `POST /api/auth/logout` — **live**
- `GET /api/auth/me` — **live** (JWT)

### Family / me (UC8–UC12, UC6, UC4, UC14)

- `GET|POST /api/families`, `GET /api/families/me` — **live**
- `GET /api/families/me/restriction-summary` — **live** (UC6)
- `GET /api/families/me/members|profiles`
- `POST /api/families/me/profiles` — dependant (UC9)
- `PUT|PATCH /api/families/me/profiles/{profileId}`
- `DELETE /api/families/me/members/{userId}`
- `GET /api/families/me/user-search`, `POST …/invitations`
- `GET|PUT /api/families/me/active-profile`
- `GET /api/families/me/scans` — **live** (UC4 family list; PRIMARY_ADMIN)
- `GET /api/families/me/scan-verdict-trends` — UC14

### Invitations (UC10)

- `GET /api/invitations/me`
- `POST /api/invitations/{token}/accept|decline`

### Profiles / scan (UC1–UC5, UC17, UC24)

- `GET /api/restrictions` — **live** (JWT)
- `GET|PUT /api/profiles/{profileId}/restrictions` — **live** (JWT + D3)
- `GET /api/scan/history/{profileId}` — **live** (JWT + ownership)
- `GET /api/profiles/{profileId}/recommendations`
- `GET /api/profiles/{profileId}/recommendation-history` — UC17
- `POST /api/scan/validate` — **live** (still transitional public)
- `POST /api/scan/assess` — **live** (JWT)
- `POST /api/scan/assess-ocr` (or assess with ingredient text) — UC24

### Admin (UC7, UC13, UC15–UC16, UC20–UC23)

- `GET /api/admin/consumer-trends`
- `GET /api/admin/consumer-trends/export` — UC22
- `GET /api/admin/users`, `PATCH /api/admin/users/{userId}/status` — UC13 status-only account management
- `GET /api/admin/usage-stats` — UC15
- `GET /api/admin/health-events` — UC16
- `GET /api/admin/ai-performance` — UC21
- `GET|PUT /api/admin/subscription-plans` — UC23
- `GET /api/admin/product-reports` — UC20 review

### Deprecate

- `GET /api/families/{familyId}/profiles` → `/api/families/me/profiles`

---

## Appendix B — Conflicts register

| ID | Conflict | Decision |
| --- | --- | --- |
| C1 | Role vocabularies | Membership vs platform roles (UC19-S2) |
| C2 | UC1 create without family | Prefer UC8 bootstrap |
| C3 | Mock immediate link | Replace with UC9 invite + UC10 |
| C4 | Profile vs account active | M4 vs `users.is_active` |
| C5 | Active profile | M3 + UC11 |
| C6–C8 | Codes / UNSAFE / PREFERENCE | Align clients to backend |
| C9 | Dependant without user | Profile-only |
| C10 | Open familyId IDOR | `/me` scoped APIs |

---

*End of Jira-ready backlog*

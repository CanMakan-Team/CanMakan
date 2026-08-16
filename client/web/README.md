# CanMakan Web

This React, TypeScript and Vite client contains two deliberately separate web
entry areas:

- USER portal with separate personal and optional Family Circle areas
- System Admin Portal

The implementation is an evolutionary Sprint 1 / Sprint 2 client. **Auth and
UC8 family create/`/me` always call the live Spring Boot API** (UC19 JWT
Bearer access token). Other family and analytics surfaces may still use browser mocks
when `VITE_USE_MOCK_API=true`.

Mascot PNGs are shared with Android under `client/shared/assets/mascot/`.
The web client imports them from that folder (Vite hashes the filenames).
Invitation emails still use the stable hosted path
`/email/canmakan-mascot-wave.png`, served from the same wave PNG. Do not copy
mascots into `public/`.

The browser favicon is the Android launcher at
`client/mobile/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`. Do not copy a
separate icon into `public/`.

## Selected Web features

The primary information architecture implements the latest selected scope:

| Feature | Sprint 1 Web implementation |
| --- | --- |
| 4 | Family Scan History with profile, verdict, period and completeness filters plus supplied-assessment detail |
| 6 | Dynamic Family Allergy & Dietary Requirement Summary |
| 7 | Anonymised Consumer Trends with accessible verdict and flagged-ingredient visualisations |
| 22 | UTF-8 CSV export of the currently selected anonymous UC7 aggregate dataset |
| 8 | Add Existing App User to Family |
| 9 | Create New Family Member Profile for a non-login dependant |
| 10 | Switch the active family assessment profile |
| 11 | Update an existing family dietary profile |
| 13 | User Accounts & Access with live status-only Suspend/Reactivate controls |

Earlier Figma concepts such as assessment review queues, product data issues,
ingredient aliases, logs, system health, AI reasoning review and application
usage statistics are retained only under **Future Features**. They are not
represented as completed Sprint 1 functions.

## Routes and role rules

Public/supporting routes:

- `/` — redirects to the USER entry at `/login`
- `/login` — platform USER email/password sign-in (live); `/family-login` redirects here
- `/register` — UC18 create account; `/family-register` redirects here
- `/me/setup-profile` — protected optional SELF-profile onboarding after registration
- `/system-admin-login` — System Admin email/password sign-in (live)
- `/access-denied` — role boundary notice

There is no public combined portal chooser. The User Portal and System login pages
are separate compositions and do not link to one another. The System
Administrator entry remains available only at its dedicated route. User login
links to `/register`; registration does not create a Family Circle.
Authenticated users with no circle may select `/family/circle` later. Family
members use the mobile app for daily scanning; household web tools are for
`PRIMARY_ADMIN` only.

Protected USER routes:

- `/family` — resolver: `PRIMARY_ADMIN` → dashboard; otherwise `/me`
- `/me` — personal desk (account, optional profile, create-circle if none)
- `/me/setup-profile` — optional standalone SELF-profile setup
- `/me/account` — account settings (works without a family)
- `/family/circle` — create Family Circle when membership is missing
- `/family/dashboard` — Family Circle dashboard (`PRIMARY_ADMIN`)
- `/family/members` — link, create, edit and active-profile flows (`PRIMARY_ADMIN`)
- `/family/restrictions` — dynamic family restriction summary (`PRIMARY_ADMIN`)
- `/family/history` — supplied scan-assessment history (`PRIMARY_ADMIN`)
- `/family/verdict-trends` — family verdict trends (`PRIMARY_ADMIN`)

Legacy `/family/personal`, `/family/setup-profile`, and `/family/account` redirect to `/me` equivalents.

Protected System Admin routes:

- `/system` — selected-feature dashboard
- `/system/trends` — anonymised Consumer Trends
- `/system/users` — User Accounts & Access
- `/system/future` — clearly labelled future/prototype-only concepts

`ROLE_APP_USER` cannot navigate to System pages and
`ROLE_SYSTEM_ADMIN` cannot use USER routes. There is no
Family/System role switch. The React guards improve navigation only:
**Spring Security must enforce the same access rules on every production API
endpoint.**

## Existing-user linking versus profile creation

These concepts are intentionally separate:

- **Add Existing App User** searches for an already registered user using an
  email address, shows only safe identifying information and confirms a family
  association.
- **Create New Family Member Profile** creates a child/dependant profile without
  a separate login account.

In mock mode, try:

- `jamie@example.com` — available to link
- `alicia@example.com` — already linked
- `pending@example.com` — pending invitation
- `error@demo.test` — controlled service-error demonstration

## Mock architecture and persistence

Pages depend on the shared API layer under `src/shared/api/`, including
`familyService.ts`, `adminService.ts` and `authService.ts`. Endpoint paths and
request/response types are isolated under `src/shared/api`. The services
delegate either to the mock repositories or the HTTP client, so switching
transport does not require page rewrites.

The frontend is organised around feature folders such as `src/features/family`,
`src/features/admin`, `src/features/analytics` and `src/features/auth`, with shared
UI and application wiring under `src/shared/ui` and `src/app/router`.

Mock repositories:

- simulate network latency;
- store mutable family data in `localStorage`;
- persist the active profile for the browser session;
- add linked users to the family list;
- create and update dependant profiles;
- immediately refresh dashboard/member/restriction views after mutations;
- return anonymised Consumer Trends through `adminService`;
- provide a controlled user-search error.

Clear `canmakan.mock.family` to reset mock family data. Authentication access
tokens and roles are memory-only; legacy `canmakan.session` data is removed and
never trusted during restoration.

## Environment

Copy `.env.example` to `.env` or `.env.local` if machine-specific settings are needed:

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK_API=false
```

A checked-in `.env` defaults to the live backend. The browser calls Spring on
port 8080 from Vite (`5173`); backend CORS must allow that origin (defaults under
`canmakan.cors.*`, overridable via `CANMAKAN_CORS_ALLOWED_ORIGINS` for deploy).
Network failures show “service is currently unreachable” in `apiClient` — that
includes CORS blocks, not only a down server.

Never put credentials or secrets in Vite environment variables.

## Install and run

```powershell
npm install
npm run dev
```

```powershell
npm test          # Vitest suites under src/test/
npm run test:watch
npm run verify    # typecheck + lint + tests
```

Quality commands:

```powershell
npm run lint
npm run typecheck
npm run test
npm run build
```

`npm run verify` runs typecheck, ESLint, and the Vitest suite. `npm run build`
also produces the Vite production bundle.

UC14 verdict-trend coverage lives in `src/test/features/analytics/VerdictTrendsPage.test.tsx` and `VerdictTrendChart.test.tsx`. CI uploads Vitest lcov to SonarCloud (`canmakan-web`); keep those tests in the same PR as page or chart changes so new-code coverage stays honest. Generated `src/styles/tokens.css` is omitted from Sonar analysis; `src/mocks` is omitted from coverage (pages and services stay in the gate).

## Sprint 1 demo flow

User Portal (family admin):

1. Open `/login` and sign in with a **PRIMARY_ADMIN** email/password, or create
   an account at `/register` then create a Family Circle at `/family/circle`.
2. Confirm there is no System Admin navigation. Members and users without a
   circle land on `/me` and do not see Family Members.
3. Open **Family Members** and choose **Add Existing App User**.
4. Search `jamie@example.com`, confirm the link and verify Jamie appears.
5. Choose **Create New Profile** and create Chloe as a Child.
6. Add Peanut Allergy and Lactose Intolerance; verify the summary updates.
7. Select Chloe as the active assessment profile.
8. Edit Chloe and add Low Sugar; verify the profile and summary refresh.
9. Open **Family Scan History** and filter by profile.

System Admin Portal:

1. Sign out and open `/system-admin-login`.
2. Confirm there is no Family Admin navigation.
3. Open **Consumer Trends** and inspect the anonymised charts and table values.
4. Generate the CSV report and confirm its date range and category match the current view.
5. Open **User Accounts & Access**, filter by email, role or status, then suspend or reactivate an account with a reason.
6. Confirm the status feedback and refreshed filtered account list.

## Implemented backend contracts (UC8 / UC18)

Auth (live DB, UC19 JWT):

- `POST /api/auth/register` — email, password → active USER account only
- `POST /api/auth/login` — email, password → `AuthResponse` (access JWT + user) + refresh cookie
- `POST /api/profiles/me` — authenticated optional SELF profile + restrictions
- `POST /api/auth/refresh` — rotates the HttpOnly refresh session and returns a new access response
- `POST /api/auth/logout` — clears refresh cookie / session server-side
- `DELETE /api/auth/account` — soft-deactivates the signed-in account only
- `GET /api/auth/me` — authoritative account id, email, platform role and active status

Registration and authentication remain separate backend operations. After
successful account-only registration, the browser calls the normal login path,
keeps the access credential in memory and opens optional dietary setup. Profile
Name is entered on `/me/setup-profile` when the user creates a SELF profile;
**Set Up Later** makes no profile request. If automatic
login fails, the account remains and normal login is offered with email prefilled.
The family navigation keeps `/me/setup-profile` available so an authenticated
user can complete skipped setup later.
Save and Set Up Later finish at `/me`, which performs no family
creation or membership request. `/family` checks optional membership only to
route family admins to `/family/dashboard`; a member or 404 routes to `/me`.
The Family Circle form opens only from the explicit `/family/circle` action.
Startup restoration still uses the HttpOnly refresh cookie and verifies
`/api/auth/me` before protected pages render.
Cookie-changing login, refresh, and logout operations require Web Locks so all
same-origin tabs share one mutation boundary. Cross-tab invalidation messages
contain coordination metadata only; no credential or account data is persisted.
Only safe GET/HEAD/OPTIONS requests may perform one automatic authentication
recovery. Mutating requests are never replayed automatically.

Family (caller id from Bearer JWT):

```text
POST  /api/families
GET   /api/families/me
```

System Admin account management (caller id from Bearer JWT):

```text
GET   /api/admin/users?query={query}&role={USER|ADMIN}&active={true|false}
PATCH /api/admin/users/{userId}/status
```

Web: `FamilyMeGate` protects family-only routes. The create form is available
only through the explicit `/family/circle` entry when `/me` is 404. Details:
[`docs/api/families.md`](../../docs/api/families.md).

## Proposed backend contracts requiring confirmation

The frontend isolates these proposal paths; most are **not** implemented on
Spring Boot yet (UC9–UC12 / admin / analytics):

```text
GET   /api/families/me/members
GET   /api/families/me/user-search?email={email}
POST  /api/families/me/members/link
POST  /api/families/me/profiles
GET   /api/families/me/profiles/{profileId}
PUT   /api/families/me/profiles/{profileId}
PUT   /api/families/me/active-profile
GET   /api/families/me/restriction-summary
GET   /api/families/me/scans
GET   /api/admin/consumer-trends
```

## Safety and architecture boundaries

React displays scan verdicts as `SAFE` | `WARNING` | `UNSAFE` exactly as supplied.
It does not interpret ingredients, calculate a food-safety verdict, upgrade incomplete data to Safe
or override the backend decision. The intended architecture remains:

- Agentic AI: structured ingredient-level interpretation;
- Spring Boot Rule Engine: final verdict;
- React: display and interaction only.

Mock alternatives and explanations are display data, not medical advice or
food-safety guarantees.

## Known limitations

- Live register/login and UC8 create/`/me` use the database with JWT Bearer
  identity on family APIs.
- Members/invite/history and analytics pages may still be mock when
  `VITE_USE_MOCK_API=true` (default is `false`).
- Backend `spring.sql.init.mode=always` reseeds and drops newly registered users
  on restart.
- Scan records, recommendations and aggregate trends may still be mock outputs.
- Spring Security filter chain / JWT remain UC19.

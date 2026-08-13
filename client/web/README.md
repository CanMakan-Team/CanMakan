# CanMakan Web

This React, TypeScript and Vite client contains two deliberately separate web
portals:

- Family Admin Portal
- System Admin Portal

The implementation is an evolutionary Sprint 1 / Sprint 2 client. **Auth and
UC8 family create/`/me` always call the live Spring Boot API** (UC19 JWT
Bearer access token). Other family and analytics surfaces may still use browser mocks
when `VITE_USE_MOCK_API=true`.

## Selected Web features

The primary information architecture implements the latest selected scope:

| Feature | Sprint 1 Web implementation |
| --- | --- |
| 4 | Family Scan History with profile, verdict, period and completeness filters plus supplied-assessment detail |
| 6 | Dynamic Family Allergy & Dietary Requirement Summary |
| 7 | Anonymised Consumer Trends with accessible verdict and flagged-ingredient visualisations |
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

- `/` — redirects to the Family Admin entry at `/family-login`
- `/family-login` — Family Admin email/password sign-in (live)
- `/family-register` — UC18 create account (same family login theme)
- `/system-admin-login` — System Admin email/password sign-in (live)
- `/access-denied` — role boundary notice

There is no public combined portal chooser. The Family and System login pages
are separate compositions and do not link to one another. The System
Administrator entry remains available only at its dedicated route. Family login
links to `/family-register`; registration does not create a family circle
(UC8 empty-state does).

Protected Family Admin routes:

- `/family` — selected-feature dashboard
- `/family/members` — link, create, edit and active-profile flows
- `/family/restrictions` — dynamic family restriction summary
- `/family/history` — supplied scan-assessment history
- `/family/account` — authoritative account, family-role and SELF-profile information

Protected System Admin routes:

- `/system` — selected-feature dashboard
- `/system/trends` — anonymised Consumer Trends
- `/system/users` — User Accounts & Access
- `/system/future` — clearly labelled future/prototype-only concepts

`ROLE_FAMILY_ADMIN` cannot navigate to System pages and
`ROLE_SYSTEM_ADMIN` cannot use `/family` as a Family Admin. There is no
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

## Sprint 1 demo flow

Family Portal:

1. Open `/family-login` and sign in with a registered email/password, or create
   an account at `/family-register`.
2. Confirm there is no System Admin navigation.
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
4. Open **User Accounts & Access**, filter by email, role or status, then suspend or reactivate an account with a reason.
5. Confirm the status feedback and refreshed filtered account list.

## Implemented backend contracts (UC8 / UC18)

Auth (live DB, UC19 JWT):

- `POST /api/auth/register` — name, email, password → user + SELF profile
- `POST /api/auth/login` — email, password → `AuthResponse` (access JWT + user) + refresh cookie
- `POST /api/auth/refresh` — rotates the HttpOnly refresh session and returns a new access response
- `POST /api/auth/logout` — clears refresh cookie / session server-side
- `GET /api/auth/me` — authoritative account id, email, platform role and active status

Registration and authentication are separate: after successful registration,
the browser returns to `/family-login` without issuing an access token. On login,
the access credential stays in memory; startup restoration uses the HttpOnly
refresh cookie and verifies `/api/auth/me` before protected pages render.
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

Web: `FamilyMeGate` + `CreateFamilyCirclePage` when `/me` is 404. Details:
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

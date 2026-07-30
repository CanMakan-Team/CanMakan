# CanMakan Web — Sprint 1 Evolutionary Prototype

This React, TypeScript and Vite client contains two deliberately separate web
portals:

- Family Admin Portal
- System Admin Portal

The implementation is an evolutionary Sprint 1 prototype. It uses asynchronous,
mutable browser mock repositories by default because the proposed Spring Boot
contracts are not yet confirmed. Mock mode is clearly labelled in the UI and is
not production authentication or backend integration.

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
| 12 | User Accounts & Access with mock confirmations and audit entries |

Earlier Figma concepts such as assessment review queues, product data issues,
ingredient aliases, logs, system health, AI reasoning review and application
usage statistics are retained only under **Future Features**. They are not
represented as completed Sprint 1 functions.

## Routes and role rules

Public/supporting routes:

- `/` — redirects to the Family Admin entry at `/family-login`
- `/family-login` — Family Admin Prototype Login
- `/system-admin-login` — System Admin Prototype Login
- `/access-denied` — role boundary notice

There is no public combined portal chooser. The Family and System login pages
are separate compositions and do not link to one another. The System
Administrator entry remains available only at its dedicated route.

Protected Family Admin routes:

- `/family` — selected-feature dashboard
- `/family/members` — link, create, edit and active-profile flows
- `/family/restrictions` — dynamic family restriction summary
- `/family/history` — supplied scan-assessment history
- `/family/account` — supporting mock-session information

Protected System Admin routes:

- `/system` — selected-feature dashboard
- `/system/trends` — anonymised Consumer Trends
- `/system/users` — User Accounts & Access
- `/system/future` — clearly labelled future/prototype-only concepts
- `/family-test` — read-only Family Portal test access that preserves
  `ROLE_SYSTEM_ADMIN`

`ROLE_FAMILY_ADMIN` cannot navigate to System pages and
`ROLE_SYSTEM_ADMIN` cannot use `/family` as a Family Admin. There is no
Family/System role switch. The React guards improve prototype navigation only:
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
- store mutable family and access data in `localStorage`;
- persist the active profile for the browser session;
- add linked users to the family list;
- create and update dependant profiles;
- immediately refresh dashboard/member/restriction views after mutations;
- update user roles/statuses and record mock audit entries;
- return anonymised Consumer Trends through `adminService`;
- provide a controlled user-search error.

Clear the `canmakan.mock.family`, `canmakan.mock.admin` and `canmakan.session`
local-storage keys to reset the prototype.

## Environment

Copy `.env.example` to `.env.local` if machine-specific settings are needed:

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK_API=true
```

Set `VITE_USE_MOCK_API=false` only when a compatible backend is available. The
HTTP client is prepared to attach `Authorization: Bearer <token>` when a later
authenticated session supplies an access token. A `401` clears the invalid
session; a `403` is kept distinct as an authorisation error; network errors
retain the current page and provide usable feedback.

Never put credentials or secrets in Vite environment variables.

## Install and run

```powershell
npm install
npm run dev
```

Quality commands:

```powershell
npm run lint
npm run typecheck
npm run build
```

No automated frontend test runner was present when this Sprint 1 prototype was
implemented.

## Sprint 1 demo flow

Family Portal:

1. Open `/family-login` and enter using Prototype Login.
2. Confirm there is no System Admin navigation.
3. Open **Family Members** and choose **Add Existing App User**.
4. Search `jamie@example.com`, confirm the link and verify Jamie appears.
5. Choose **Create New Profile** and create Chloe as a Child.
6. Add Peanut Allergy and Dairy Free; verify the summary updates.
7. Select Chloe as the active assessment profile.
8. Edit Chloe and add Low Sugar; verify the profile and summary refresh.
9. Open **Family Scan History** and filter by profile.

System Admin Portal:

1. Sign out and open `/system-admin-login`.
2. Confirm there is no Family Admin navigation.
3. Open **Consumer Trends** and inspect the anonymised charts and table values.
4. Open **User Accounts & Access**, filter by role and manage an account.
5. Confirm the access change and inspect the mock audit record.
6. Open **Family Portal — Test Access**, confirm the session remains
   `ROLE_SYSTEM_ADMIN`, then return to the System portal.

## Proposed backend contracts requiring confirmation

The frontend isolates these proposal paths; this task does not implement or
confirm the Spring Boot endpoints:

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
GET   /api/admin/users
PATCH /api/admin/users/{userId}/access
```

## Safety and architecture boundaries

React displays final `SAFE`, `WARNING`, `AVOID` or `INCOMPLETE` values exactly
as supplied by the service. It does not interpret ingredients, calculate a
food-safety verdict, upgrade incomplete data to Safe or override the backend
decision. The intended architecture remains:

- Agentic AI: structured ingredient-level interpretation;
- Spring Boot Rule Engine: final verdict;
- React: display and interaction only.

Mock alternatives and explanations are display data, not medical advice or
food-safety guarantees.

## Known limitations

- Prototype login creates a browser-only mock session; there is no real JWT
  issuance, password verification or production security.
- State is local to one browser and has no multi-user consistency.
- Reporting-period and platform controls demonstrate the service-ready UI but
  the fixed mock aggregate dataset is intentionally limited and marked partial.
- Scan records, recommendations and aggregate trends are supplied mock outputs.
- Spring Boot, MySQL, Open Food Facts, Agentic AI, Rule Engine and ML services
  are not integrated by this frontend task.

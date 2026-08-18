# Web Code Quality Assessment

Living document for the React + Vite web client under `client/web`.  
Update the **Changes checklist** when work lands; keep findings status notes current.

**Location:** `docs/code-quality/WEB-CODE-QUALITY.md` (linked from [`docs/code-quality/README.md`](README.md))  
**Scope:** `client/web` (~168 TypeScript/TSX source files plus Vitest and Playwright tests)  
**Out of scope:** Spring Boot, MySQL schema, Android. This client *calls* those APIs; proposed refactors must **not** change API contracts or database behaviour unless a later, separately approved task says otherwise.

**Status of this document:** Assessment and refactoring plan. **REF-001–REF-019** are implemented in `client/web` (see checklist). F-WEB-06 (client-side full-list fetches) and F-WEB-10 (navigation-only RBAC) remain documented with no code change.

---

## 1. Scope Reviewed

| Area | Paths |
| --- | --- |
| App shell and routing | `client/web/src/app/`, `client/web/src/main.tsx`, `client/web/src/pages/` |
| Features | `client/web/src/features/auth`, `account`, `family`, `admin`, `analytics` |
| Shared API / UI / validation | `client/web/src/shared/` |
| Mocks | `client/web/src/mocks/` |
| Styles | `client/web/src/styles/app.css`, generated `tokens.css` |
| Tests | `client/web/src/test/`, `client/web/e2e/` |

Largest page modules (approximate non-blank lines at review time):

| File | Lines | Role |
| --- | ---: | --- |
| `features/analytics/ConsumerTrendsPage.tsx` | ~922 | System-admin consumer analytics |
| `features/admin/SystemHealthPage.tsx` | ~514 | UC16 health snapshot |
| `features/analytics/UsageStatisticsPage.tsx` | ~505 | UC15 usage statistics |
| `features/account/pages/PersonalHomePage.tsx` | ~467 | User desk / home |
| `features/admin/UserAccessPage.tsx` | ~451 | Account suspend/activate |
| `features/admin/AdminScanFeedbackPage.tsx` | ~415 | Scan feedback moderation |
| `features/analytics/VerdictTrendsPage.tsx` | ~412 | Family verdict trend (client aggregate) |
| `features/family/pages/FamilyRestrictionSummaryPage.tsx` | ~411 | Household restriction matrix |
| `features/family/pages/FamilyScanHistoryPage.tsx` | ~369 | Family scan history |
| `features/family/pages/FamilyMembersPage.tsx` | ~314 | Roster admin |

Compatibility re-exports (`family/pages/PersonalHomePage.tsx`, `SelfProfileSetupPage.tsx`, `FamilyAccountPage.tsx`, `family/api/selfProfileApiService.ts`) are shims, not duplicated implementations.

---

## 2. Architecture Overview

The web client is **feature-first**. It does **not** have Spring Controller / Service / DAO packages. The equivalent layering is:

```text
UI (pages, components, layout)
    ↓
Feature lib (pure helpers: dates, roles, display, matrix)
    ↓
ApiService / authService (HTTP adapters)
    ↓
apiClient (fetch, 401 refresh, credentials: include)
    ↓
Spring Boot (authoritative business rules and persistence)
```

When `VITE_USE_MOCK_API=true`, some adapters branch to `src/mocks/` (a **fake repository**, not a second backend).

```text
Page / component
    → feature lib
    → *ApiService
         ├─ apiRequest → backend
         └─ mock*Repository (dev/demo only)
```

**Frontend responsibilities (as assessed):** presentation, UI state, client-side validation (email/password/profile name), calling APIs, displaying errors.  
**Not frontend responsibilities:** persistence, authorization decisions, dietary evaluation, scan verdict calculation.

**Strengths to keep**

- In-memory access token (`authSessionStore`); refresh cookie; legacy `localStorage` session is cleared and not trusted.
- Shared `apiClient` with safe GET retry after refresh, session-mutation lock, and user-facing `ApiError` messages.
- Route guards: `ProtectedRoute` (role) and `FamilyMeGate` (PRIMARY_ADMIN). Comments already state these are navigation-only.
- Thin HTTP objects: `authService`, `adminService`, `familyApiService`, analytics and health services.
- Account vs family folders with re-exports instead of copy-paste pages.
- Broad Vitest coverage (auth, apiClient, family pages, analytics, admin).

**Layer mapping for assessors**

| Assessor term | Web equivalent | Must not contain |
| --- | --- | --- |
| Frontend / UI | Pages, components, `PortalLayout` | Persistence, server authorization |
| Controller | Route components + thin page `load()` | Domain aggregation, payload contracts |
| Service | `*ApiService`, `authService` | JSX, chart drawing |
| DAO / Repository | `mocks/*Repository` (fake only); real data is backend | UI concepts, dietary product rules |

Do not invent Spring-style `controller/` folders on the web client.

---

## 3. Assessor Criteria Evaluation

| Criterion | Current Assessment | Main Issues | Priority |
| --------------------------- | ------------------ | ----------- | -------- |
| Readability | Fair (6/10) | 500–980 line pages; domain rules next to JSX; mixed quotes on Consumer Trends; hardcoded hex | P1 |
| Maintainability | Weak–fair (5.5/10) | Repeated load/debounce/paging; per-method `useMockApi` branches; mock-only custom events; ~5300-line `app.css` | P1 |
| Layer Separation | Fair (6/10) | Aggregation, response validation, CSV, dairy equivalence live in pages | P1 |
| Low Coupling | Fair (6/10) | Family service tightly bound to mocks; admin feedback imports family catalog service; dashboard fans out five APIs | P2 |
| Reasonable Method Length | Weak–fair (5/10) | File-level god components; matrix `useMemo` and chart functions hard to name in one sentence | P1 |
| Business Logic Distribution | Fair (6/10) | Verdict buckets, dairy matching, payload checks, CSV assembly in UI | P1 |
| Complex Method Refactoring | Weak (5/10) | Consumer Trends, verdict `aggregate`, restriction matrix, Personal Home, health/usage result trees | P1 |

---

## 4. Quality Scorecard

Scores are for the **current** web client. Target is after a later, separately approved refactor. “After implementation” stays blank until that work lands.

| Category | Current | Target | After implementation |
| --- | ---: | ---: | ---: |
| Readability | 6/10 | 8/10 | 8/10 |
| Maintainability | 5.5/10 | 8/10 | 7.5/10 |
| Layer Separation | 6/10 | 8/10 | 8/10 |
| Low Coupling | 6/10 | 8/10 | 7.5/10 |
| Method Complexity | 5/10 | 8/10 | 7.5/10 |
| Testability | 7/10 | 8/10 | 8/10 |
| Security | 7.5/10 | 8/10 | 8/10 |
| Correctness | 6/10 | 8/10 | 8/10 |
| Overall | 6/10 | 8/10 | 7.5/10 |

Do not inflate: auth and tests are relatively strong; page-level complexity and one date-bucketing defect pull the overall score down.

---

## 5. Critical Findings

There is **no CRITICAL** web-only security or data-integrity defect (no `dangerouslySetInnerHTML`, no access token persisted in `localStorage`, no hard-coded secrets in source). Highest items first.

### Findings table

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| F-WEB-01 | HIGH | Verdict trend day keys mix UTC `toISOString` with local midnight buckets | Done |
| F-WEB-02 | HIGH | `ConsumerTrendsPage.tsx` is a ~980-line mixed-layer module | Done |
| F-WEB-03 | HIGH | `familyApiService` repeats mock vs HTTP on every method | Done |
| F-WEB-04 | HIGH | Restriction matrix dairy/severity rules embedded in page JSX | Done |
| F-WEB-05 | MEDIUM | Admin/health/usage loads lack request-generation guards | Done |
| F-WEB-06 | MEDIUM | Full user list and full scan history fetched then filtered in the browser | Documented — no new API in this plan |
| F-WEB-07 | MEDIUM | `canmakan:family-data-changed` only dispatched from mocks, listened on live pages | Done |
| F-WEB-08 | MEDIUM | Duplicated CSV download, hex palettes, debounce/paging, period options | Done |
| F-WEB-09 | LOW | Always-on ngrok header; unused `VITE_FIREBASE_API_KEY` / likely unused `firebase` package | Done |
| F-WEB-10 | INFO | Client role gates are navigation-only (correct); do not add a client policy engine | Documented — no change |

### Finding detail

#### F-WEB-01

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `client/web/src/features/analytics/VerdictTrendsPage.tsx` |
| Class/component | `VerdictTrendsPage` |
| Method/function | `isoDate()`, `aggregate()` |
| Current problem | `isoDate` uses `date.toISOString().slice(0, 10)` (UTC calendar day). Bucket keys are built from local `setHours(0, 0, 0, 0)` then passed through `isoDate`. In Singapore (UTC+8), local midnight is the previous UTC date, so “today” scans often miss the intended bucket. Tests in `VerdictTrendsPage.test.tsx` encode this UTC `bucketIso` helper. |
| Why it matters | Family admins can see wrong daily Safe/Warning/Unsafe counts. Consumer Trends already uses `Asia/Singapore` helpers in `consumerTrendsDateRange.ts`. |
| Proposed solution | Extract `aggregateFamilyVerdictTrend` and key days with Singapore calendar dates (same approach as `feedbackTimestamps.ts` / `singaporeToday`). Rewrite tests to use explicit SGT instants. |
| Affected layer | UI → should be feature lib |
| Risk | MEDIUM (visible chart change = correctness fix) |
| Blast radius | `VerdictTrendsPage`, its tests, CSV export rows |

#### F-WEB-02

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `client/web/src/features/analytics/ConsumerTrendsPage.tsx` |
| Class/component | `ConsumerTrendsPage` and nested chart functions |
| Method/function | Module as a whole; `prepareConsumerTrendsResponse`, `DailyActivityChart`, `usePagedItems` |
| Current problem | Fetch, range validation, payload sanitizing, export, formatters, paging hook, and six chart sections live in one file. Quote style differs from the rest of the repo. |
| Why it matters | Another developer cannot explain or safely change one chart without scrolling mixed concerns. |
| Proposed solution | Split by cohesive units (normalize, format, paging, each chart). Page keeps query state, load effect, toolbar, composition. |
| Affected layer | UI (split); validation → API adapter / lib |
| Risk | MEDIUM |
| Blast radius | Consumer Trends tests and CSS class names (keep class names stable) |

#### F-WEB-03

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `client/web/src/features/family/api/familyApiService.ts` |
| Class/component | `familyApiService` |
| Method/function | Nearly every method (`getMembers`, `createProfile`, …) |
| Current problem | Each method independently branches `useMockApi ? mockFamilyRepository : apiRequest`. |
| Why it matters | Adding an endpoint requires two implementations and two branch sites; easy to miss a mock path. |
| Proposed solution | Single facade: if mock mode, return `mockFamilyRepository`; else HTTP implementation with the same method names. |
| Affected layer | ApiService ↔ mock repository |
| Risk | LOW if behaviour is unchanged |
| Blast radius | Family API tests, mock repository |

#### F-WEB-04

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `client/web/src/features/family/pages/FamilyRestrictionSummaryPage.tsx` |
| Class/component | `FamilyRestrictionSummaryPage` |
| Method/function | `restrictionRows` `useMemo`, `isHouseholdRestriction`, `selectionTone`, dairy code sets |
| Current problem | Household matrix grouping (including `DAIRY` / `LACTOSE_*` equivalence) and severity tones are mixed with grid rendering. |
| Why it matters | A small dietary-display rule change requires editing a large page; hard to unit-test without the DOM. |
| Proposed solution | `restrictionMatrix.ts` pure functions + tests; page only filters and renders. Do **not** move this to Spring without a product decision. |
| Affected layer | UI → feature lib |
| Risk | MEDIUM (must preserve dairy collapsing) |
| Blast radius | Restriction summary page tests |

#### F-WEB-05

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `SystemHealthPage.tsx`, `UsageStatisticsPage.tsx`, `UserAccessPage.tsx` (and similar `load` callbacks) |
| Class/component | Page `load` |
| Method/function | `load` |
| Current problem | Consumer Trends uses `latestLoadRequest`; several other pages do not. Fast filter or window changes can apply a stale response. |
| Why it matters | UI can show the wrong period or filter after a race. |
| Proposed solution | Request-id or cancelled-flag, same pattern as Consumer Trends / Invite landing. |
| Affected layer | UI state |
| Risk | LOW |
| Blast radius | Existing page tests (may need `waitFor` already present) |

#### F-WEB-06

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `UserAccessPage.tsx`, `SystemDashboardPage.tsx`, `FamilyScanHistoryPage.tsx`, `VerdictTrendsPage.tsx` |
| Class/component | Listed pages |
| Method/function | `load` / `loadHistory` / `getUsers` / `getScanHistory` |
| Current problem | Admin users API returns a full array; dashboard counts from that list. Family history and verdict trend download all scans then filter/aggregate in the browser. |
| Why it matters | Identifiable performance risk as data grows. |
| Proposed solution | Document only in this web-only plan. Do **not** invent pagination APIs here. Keep client filters. Coordinate with backend later if lists grow. |
| Affected layer | Frontend ↔ Backend (future) |
| Risk | N/A this pass |
| Blast radius | Would require API + Android if pagination is added later |

#### F-WEB-07

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `mockFamilyRepository.ts`; listeners in `FamilyDashboardPage.tsx`, `FamilyRestrictionSummaryPage.tsx` |
| Class/component | Mock repo / those pages |
| Method/function | `dispatchEvent('canmakan:family-data-changed')` / `addEventListener` |
| Current problem | Event is only fired from mocks but live pages always listen. |
| Why it matters | Suggests live pages refresh on roster changes; they do not, except under mock API. Hidden coupling and false mental model. |
| Proposed solution | Gate listeners to mock mode, or stop listening and rely on explicit `reload` / navigation (preferred for production). |
| Affected layer | UI ↔ mock DAO |
| Risk | LOW for production (listeners are currently no-ops unless mocks fire) |
| Blast radius | Dashboard and restriction tests under mock |

#### F-WEB-08

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `UsageStatisticsPage.tsx`, `VerdictTrendsPage.tsx`, `consumerTrendsReport.ts`; health/usage/verdict colour constants; User Access vs Scan Feedback filter helpers |
| Class/component | Multiple |
| Method/function | CSV `createObjectURL` blocks; hex palettes; `filtersEqual` / debounce |
| Current problem | Same download and admin-chrome patterns copied. |
| Why it matters | Bug fixes (revoke object URL, filename) must be repeated. |
| Proposed solution | Shared `downloadTextFile`; CSS variables for section colours; extract debounce/paging only when touching those pages. |
| Affected layer | UI / shared lib |
| Risk | LOW |
| Blast radius | Export tests |

#### F-WEB-09

| Field | Value |
| --- | --- |
| Severity | LOW |
| File | `shared/api/apiClient.ts`, `vite-env.d.ts`, `package.json` |
| Class/component | `executeRequest` |
| Method/function | header set `ngrok-skip-browser-warning` |
| Current problem | Header always sent. `VITE_FIREBASE_API_KEY` is typed but unused. `firebase` dependency appears unused in `src` (distribution URL is a plain env string + QR). |
| Why it matters | Hygiene and bundle/dep noise; ngrok header is unnecessary in production. |
| Proposed solution | Gate header behind env; confirm then remove unused dep and env type. |
| Affected layer | Shared API / build |
| Risk | LOW (do not break tunnel workflows without checking) |
| Blast radius | `apiClient` tests, CI env |

#### F-WEB-10

| Field | Value |
| --- | --- |
| Severity | INFO |
| File | `ProtectedRoute.tsx`, `FamilyMeGate.tsx` |
| Class/component | Route guards |
| Method/function | role / PRIMARY_ADMIN checks |
| Current problem | None as a defect. Client checks improve navigation only. |
| Why it matters | Assessors may flag “auth only on the client.” The code already documents the correct model; backend must remain authoritative. |
| Proposed solution | No change. Do not add a second client authorization framework. |
| Affected layer | Frontend navigation |
| Risk | N/A |
| Blast radius | None |

---

## 6. Long / Complex Methods

Every item below is in the refactoring plan. Extraction must be by **cohesive responsibility**, not numbered steps.

### `ConsumerTrendsPage` module

| Field | Detail |
| --- | --- |
| Location | `client/web/src/features/analytics/ConsumerTrendsPage.tsx` |
| Current responsibilities | Query/period/category state; debounced fetch; `prepareConsumerTrendsResponse`; export; date/number format; `ConsumerTrendsResult`; `DailyActivityChart`; `ProductRankingChart`; `CategoryOverviewChart`; `ConcernBars`; `OutcomeMix`; `usePagedItems`; `ListPageNav` |
| Why too complex | Mixed layers; cannot explain the file in one sentence; charts untestable without the page |
| Proposed decomposition | `prepareConsumerTrendsResponse` → normalize/API; formatters → `consumerTrendsFormat.ts`; paging → `listPaging`; each chart → `features/analytics/components/`; page = state + load + toolbar + compose |
| Assessment criteria | Readability, method length, layer separation, testability |
| Risk | MEDIUM |

```text
ConsumerTrendsPage()
 ├── loadConsumerTrends()          (page)
 ├── prepareConsumerTrendsResponse()  (lib / api service)
 ├── downloadConsumerTrendsReport()   (already separate)
 ├── DailyActivityChart()
 ├── OutcomeMix()
 ├── ProductRankingChart()
 ├── CategoryOverviewChart()
 └── ConcernBars()
```

### `VerdictTrendsPage.aggregate` / `isoDate` / `handleExport`

| Field | Detail |
| --- | --- |
| Location | `client/web/src/features/analytics/VerdictTrendsPage.tsx` |
| Current responsibilities | Fetch all family scans; local-midnight window; UTC day keys; Safe/Warning/Unsafe counts; donut gradient; CSV blob; filters; chart |
| Why too complex | Domain aggregation in the page; **incorrect date keys** (F-WEB-01) |
| Proposed decomposition | `aggregateFamilyVerdictTrend(records, periodDays, now)` in `verdictTrendAggregate.ts` using Singapore day keys; CSV via `downloadTextFile`; page loads and presents |
| Assessment criteria | Correctness, business-logic distribution, method length |
| Risk | MEDIUM (tests today lock UTC `bucketIso`) |

```text
VerdictTrendsPage()
 ├── loadScanHistory()
 ├── aggregateFamilyVerdictTrend()
 ├── sharePercents()              (already exported; keep)
 └── downloadVerdictTrendCsv()
```

### `FamilyRestrictionSummaryPage` matrix builders

| Field | Detail |
| --- | --- |
| Location | `client/web/src/features/family/pages/FamilyRestrictionSummaryPage.tsx` |
| Current responsibilities | Load summary; dairy-family matching; row construction; severity tones; filter chips; reference accordion; grid render |
| Why too complex | Domain mapping mixed with layout |
| Proposed decomposition | `buildRestrictionRows`, `isHouseholdRestriction`, `selectionTone` → `restrictionMatrix.ts` |
| Assessment criteria | Layer separation, business-logic distribution, testability |
| Risk | MEDIUM |

```text
FamilyRestrictionSummaryPage()
 ├── loadSummary()
 ├── buildRestrictionMatrix(data)
 └── renderGrid()
```

### `PersonalHomePage`

| Field | Detail |
| --- | --- |
| Location | `client/web/src/features/account/pages/PersonalHomePage.tsx` |
| Current responsibilities | Session/family context; catalog + self profile fetch; member count; recent scans; tester notice and install flags in `localStorage`; greeting; QR / Firebase App Distribution URL; layout |
| Why too complex | Multiple unrelated data loads and banners in one component |
| Proposed decomposition | `usePersonalHomeData`; `MobileAppBanner`; `RecentScansList`; page composes |
| Assessment criteria | Method length, readability, coupling (effects vs UI) |
| Risk | MEDIUM |

```text
PersonalHomePage()
 ├── usePersonalHomeData()
 ├── MobileAppBanner()
 └── RecentScansList()
```

### `SystemHealthPage` / `UsageStatisticsPage` result trees

| Field | Detail |
| --- | --- |
| Location | `SystemHealthPage.tsx` (`SystemHealthResult` and below); `UsageStatisticsPage.tsx` (`UsageStatisticsResult`, `SectionPanel`, charts) |
| Current responsibilities | Page `load` is already short. Remaining hundreds of lines are section UI plus duplicated hex `BLUE`/`GREEN`/`AMBER`/`RED` and `SectionPanel`. |
| Why too complex | File length and duplicated chrome, not one cyclomatic `load` |
| Proposed decomposition | `SystemHealthResult.tsx`, `UsageStatisticsResult.tsx`; CSS variables instead of hex copies |
| Assessment criteria | Readability, maintainability, low coupling (tokens) |
| Risk | LOW–MEDIUM |

### `UserAccessPage` / `AdminScanFeedbackPage`

| Field | Detail |
| --- | --- |
| Location | `features/admin/UserAccessPage.tsx`, `AdminScanFeedbackPage.tsx` |
| Current responsibilities | URL-initialized filters, debounce, pagination reset during render, list load, action modals, notices, (feedback) restriction catalog refetch on every load |
| Why too complex | Many UI workflows in one component; catalog refetch is extra coupling/work |
| Proposed decomposition | `useDebouncedFilters`; `usePaginationReset`; extract modals; load catalog once on feedback |
| Assessment criteria | Maintainability, method length, coupling |
| Risk | MEDIUM |

### `SelfProfileSetupPage` payload rules

| Field | Detail |
| --- | --- |
| Location | `features/account/pages/SelfProfileSetupPage.tsx` |
| Current responsibilities | Catalog + existing profile load; “touched ids vs persisted severity” so PREFERENCE from other clients is not overwritten; save |
| Why too complex | Persistence mapping is a business rule inside the form |
| Proposed decomposition | `buildRestrictionPayload(selected, persisted, touched)` + unit tests |
| Assessment criteria | Business-logic distribution, testability |
| Risk | MEDIUM (must not change dietary PUT/POST body) |

```text
SelfProfileSetupPage()
 ├── loadCatalogAndExisting()
 ├── buildRestrictionPayload()
 └── saveProfile()
```

### `familyApiService` object

| Field | Detail |
| --- | --- |
| Location | `features/family/api/familyApiService.ts` |
| Current responsibilities | HTTP mapping plus per-method mock branch |
| Why too complex | Repeated branching, not one long method |
| Proposed decomposition | Single mock vs HTTP facade with identical method names |
| Assessment criteria | Maintainability, low coupling |
| Risk | LOW |

---

## 7. Layer Separation Problems

Web has no Controller or DAO in the Spring sense. Violations below use the assessor vocabulary mapped in section 2.

```text
Frontend → Backend business logic violation
```

- `VerdictTrendsPage.aggregate` implements reporting buckets that belong in a pure lib (and conceptually match server analytics, but this endpoint is family scan history by design).
- `FamilyRestrictionSummaryPage` dairy equivalence is a dietary **display** rule in the UI.
- `prepareConsumerTrendsResponse` is API-contract validation inside a page.

**Correction:** move to feature `lib` or the API adapter. Do not duplicate Spring dietary evaluation.

```text
UI → Networking / orchestration violation
```

- `PersonalHomePage` runs three independent fetch effects inline.
- `SystemDashboardPage` fans out five `Promise.allSettled` calls and derives counts in the component body.

**Correction:** `usePersonalHomeData` / `loadDashboardSnapshot` hooks or small modules. Still the UI layer, but not mixed with markup.

```text
ApiService → DAO responsibility issue
```

- `familyApiService` (and usage/health/consumer services) mix HTTP with mock repositories on every call.

**Correction:** one gateway at the service boundary.

```text
Admin UI → family API
```

- `AdminScanFeedbackPage` imports `selfProfileApiService.getCatalog` (family/account dietary catalog).

**Correction:** optional shared `restrictionsApiService` only if naming confusion is real. Catalog is a legitimate shared read. Do not add an interface for its own sake.

```text
Controller → Service / Controller → DAO
```

- Not applicable as Java packages. Page `load` functions that only fetch and set state are acceptable.

```text
DAO → Business logic
```

- Mock repositories may contain demo data generation (`buildMockUsageStatistics`). Acceptable for mocks; do not grow mock generators into a second product.

Do **not** move SVG chart drawing into ApiServices.

---

## 8. Coupling Problems

| Issue | Proposed solution |
| --- | --- |
| `familyApiService` ↔ `mockFamilyRepository` on every method | Single facade (REF-008) |
| Copy-paste `setTimeout(0)` + `useCallback(load)` | After two or three extractions, consider `useAsyncLoad`; **do not** start with a generic hook framework |
| `SystemDashboardPage` ↔ five services | Acceptable for a dashboard; optional `loadDashboardSnapshot()` module, not a BFF |
| Hex colours ↔ design tokens | Use CSS variables (`--safe`, `--warning`, and admin accents) |
| Auth module ↔ `apiClient` via `configureApiAuthBridge` | **Keep.** This is the right boundary. |
| Pages importing `familyApiService` directly | **Keep.** That is the intended UI ↔ networking boundary. |

Do **not** introduce Redux or React Query solely to reduce coupling.

---

## 9. Refactoring Plan

Each step is small, independently reviewable, and must not change API contracts. **None of these are implemented in the documentation pass.**

### REF-001

| Field | Value |
| --- | --- |
| Priority | P0 |
| Location | `client/web/src/test/features/analytics/VerdictTrendsPage.test.tsx` (and new `verdictTrendAggregate.test.ts` when extracted) |
| Current problem | Tests encode UTC `bucketIso`, so they cannot catch the Singapore-day bug. |
| Assessment criterion | Correctness, testability |
| Proposed refactoring | Add characterization tests using Asia/Singapore calendar days (evening SGT still “today”). Keep current tests until REF-002, then replace `bucketIso`. |
| Responsibilities before | Tests follow implementation’s UTC keys |
| Responsibilities after | Tests specify calendar-day behaviour |
| Files affected | Verdict trend tests; later aggregate module |
| Dependencies / consumers | `VerdictTrendsPage` |
| Risk | LOW |
| Testing required | The new tests themselves |
| Expected benefit | Safe to fix F-WEB-01 without silent chart drift |

### REF-002

| Field | Value |
| --- | --- |
| Priority | P0 |
| Location | `VerdictTrendsPage.tsx` `isoDate` / `aggregate` |
| Current problem | F-WEB-01 date bucketing |
| Assessment criterion | Correctness |
| Proposed refactoring | Singapore (or one consistent calendar) day keys; reuse `singaporeToday` / day-key helpers |
| Responsibilities before | Page owns UTC/local mix |
| Responsibilities after | Pure aggregator owns calendar days |
| Files affected | `VerdictTrendsPage.tsx`, tests |
| Dependencies / consumers | Family verdict UI, CSV export |
| Risk | MEDIUM |
| Testing required | REF-001 tests must pass after the switch |
| Expected benefit | Accurate daily verdict mix in SGT |

### REF-003

| Field | Value |
| --- | --- |
| Priority | P0 |
| Location | `SystemHealthPage.tsx`, `UsageStatisticsPage.tsx`, `UserAccessPage.tsx` |
| Current problem | F-WEB-05 stale responses |
| Assessment criterion | Correctness, reliability |
| Proposed refactoring | Request generation id or cancelled flag in `load` |
| Responsibilities before | Last write wins regardless of request order |
| Responsibilities after | Only the latest request updates state |
| Files affected | Listed pages + tests |
| Dependencies / consumers | Admin UI |
| Risk | LOW |
| Testing required | Existing load tests; optional race test |
| Expected benefit | Filters/windows match displayed data |

### REF-004

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `ConsumerTrendsPage.tsx` → `consumerTrendsApiService.ts` or `consumerTrendsNormalize.ts` |
| Current problem | Contract validation in the page |
| Assessment criterion | Layer separation |
| Proposed refactoring | Normalize/validate in the adapter; throw `ApiError` on incomplete payloads |
| Responsibilities before | Page sanitizes API JSON |
| Responsibilities after | Service returns a trusted view model or errors |
| Files affected | Consumer trends service, page, tests |
| Dependencies / consumers | `ConsumerTrendsPage`, `SystemDashboardPage` (if it should also reject incomplete data) |
| Risk | MEDIUM |
| Testing required | Incomplete JSON rejected; happy path unchanged |
| Expected benefit | Page is presentation-only |

### REF-005

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | New `verdictTrendAggregate.ts` |
| Current problem | Aggregation buried in the page |
| Assessment criterion | Business-logic distribution, method length |
| Proposed refactoring | Extract `aggregateFamilyVerdictTrend` after REF-002 |
| Responsibilities before | Page aggregates |
| Responsibilities after | Lib aggregates; page presents |
| Files affected | New lib, page, tests |
| Dependencies / consumers | `VerdictTrendsPage` |
| Risk | LOW after REF-002 |
| Testing required | Unit tests on the pure function |
| Expected benefit | Testable reporting without DOM |

### REF-006

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `FamilyRestrictionSummaryPage.tsx` → `restrictionMatrix.ts` |
| Current problem | F-WEB-04 |
| Assessment criterion | Layer separation, testability |
| Proposed refactoring | Pure matrix builders + dairy/tone helpers; identical UI |
| Responsibilities before | Page computes and renders |
| Responsibilities after | Lib computes; page renders |
| Files affected | New lib, page, new unit tests, existing page tests |
| Dependencies / consumers | Restriction summary only |
| Risk | MEDIUM |
| Testing required | Dairy collapsing and severity tones |
| Expected benefit | Dietary display rules are reviewable |

### REF-007

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `SelfProfileSetupPage.tsx` |
| Current problem | Touched vs persisted severity mixed into the form |
| Assessment criterion | Business-logic distribution |
| Proposed refactoring | `buildRestrictionPayload` |
| Responsibilities before | Submit handler encodes the rule |
| Responsibilities after | Pure function + tests |
| Files affected | Setup page, new test file |
| Dependencies / consumers | Self profile create/update |
| Risk | MEDIUM |
| Testing required | Untouched PREFERENCE preserved; toggled STRICT_AVOID sent |
| Expected benefit | Safer dietary payload |

### REF-008

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `familyApiService.ts` |
| Current problem | F-WEB-03 |
| Assessment criterion | Low coupling, maintainability |
| Proposed refactoring | Single mock vs HTTP facade |
| Responsibilities before | Every method knows mocks |
| Responsibilities after | One branch at construction/export |
| Files affected | `familyApiService.ts`, family API tests |
| Dependencies / consumers | All family pages |
| Risk | LOW |
| Testing required | Existing `familyApiService.test.ts` mock and live paths |
| Expected benefit | One place to add endpoints |

### REF-009

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `ConsumerTrendsPage.tsx` |
| Current problem | F-WEB-02 |
| Assessment criterion | Readability, method length |
| Proposed refactoring | Split charts, paging, formatters; align quotes while touching the file (REF-017) |
| Responsibilities before | One module does everything |
| Responsibilities after | Page orchestrates |
| Files affected | Analytics feature folder, Consumer Trends tests |
| Dependencies / consumers | `/system/trends` |
| Risk | MEDIUM |
| Testing required | Keep `ConsumerTrendsPage.test.tsx` as characterization |
| Expected benefit | Navigable analytics UI code |

### REF-010

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `PersonalHomePage.tsx` |
| Current problem | Multiple data and banner concerns |
| Assessment criterion | Method length, readability |
| Proposed refactoring | Hook + section components |
| Responsibilities before | Page fetches and renders all desks |
| Responsibilities after | Composition |
| Files affected | Account pages, `PersonalHomePage.test.tsx` |
| Dependencies / consumers | `/me` |
| Risk | MEDIUM |
| Testing required | Existing home tests |
| Expected benefit | Easier home-page changes |

### REF-011

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `SystemHealthPage.tsx`, `UsageStatisticsPage.tsx` |
| Current problem | Long result trees and duplicated section chrome |
| Assessment criterion | Readability, maintainability |
| Proposed refactoring | Result component files; shared section styling via CSS variables |
| Responsibilities before | Page file includes all sections |
| Responsibilities after | Page loads; result components present |
| Files affected | Admin/analytics pages, health/usage tests |
| Dependencies / consumers | `/system/health`, `/system/usage` |
| Risk | LOW–MEDIUM |
| Testing required | Existing page tests |
| Expected benefit | Shorter files; shared admin look |

### REF-012

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `UserAccessPage.tsx`, `AdminScanFeedbackPage.tsx` |
| Current problem | Filter/modal/paging mixed; catalog refetch every feedback page |
| Assessment criterion | Maintainability, coupling |
| Proposed refactoring | Debounce/paging helpers; extract modals; catalog once |
| Responsibilities before | Pages own all workflows |
| Responsibilities after | Smaller pages + helpers |
| Files affected | Admin pages and tests |
| Dependencies / consumers | `/system/users`, `/system/feedback` |
| Risk | MEDIUM |
| Testing required | Filter, paging, resolve-toggle tests |
| Expected benefit | Safer admin list changes |

### REF-013

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | New shared helper; `UsageStatisticsPage`, `VerdictTrendsPage`, `consumerTrendsReport.ts` |
| Current problem | F-WEB-08 CSV duplication |
| Assessment criterion | Maintainability, low coupling |
| Proposed refactoring | `downloadTextFile(filename, mime, text)` |
| Responsibilities before | Each feature builds blobs |
| Responsibilities after | One download helper |
| Files affected | Analytics pages/report + tests |
| Dependencies / consumers | Export buttons |
| Risk | LOW |
| Testing required | Existing export tests pointed at helper |
| Expected benefit | Consistent download/revoke |

### REF-014

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Health, usage, verdict trend colour constants |
| Current problem | Hex copies bypass tokens |
| Assessment criterion | Low coupling, maintainability |
| Proposed refactoring | CSS variables from design tokens |
| Responsibilities before | JS owns palette |
| Responsibilities after | Theme owns palette |
| Files affected | Those pages + `app.css` / tokens as needed |
| Dependencies / consumers | Admin analytics visuals |
| Risk | LOW |
| Testing required | Visual/page tests still find labels |
| Expected benefit | Brand consistency |

### REF-015

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Family dashboard and restriction summary listeners; mock repository |
| Current problem | F-WEB-07 |
| Assessment criterion | Low coupling, maintainability |
| Proposed refactoring | Remove production listeners or gate to mock mode; prefer explicit reload |
| Responsibilities before | Pages pretend to be event-driven |
| Responsibilities after | Honest refresh model |
| Files affected | Two family pages, mock repo |
| Dependencies / consumers | Mock-mode family UI |
| Risk | LOW |
| Testing required | Dashboard/restriction tests |
| Expected benefit | Clearer data-flow |

### REF-016

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | `shared/api/apiClient.ts` |
| Current problem | F-WEB-09 ngrok header always on |
| Assessment criterion | Security hygiene |
| Proposed refactoring | Gate with env; do not break documented tunnel workflows |
| Responsibilities before | Every request sends ngrok skip header |
| Responsibilities after | Only when configured |
| Files affected | `apiClient.ts`, tests, env types |
| Dependencies / consumers | All HTTP |
| Risk | LOW |
| Testing required | `apiClient.test.ts` |
| Expected benefit | Cleaner production requests |

### REF-017

| Field | Value |
| --- | --- |
| Priority | P3 |
| Location | `ConsumerTrendsPage.tsx` (when split in REF-009) |
| Current problem | Double quotes vs project single quotes |
| Assessment criterion | Readability (consistency) |
| Proposed refactoring | Match surrounding files **only while touching that file** |
| Responsibilities before / after | Unchanged behaviour |
| Files affected | Consumer Trends modules |
| Risk | LOW |
| Testing required | Existing tests |
| Expected benefit | Less noise in diffs |

### REF-018

| Field | Value |
| --- | --- |
| Priority | P3 |
| Location | `client/web/package.json`, `vite-env.d.ts` |
| Current problem | Likely unused `firebase` package; unused `VITE_FIREBASE_API_KEY` |
| Assessment criterion | Maintainability |
| Proposed refactoring | Confirm no import, then remove |
| Files affected | package files, env types |
| Risk | LOW |
| Testing required | `npm run verify` |
| Expected benefit | Smaller install surface |

### REF-019

| Field | Value |
| --- | --- |
| Priority | P3 |
| Location | `client/web/src/styles/app.css` |
| Current problem | ~5300-line stylesheet |
| Assessment criterion | Maintainability |
| Proposed refactoring | **Do not** split in one pass. Optional section comments when editing related UI. |
| Files affected | `app.css` incrementally |
| Risk | HIGH if split blindly |
| Testing required | Visual regression / Playwright smoke |
| Expected benefit | Slightly easier navigation without a CSS rewrite |

### Implementation order

**Phase 1 — Safety:** REF-001, REF-002, REF-003  

**Phase 2 — Layer separation:** REF-004, REF-005, REF-006, REF-007, REF-008  

**Phase 3 — Complexity reduction:** REF-009, REF-010, REF-011, REF-012  

**Phase 4 — Coupling reduction:** REF-013, REF-014, REF-015, REF-016  

**Phase 5 — Maintainability:** REF-017, REF-018, REF-019 (comments only)  

**Phase 6 — Validation:** `npm run verify` in `client/web`; optional Playwright smoke (login, one family page, one admin analytics page); confirm no API/schema changes.

---

## 10. Testing Plan

Add or adjust tests **before** risky behaviour changes.

| Before | Tests |
| --- | --- |
| REF-002 | Unit tests for Singapore-day buckets (evening SGT still today); keep UTC tests until the switch |
| REF-004 | Incomplete consumer-trends JSON rejected; complete payloads still render |
| REF-006 | Dairy family collapsing; severity tones; “other” codes |
| REF-007 | Payload builder: untouched PREFERENCE vs toggled STRICT_AVOID |
| REF-009 | Keep `ConsumerTrendsPage.test.tsx`; add chart tests only if extraction makes them cheap |
| REF-003 / REF-012 | Existing admin page tests; do not weaken filter/paging coverage |

**Regression (do not weaken):** `SessionProvider`, `apiClient`, `ProtectedRoute`, family members, admin scan feedback, auth field validation.

**Do not** rely only on Playwright for date-bucket correctness.

---

## 11. API / Database Impact

| Surface | Impact of this plan |
| --- | --- |
| API contracts | **No change** |
| Database schema | **No change** |
| Database behaviour | **No change** |
| React consumers | Internal splits; **visible** verdict-trend date-bucket **correctness** fix (REF-002) |
| Android consumers | **Unaffected** |

F-WEB-06 (server pagination) is explicitly **out of scope** for a web-only refactor.

---

## 12. Files Expected to Change

### This documentation task

| File | Planned change | Assessment criterion | Risk |
| --- | --- | --- | --- |
| `docs/code-quality/WEB-CODE-QUALITY.md` | Create this living assessment | Other (process) | LOW |
| `docs/code-quality/README.md` | Link the web document | Other (process) | LOW |

### Later implementation (not done in this pass)

| File | Planned change | Assessment criterion | Risk |
| --- | --- | --- | --- |
| `client/web/src/features/analytics/VerdictTrendsPage.tsx` | Calendar-day aggregate extract | Correctness, method length | MEDIUM |
| `client/web/src/test/features/analytics/VerdictTrendsPage.test.tsx` | Replace UTC `bucketIso` | Testability | MEDIUM |
| `client/web/src/features/analytics/ConsumerTrendsPage.tsx` | Split module | Method length, readability | MEDIUM |
| `client/web/src/features/analytics/consumerTrendsApiService.ts` | Normalize/validate response | Layer separation | MEDIUM |
| `client/web/src/features/family/pages/FamilyRestrictionSummaryPage.tsx` | Extract matrix lib | Layer separation | MEDIUM |
| `client/web/src/features/family/api/familyApiService.ts` | Mock facade | Low coupling | LOW |
| `client/web/src/features/account/pages/PersonalHomePage.tsx` | Hook + sections | Method length | MEDIUM |
| `client/web/src/features/account/pages/SelfProfileSetupPage.tsx` | Payload helper | Business logic | MEDIUM |
| `client/web/src/features/admin/SystemHealthPage.tsx` | Result extract; request id | Readability, correctness | LOW |
| `client/web/src/features/analytics/UsageStatisticsPage.tsx` | Result extract; request id; CSV helper | Readability | LOW |
| `client/web/src/features/admin/UserAccessPage.tsx` | Filters/modals; request id | Maintainability | MEDIUM |
| `client/web/src/features/admin/AdminScanFeedbackPage.tsx` | Catalog once; extract filters | Coupling | MEDIUM |
| `client/web/src/shared/api/apiClient.ts` | Optional ngrok header gate | Security hygiene | LOW |
| `client/web/package.json` | Remove unused `firebase` if confirmed | Maintainability | LOW |

---

## 13. Changes NOT Recommended

- Redux, React Query, or a new “web service layer” framework.
- Interfaces for every `*ApiService`.
- Replacing working SVG charts with Recharts/D3.
- Deleting `VITE_USE_MOCK_API` or mock repositories.
- Inventing server-side pagination or new scan-history APIs in this web-only effort.
- Moving dairy grouping to Spring without a product decision.
- Splitting `app.css` into many files in one PR.
- Changing in-memory JWT + HttpOnly refresh cookie session design (already sound).
- Treating `ProtectedRoute` / `FamilyMeGate` as authorization (they are navigation).
- Mass-renaming `adminService` vs `*ApiService` for naming fashion.
- Expanding `FamilyMeContext` into a global store for all family pages.

---

## 14. Expected Assessment Improvement

Implementing REF-001–REF-018 (and incremental CSS comments) should move the scorecard toward the targets in section 4 as follows.

- **Readability:** Pages become composition of named sections (`DailyActivityChart`, `usePersonalHomeData`). Intent is visible without scrolling mixed fetch and SVG.
- **Maintainability:** One family mock facade; shared download and (where extracted) paging; fewer copy-paste admin filters; fewer hex copies.
- **Layer separation:** Domain and contract checks live in `lib` or ApiServices; JSX presents. Mapping remains Page → lib → ApiService → apiClient, not a fake Spring tree.
- **Low coupling:** Pages do not know mock repositories; health/usage/verdict share theme tokens; mock events are not a fake live bus.
- **Reasonable method length:** Charts, matrix builders, and aggregators are isolated functions with one job.
- **Appropriate business-logic distribution:** Date buckets, dairy matrix, payload construction, and CSV assembly leave the render path.
- **Reduced complexity:** Consumer Trends and Personal Home become navigable; verdict aggregation is a testable pure function.

---

## Changes checklist (later implementation)

- [x] REF-001 Characterization tests for Singapore verdict days
- [x] REF-002 Fix verdict day keys
- [x] REF-003 Request-id on health / usage / user-access loads
- [x] REF-004 Consumer trends response normalize in adapter
- [x] REF-005 `verdictTrendAggregate.ts`
- [x] REF-006 `restrictionMatrix.ts`
- [x] REF-007 `buildRestrictionPayload`
- [x] REF-008 Family API mock facade
- [x] REF-009 Split Consumer Trends page
- [x] REF-010 Split Personal Home
- [x] REF-011 Split health/usage results
- [x] REF-012 Admin list filter/modal extract; catalog once
- [x] REF-013 Shared `downloadTextFile`
- [x] REF-014 CSS variables for admin/analytics colours
- [x] REF-015 Mock event listeners
- [x] REF-016 Gate ngrok header
- [x] REF-017 Quote alignment on Consumer Trends (with REF-009)
- [x] REF-018 Remove unused Firebase dep/type if confirmed
- [x] REF-019 Incremental `app.css` comments only (no big-bang split)
- [x] `npm run verify` in `client/web`
- [x] Optional Playwright smoke
- [x] Update findings table Status column to Done
- [x] Fill scorecard “After implementation” column

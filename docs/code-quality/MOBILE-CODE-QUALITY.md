# Mobile Code Quality Assessment

Living document for the Android Jetpack Compose client under `client/mobile`.  
Update the **Changes checklist** when work lands; keep findings status notes current.

**Location:** `docs/code-quality/MOBILE-CODE-QUALITY.md` (linked from [`docs/code-quality/README.md`](README.md))  
**Scope:** `client/mobile` (~119 main Kotlin sources, ~42 unit-test Kotlin sources, Gradle/Hilt/OkHttp/Retrofit, Android manifests and network-security XML)  
**Out of scope:** Spring Boot, MySQL schema, React web. This client *calls* those APIs; proposed refactors must **not** change API contracts or database behaviour unless a later, separately approved task says otherwise. Visual redesign of the supplied Compose UI is also out of scope (see root `AGENTS.md`).

**Status of this document:** Assessment and refactoring plan only. **No REF-* items are implemented.** Do not treat this file as authorization to change application code until the plan is approved.

---

## 1. Scope Reviewed

| Area | Paths |
| --- | --- |
| App entry | `CanMakanApplication.kt`, `MainActivity.kt` |
| Navigation shell | `navigation/` (`CanMakanApp`, `AuthNavGraph`, `CanMakanNavGraph`, `CanMakanNavGraphViewModel`, invite deep links) |
| Auth / session | `features/auth/` (login, registration, EncryptedSharedPreferences, refresh cookie jar, bearer interceptor/authenticator) |
| Account | `features/account/` |
| Dietary profile | `features/dietaryprofile/` (onboarding, restriction sheet, dairy presentation) |
| Family | `features/family/` (drawer, create/invite/dependant, restriction matrix) |
| Product | `features/product/` (scan, verdict, history, recommendation; reporting README-only) |
| Notifications / session heartbeat | `features/notifications/`, `features/session/` |
| Shared | `shared/di`, `shared/network`, `shared/ui`, `shared/model`, `shared/util`, `shared/notifications` |
| Placeholders | `features/analytics/`, `workers/`, `features/product/reporting/` (README only) |
| Tests | `app/src/test/` (unit only; **no** `androidTest`) |

Largest production files (approximate total lines at review time):

| File | Lines | Role |
| --- | ---: | --- |
| `product/verdict/ProductDetailScreen.kt` | ~629 | Verdict UI, tabs, UC20 feedback row |
| `navigation/CanMakanNavGraphViewModel.kt` | ~544 | Authenticated shell state (family + diet + notifications + pending verdict) |
| `navigation/CanMakanNavGraph.kt` | ~527 | NavHost, drawer, dietary sheet, route wiring |
| `product/scan/ScannerScreen.kt` | ~483 | CameraX + ML Kit + permission + scan chrome |
| `family/ui/FamilyRestrictionSummaryScreen.kt` | ~478 | UC6 matrix UI |
| `family/ProfileDrawerContent.kt` | ~453 | Profile switcher + family actions |
| `dietaryprofile/setup/ui/AuthenticatedDietaryOnboardingScreen.kt` | ~380 | Post-login SELF profile setup UI |
| `auth/ui/RegistrationScreens.kt` | ~369 | Registration UI |
| `shared/di/NetworkModule.kt` | ~343 | Retrofit/OkHttp, retry interceptor, API factories |

Android layer mapping for assessors (there is no Spring Controller/Service/DAO on the device):

| Assessor term | Android equivalent | Must not contain |
| --- | --- | --- |
| Frontend / UI | Composables (`*Screen`, sheets, drawer, theme) | Retrofit calls, persistence, server authorization |
| Controller | Navigation graph + thin screen callbacks | Multi-step workflows, DTO mapping |
| Service | `@HiltViewModel` orchestration | HTTP path strings, JSON parsing of error bodies |
| DAO / Repository | `*Repository` + Retrofit `*ApiService` | Compose, navigation, dietary verdict rules |

Real persistence and dietary assessment live on Spring Boot. Android repositories are **HTTP adapters**, not a second database layer. There is **no Room/SQLite**.

---

## 2. Architecture Overview

Intended (and mostly followed) flow:

```text
Composable Screen / Sheet
    ↓  hiltViewModel(), collectAsStateWithLifecycle
@HiltViewModel (UI state, account/profile ownership, coroutines)
    ↓
Repository interface or concrete HTTP façade
    ↓
Retrofit *ApiService
    ↓
OkHttp (Bearer interceptor, refresh authenticator, cookie jar, retries)
    ↓
Spring Boot (authoritative rules and persistence)
```

**Strengths to keep**

- Feature packages align with backend/web names (`auth`, `family`, `dietaryprofile`, `product`).
- Screens reviewed do **not** call repositories directly; networking stays in ViewModels or repositories.
- Session stack is carefully designed: `AuthSessionStore` + Keystore `EncryptedSharedPreferences`, HttpOnly refresh via `PersistentRefreshCookieJar`, dedicated refresh OkHttp client **without** the bearer interceptor, `AuthAccountKey` + generation counters so stale coroutines cannot apply another account’s result. This is well unit-tested.
- Client-side restriction edit gating (`RestrictionEditAuthorization`) is documented as UX only (D3). Backend remains the authority.
- Dairy catalog aliases are isolated in `DairyRestrictionPresentation` (presentation, not a second dietary engine).
- Release `BASE_URL` is fail-closed (HTTPS, host, trailing slash). Debug may use cleartext for emulator/LAN.
- Http logging is `BASIC` (no request bodies). Auth `toString()` redacts tokens and passwords.

**Where the architecture drifts**

```text
ScannerViewModel / ScanFeedbackViewModel
    → CanMakanApiService (Retrofit)     // skips product repository
```

```text
CanMakanNavGraphViewModel
    → DietaryRestrictionRepository
    → FamilyProfileRepository
    → NotificationsRepository
    → AuthSessionStore
    → ActiveProfileManager
    → SystemNotifier
    + pending VerdictDetail navigation cache
```

That shell ViewModel is the main coupling and complexity hotspot.

---

## 3. Assessor Criteria Evaluation

| Criterion | Current Assessment | Main Issues | Priority |
| --------------------------- | ------------------ | ----------- | -------- |
| Readability | Fair (6.5/10) | 450–630 line screens; `CreateFamilyException` reused for notifications/profile switch; leftover sample/ngrok comments | P1 |
| Maintainability | Fair (6/10) | Shell VM owns four features; duplicated nav chrome and `SelectableOptionCard`; README `applicationId` stale | P1 |
| Layer Separation | Fair (6/10) | Scan/feedback ViewModels talk to Retrofit; DTO→UI mapping in `ScannerViewModel`; family HTTP façade mixes throw vs `Result` | P1 |
| Low Coupling | Fair (6/10) | Nav graph VM constructor fan-out; Settings toggle lives on shell VM; absolute vs relative Retrofit paths | P1 |
| Reasonable Method Length | Weak–fair (5.5/10) | `CanMakanNavGraph`, `ProfileDrawerContent`, `MatrixGrid`, `processBarcode`, `reloadFamilyContext`, `buildOkHttpClient` | P1 |
| Business Logic Distribution | Fair (6.5/10) | Verdict calculation stays on server (good); client still maps findings, dairy aliases, and edit permission in VMs/UI helpers | P2 |
| Complex Method Refactoring | Weak–fair (5.5/10) | Shell load/switch/create-family; scan pipeline; OkHttp retry interceptor | P1 |

---

## 4. Quality Scorecard

Scores are for the **current** mobile client. Target is after a later, separately approved refactor. “After implementation” stays blank until that work lands.

| Category | Current | Target | After implementation |
| --- | ---: | ---: | ---: |
| Readability | 6.5/10 | 8/10 | |
| Maintainability | 6/10 | 8/10 | |
| Layer Separation | 6/10 | 8/10 | |
| Low Coupling | 6/10 | 8/10 | |
| Method Complexity | 5.5/10 | 8/10 | |
| Testability | 7.5/10 | 8/10 | |
| Security | 8/10 | 8.5/10 | |
| Correctness | 7/10 | 8/10 | |
| Overall | 6.5/10 | 8/10 | |

Do not inflate: auth, ownership guards, and unit tests are strong. God-size Compose files, the shell ViewModel, and scan-layer skip pull the overall score down. Security is already relatively strong; this plan should not gamble it for style.

---

## 5. Critical Findings

There is **no CRITICAL** mobile-only security or data-integrity defect (no WebView XSS surface, no plaintext token `SharedPreferences`, no hard-coded API keys in Kotlin sources, camera permission is requested at runtime). Highest items first.

### Findings table

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| F-MOB-01 | HIGH | `CanMakanNavGraphViewModel` is a multi-feature god object (~544 lines, six collaborators) | Planned |
| F-MOB-02 | HIGH | `ScannerViewModel` / `ScanFeedbackViewModel` call Retrofit directly and map DTOs in the ViewModel | Planned |
| F-MOB-03 | HIGH | Authenticated `CanMakanNavGraph` + drawer + sheet is one ~527-line composable | Planned |
| F-MOB-04 | HIGH | OkHttp retry interceptor uses `Thread.sleep` on the dispatcher thread | Planned |
| F-MOB-05 | MEDIUM | Mix of leading-`/` and relative Retrofit paths on `CanMakanApiService` | Planned |
| F-MOB-06 | MEDIUM | Unknown assess verdict strings default to `WARNING` | Planned |
| F-MOB-07 | MEDIUM | `FamilyProfileRepository` error type, JSON regex, and `Result` vs throw are inconsistent | Planned |
| F-MOB-08 | MEDIUM | Large Compose files mix chrome, camera, matrix layout, and feedback forms | Planned |
| F-MOB-09 | MEDIUM | Duplicated `SelectableOptionCard`, network-error `when`, and per-route nav callbacks | Planned |
| F-MOB-10 | MEDIUM | `collectAsState()` on Scanner and family matrix (not lifecycle-aware) | Planned |
| F-MOB-11 | MEDIUM | Stale comment: NavGraph still says inbox open marks all notifications read | Planned |
| F-MOB-12 | MEDIUM | `CreateDependantProfileViewModel` has no dedicated unit test | Planned |
| F-MOB-13 | LOW | Dead `ProductSampleData`, unbound `SampleDietaryRestrictionRepository`, unused ngrok `DEFAULT_BASE_URL` | Planned |
| F-MOB-14 | LOW | Always-on `ngrok-skip-browser-warning` and spoofed Chrome `User-Agent` | Planned |
| F-MOB-15 | LOW | `Gson` `Strictness.LENIENT`; unused `USE_BIOMETRIC` permission; README `applicationId` | Planned |
| F-MOB-16 | INFO | `RestrictionEditAuthorization` is navigation/UX only — do not turn it into a client policy engine | Documented — no change |
| F-MOB-17 | INFO | No instrumented/UI tests; Sonar already excludes Compose screens from coverage | Documented — optional later |

### Finding detail

#### F-MOB-01

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `client/mobile/app/src/main/java/sg/edu/nus/iss/canmakan/navigation/CanMakanNavGraphViewModel.kt` |
| Class/component | `CanMakanNavGraphViewModel` |
| Method/function | Class as a whole; especially `reloadFamilyContext`, `switchProfile`, `createFamilyCircle`, `setNotificationsEnabled`, `refreshNotifications` |
| Current problem | Activity-scoped VM loads family/`me`/active profile, restriction display names, create-circle, optimistic profile switch, unread badge, notification preference PUT, system tray notify-once set, and in-memory `pendingVerdict` for product detail. ~15 `StateFlow`s and several generation counters live in one type. |
| Why it matters | A small family or notification change requires editing the navigation module. Tests (`CanMakanNavGraphViewModelTest`, ~778 lines) encode the whole kitchen sink. Assessors will see mixed Service-layer responsibilities in a “navigation” class. |
| Proposed solution | Keep one thin shell VM (or coordinator) that **exposes** combined UI state. Move cohesive work into existing or new types: family context load/switch/create (family feature), notification badge/preference (notifications/account), pending verdict holder (product or a 20-line navigator state). Do **not** invent interfaces for each unless a test double is required. |
| Affected layer | ViewModel (Service equivalent) — split by feature, not by “step1/step2” |
| Risk | MEDIUM (shell is on every authenticated screen) |
| Blast radius | `CanMakanNavGraph.kt`, Settings notification toggle wiring, `CanMakanNavGraphViewModelTest` |

**Current layer → Correct layer**

> Navigation ViewModel currently performs family orchestration, dietary summary load, notification preference persistence, tray notifications, and scan-result caching.  
> Proposed: Navigation → route/shell composition only; Family feature → membership/active profile; Notifications/account → badge and preference; Product → pending `VerdictDetail`.

#### F-MOB-02

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `ScannerViewModel.kt`, `ScanFeedbackViewModel.kt`, `shared/network/CanMakanApiService.kt` |
| Class/component | `ScannerViewModel`, `ScanFeedbackViewModel` |
| Method/function | `processBarcode`, `loadAlternatives`, `toVerdictDetail`, `submitNegativeFeedback`, `submitPositiveFeedback` |
| Current problem | History/recommendations already use `ScanHistoryRepository` / `RecommendationHistoryRepository`. Scan validate/assess/feedback skip that layer and depend on Retrofit + DTO types in the ViewModel. `ScanFeedbackViewModel` has no account/profile generation guard (backend must still authorize; client can apply a late error after sign-out). |
| Why it matters | Layer-separation criterion: ViewModel should orchestrate, repository should own HTTP and DTO mapping. Scan is the core UC and is the inconsistent exception. |
| Proposed solution | Add `ScanRepository` (interface + `ServerScanRepository`) wrapping validate, assess, recommendations-for-scan, and feedback. ViewModel keeps process-state machine and ownership checks. Map `AssessmentResponse` → `VerdictDetail` in the repository or a small mapper object (same package as product models), not in Compose. |
| Affected layer | ViewModel → Repository; UI unchanged |
| Risk | MEDIUM (must preserve validate → assess → optional alternatives order and SUCCESS navigation) |
| Blast radius | `ScannerViewModelTest`, `ScanFeedbackViewModelTest`, `NetworkModule` Retrofit factory |

#### F-MOB-03

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `navigation/CanMakanNavGraph.kt` |
| Class/component | `CanMakanNavGraph` |
| Method/function | Entire `@Composable` (NavHost lambdas inline) |
| Current problem | Drawer content, invitation/profile error banners, 10+ `composable` destinations, dietary `ModalBottomSheet`, and duplicated `onMenuClick` / scan / history / notifications lambdas live in one function. Nested `fun` helpers (`openDrawer`, `navigateToScannerHome`, `openNotifications`) are reasonable; the destination list is not. |
| Why it matters | Another developer cannot change Invite without scrolling Scanner/History/Settings. Comment on notifications `DisposableEffect` is also stale (F-MOB-11). |
| Proposed solution | Extract `AuthenticatedShell` (drawer + banners + sheet) and per-route files or a `authenticatedDestinations` function that only registers routes. Preserve route string constants. **Do not** change visual layout. |
| Affected layer | UI / Controller equivalent |
| Risk | MEDIUM (navigation regressions) |
| Blast radius | All authenticated screens’ constructor call sites in the graph |

#### F-MOB-04

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `shared/di/NetworkModule.kt` |
| Class/component | `NetworkModule.buildOkHttpClient` |
| Method/function | Retry interceptor (~lines 227–270) |
| Current problem | On non-success, non-4xx responses (and `IOException`), the interceptor `Thread.sleep(1000L * tryCount)` on OkHttp’s thread, up to 3 attempts. `InterruptedException` is not handled. This is not a coroutine delay; it blocks a shared dispatcher thread. |
| Why it matters | Reliability and performance: a slow or 5xx backend can stall other calls. Harder to test than a non-blocking policy. |
| Proposed solution | Keep max 3 attempts and “do not retry 4xx / skip header `X-CanMakan-No-Retry`” behaviour. Replace sleep with OkHttp-friendly backoff that does not block (e.g. fail fast after connection timeout, or a `RetryInterceptor` that uses `Call` timeout / no sleep, documented as at-most-N immediate retries). Extend `NetworkRetryInteractionTest`. Do **not** add a new HTTP library. |
| Affected layer | Shared network (DAO-adjacent) |
| Risk | MEDIUM (timing of retries may change slightly; user-visible if 5xx was previously hidden by 1–2s waits) |
| Blast radius | All authenticated HTTP; dedicated refresh client is separate and should stay without this interceptor |

#### F-MOB-05

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `shared/network/CanMakanApiService.kt` |
| Class/component | `CanMakanApiService` |
| Method/function | `validateBarcode`, `assessBarcode`, `getRecommendations`, `submitScanFeedback` |
| Current problem | Paths `/api/scan/...` are root-absolute. `profiles/{profileId}/recommendations` is relative to Retrofit `baseUrl` (typically `.../api/`). Retrofit treats a leading `/` as “replace the URL path,” so scan vs recommendation depend on different base-URL rules. |
| Why it matters | Changing `BASE_URL` path prefix (with/without `/api/`) can break one family of endpoints and not the other — a silent production defect. |
| Proposed solution | Make **all** `CanMakanApiService` paths relative to the same `BuildConfig.BASE_URL` convention used by other `*ApiService` types. Confirm against backend mapping (no API contract change). Add a small contract test like `AuthApiServiceContractTest`. |
| Affected layer | HTTP adapter |
| Risk | MEDIUM if a path is rewritten incorrectly |
| Blast radius | Scan, alternatives, feedback |

#### F-MOB-06

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `features/product/scan/ScannerViewModel.kt` |
| Class/component | `ScannerViewModel` |
| Method/function | `parseVerdict` |
| Current problem | `ScanVerdict.valueOf(raw.uppercase())` failures become `WARNING`. A backend typo or new verdict string is shown as Warning (and alternatives are fetched) instead of ERROR. |
| Why it matters | Correctness: users may treat an unknown result as a soft warning. |
| Proposed solution | Map only known enum names; otherwise set `ScanProcessState.ERROR` with a generic message. Keep this in the mapper/repository after F-MOB-02. Extend `ScannerViewModelTest`. |
| Affected layer | ViewModel / mapper |
| Risk | LOW–MEDIUM (stricter than today) |
| Blast radius | Scan success navigation |

#### F-MOB-07

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `features/family/data/FamilyProfileRepository.kt` |
| Class/component | `FamilyProfileRepository`, `CreateFamilyException` |
| Method/function | `messageFromError`, `getFamilyRestrictionSummary`, `getActiveProfile`, `setNotificationPreference`, invitations |
| Current problem | (1) `CreateFamilyException` is thrown for active-profile, invitations, and **user notification preferences**. (2) Error messages are scraped with `Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")` instead of Gson. (3) `getFamilyRestrictionSummary` returns `Result<>` while sibling methods throw. |
| Why it matters | Readability and maintainability: callers (`NotificationsInboxViewModel`, NavGraph VM) import a create-family type for unrelated HTTP. Regex breaks on escaped quotes. |
| Proposed solution | Rename to a family/HTTP exception (e.g. `FamilyApiException`) without changing status-code handling. Parse `{ "message": ... }` with the existing Gson instance. Pick one error style for the repository (prefer throw + catch in ViewModels, matching the rest of the app); adapt `FamilyRestrictionSummaryViewModel` accordingly. |
| Affected layer | Repository |
| Risk | LOW if status codes and user-facing strings stay the same |
| Blast radius | Family/notification/onboarding tests that match exception type |

#### F-MOB-08

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `ProductDetailScreen.kt`, `ScannerScreen.kt`, `FamilyRestrictionSummaryScreen.kt`, `ProfileDrawerContent.kt`, `DietaryRestrictionSheet.kt`, `AuthenticatedDietaryOnboardingScreen.kt` |
| Class/component | Listed composables |
| Method/function | `ProductDetailScreen`, `ScanFeedbackRow`, `ScannerScreen`, `CameraPreview`, `MatrixGrid`, `ProfileDrawerContent`, sheet/onboarding bodies |
| Current problem | Files are presentation-heavy (acceptable) but each top-level composable still owns multiple visual sections that cannot be explained in one sentence. `MatrixGrid` (~191 lines) also re-filters `isActive` after the ViewModel already did. |
| Why it matters | Method-length / readability criteria. Splits should be **named UI sections**, not `step1()`. |
| Proposed solution | File-level splits only: e.g. `ProductDetailHeader`, `ScanFeedbackRow` file, `RestrictionMatrixGrid`, `CameraPreview` file, drawer sections (profile list vs actions). Same parameters, same theme tokens. No behaviour change. |
| Affected layer | UI |
| Risk | LOW if previews/manual scan of screens is done |
| Blast radius | Call sites in `CanMakanNavGraph` |

#### F-MOB-09

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | Multiple screens; `DietaryRestrictionViewModel`, `ScanHistoryViewModel`, `CanMakanNavGraphViewModel` |
| Class/component | Screens / ViewModels |
| Method/function | Nav callback parameters; `SocketTimeoutException`/`ConnectException` `when` |
| Current problem | Nearly every `composable` repeats `onMenuClick`, `onScanClick`, `onHistoryClick`, `onNotificationsClick`, `hasUnreadNotifications`. `SelectableOptionCard` is copy-pasted in onboarding and restriction sheet. Connection messages differ slightly (“firewall” vs “configured backend”). |
| Why it matters | A chrome or accessibility fix must be repeated ~10 times. |
| Proposed solution | Introduce a small `AuthenticatedChromeCallbacks` data class (or default in a shared scaffold) **without** a new navigation framework. Move one `SelectableOptionCard` to `shared/ui` or `dietaryprofile` ui. One `userMessageForNetworkFailure(exception)` helper used by the three ViewModels. |
| Affected layer | UI + small shared util |
| Risk | LOW |
| Blast radius | Screen signatures (internal to the app, not HTTP) |

#### F-MOB-10

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `ScannerScreen.kt`, `CanMakanNavGraph.kt` (family/restrictions route) |
| Class/component | `ScannerScreen`, family matrix route |
| Method/function | `collectAsState()` |
| Current problem | Rest of the shell uses `collectAsStateWithLifecycle()`. Scanner and UC6 still use `collectAsState()`, so collectors may run when the screen is STOPPED (camera tab in background). |
| Why it matters | Correctness/reliability: extra work and possible navigation from SUCCESS while not visible. |
| Proposed solution | Switch to `collectAsStateWithLifecycle`. No API change. |
| Affected layer | UI |
| Risk | LOW |
| Blast radius | Scanner and family matrix only |

#### F-MOB-11

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `CanMakanNavGraph.kt` (~lines 488–492); contrast `NotificationsInboxViewModel.startRefresh` |
| Class/component | Notifications route |
| Method/function | `DisposableEffect` comment vs `listMine()` |
| Current problem | Comment says opening the inbox marks every card read on the backend. ViewModel comment and code say listing no longer auto-marks read; refresh on dispose still updates the bell. |
| Why it matters | Maintainability: the next developer may “restore” auto-read and change product behaviour. |
| Proposed solution | Fix the NavGraph comment to match ViewModel. Keep `refreshNotifications()` on dispose if badge still depends on unread flags. |
| Affected layer | UI comments / Controller |
| Risk | LOW |
| Blast radius | None if comment-only |

#### F-MOB-12

| Field | Value |
| --- | --- |
| Severity | MEDIUM |
| File | `family/ui/CreateDependantProfileViewModel.kt` |
| Class/component | `CreateDependantProfileViewModel` |
| Method/function | `create` |
| Current problem | Validation, account-bind reset, and repository call are untested while invite/onboarding VMs have tests. |
| Why it matters | Phase 1 safety: dependant create is a mutating family API. |
| Proposed solution | Add `CreateDependantProfileViewModelTest` (name/relationship required, cancel on account change, success `created=true`). |
| Affected layer | Tests |
| Risk | LOW |
| Blast radius | None until implementation |

#### F-MOB-13

| Field | Value |
| --- | --- |
| Severity | LOW |
| File | `ProductSampleData.kt`, `SampleDietaryRestrictionRepository.kt`, `DietaryRestrictionModule.kt`, `NetworkModule.kt` |
| Class/component | Sample/dead code |
| Method/function | N/A |
| Current problem | Prototype sample history/products are unreferenced. Sample restriction repo is commented out of Hilt (good) but still in main. Unused `DEFAULT_BASE_URL` ngrok host. |
| Why it matters | Assessors may think production still uses sample data (README still says “sample presentation data” in places). |
| Proposed solution | Delete unreferenced sample types after a usage grep. Remove ngrok constant. Trim commented Hilt lines. Update `client/mobile/README.md` status paragraph. |
| Affected layer | Dead code |
| Risk | LOW |
| Blast radius | None if grep is clean |

#### F-MOB-14

| Field | Value |
| --- | --- |
| Severity | LOW |
| File | `NetworkModule.buildOkHttpClient` |
| Class/component | OkHttp interceptor |
| Method/function | Header injection |
| Current problem | Every main-client request sets `ngrok-skip-browser-warning: true` and a Chrome Mobile `User-Agent`. README states native calls are not identified by User-Agent. |
| Why it matters | Misleading identity to the server/proxies; ngrok header is unnecessary in production. |
| Proposed solution | Send the ngrok header only when `BuildConfig.DEBUG` (or when base host contains `ngrok`). Use a simple `CanMakan-Android/{version}` User-Agent unless backend **requires** the Chrome string (confirm; default to honest UA). |
| Affected layer | Network |
| Risk | LOW–MEDIUM if some proxy filters UA |
| Blast radius | All main OkHttp calls; refresh client unchanged |

#### F-MOB-15

| Field | Value |
| --- | --- |
| Severity | LOW |
| File | `NetworkModule.provideGson`, `AndroidManifest.xml`, `client/mobile/README.md` |
| Class/component | Gson / manifest / docs |
| Method/function | `setStrictness(LENIENT)` |
| Current problem | Lenient JSON can accept malformed payloads. `USE_BIOMETRIC` is declared with no biometric code. README still lists `applicationId` `com.example.canmakan` while Gradle uses `sg.edu.nus.iss.canmakan`. |
| Why it matters | Slightly weaker parse strictness; unused permission looks unfinished; docs confuse assessors. |
| Proposed solution | Use Gson default/strict unless a captured payload requires lenient (document the reason). Remove unused biometric permission. Fix README package/application ID. Keep EncryptedSharedPreferences despite AndroidX deprecation (justified in `AuthSecurePreferences.kt`). |
| Affected layer | Network / manifest / docs |
| Risk | LOW (strict Gson might break an undocumented payload — add a test with a real sample first) |
| Blast radius | All Retrofit parsing |

#### F-MOB-16

| Field | Value |
| --- | --- |
| Severity | INFO |
| File | `RestrictionEditAuthorization.kt` |
| Class/component | `RestrictionEditAuthorization` |
| Method/function | `mayEditRestrictions` |
| Current problem | None as a defect. Client hides Save for non-admins on others’ profiles. |
| Why it matters | Same as web F-WEB-10: do not duplicate Spring authorization on the phone. |
| Proposed solution | **No change.** Keep unit tests. Backend must remain source of truth. |
| Affected layer | UI gating |
| Risk | N/A |
| Blast radius | N/A |

#### F-MOB-17

| Field | Value |
| --- | --- |
| Severity | INFO |
| File | `app/src/androidTest` (missing) |
| Class/component | N/A |
| Method/function | N/A |
| Current problem | Camera, drawer, and NavHost have no instrumented tests. Unit tests for VMs/session are strong. Sonar exclusions for `*Screen*.kt` are documented in the mobile README. |
| Why it matters | Compose splits (F-MOB-03/08) will not be caught by JVM tests. |
| Proposed solution | Optional later: one smoke Compose test for login→shell **or** manual test plan in Phase 6. Do not block the refactor on Espresso/CameraX tests. |
| Affected layer | Test |
| Risk | N/A this pass |
| Blast radius | CI time if added later |

---

## 6. Long / Complex Methods

Every item below should be considered in Phase 3. Extraction must use **business or UI-section names**, not `doStep1()`.

### 6.1 `CanMakanNavGraphViewModel.reloadFamilyContext`

| | |
| --- | --- |
| Location | `navigation/CanMakanNavGraphViewModel.kt` |
| Current responsibilities | Mutex; GET `/families/me`; load family profiles; GET active profile (404 via `CreateFamilyException`); resolve profile id vs SELF/fallback; map `ActiveProfileResponse` to `DietaryProfile` including initials; apply `ActiveProfileManager`; generic network error string |
| Why too complex | Multiple HTTP round-trips, 404 policy, id resolution, UI model mapping, and account-staleness checks in one suspend function |
| Proposed decomposition | `reloadFamilyContext()` → `loadMembership()` → `loadRosterIfPresent()` → `loadActiveProfileOrNull()` → `resolveActiveProfileId()` (already exists) → `publishFamilyUiState()` / `profileFromActiveResponse()` (already exists) |
| Criteria | Method length, business-logic distribution, layer separation |
| Risk | MEDIUM |

```text
reloadFamilyContext()
 ├── loadMembership(accountKey)
 ├── loadProfilesIfMember(me)
 ├── loadActiveProfileAllowing404()
 ├── resolveActiveProfileId(...)
 ├── publishShellFamilyState(...)
 └── applyActiveProfileId(...)
```

### 6.2 `CanMakanNavGraphViewModel.switchProfile` / `createFamilyCircle`

| | |
| --- | --- |
| Location | Same file |
| Current responsibilities | Session/profile validation; optimistic apply; PUT active profile or POST family; generation cancel; 403/409 mapping; rollback; `onSuccess` navigation callback |
| Why too complex | Two workflows plus optimistic concurrency in a navigation type |
| Proposed decomposition | Move to a `FamilyShellController` (or extend `FamilyProfileRepository` usage from a dedicated `FamilyShellViewModel`). Keep optimistic apply + rollback as named private methods (already partly there). |
| Criteria | Coupling, method length |
| Risk | MEDIUM |

### 6.3 `ScannerViewModel.processBarcode`

| | |
| --- | --- |
| Location | `features/product/scan/ScannerViewModel.kt` |
| Current responsibilities | Ownership check; validate HTTP; invalid-food branch; assess HTTP; parse verdict; fetch alternatives unless SAFE; map findings via `ProductFlagCopy`; set SUCCESS/ERROR |
| Why too complex | Full scan use-case plus mapping; mixed with Retrofit |
| Proposed decomposition | See F-MOB-02. ViewModel: `processBarcode` → `ensureOwner` → `runScan(owner, barcode)` repository result → `publishState`. Repository: validate, assess, alternatives. |
| Criteria | Layer separation, business-logic distribution, method length |
| Risk | MEDIUM |

```text
processBarcode()
 ├── requireActiveOwnedProfile()
 ├── scanRepository.validateAndAssess()
 ├── scanRepository.alternativesIfNeeded()
 └── publishVerdictOrError()
```

### 6.4 `NetworkModule.buildOkHttpClient` (retry interceptor)

| | |
| --- | --- |
| Location | `shared/di/NetworkModule.kt` |
| Current responsibilities | Proxy, cookies, bearer, authenticator, UA/ngrok headers, retry loop, timeouts; plus unused DEFAULT_BASE_URL nearby |
| Why too complex | DI module contains a custom retry protocol |
| Proposed decomposition | `RetryPolicyInterceptor` class with unit tests; `UserAgentInterceptor` or debug-only ngrok interceptor; `buildOkHttpClient` only assembles |
| Criteria | Method length, reliability |
| Risk | MEDIUM |

### 6.5 `RegistrationViewModel.createAccount`

| | |
| --- | --- |
| Location | `features/auth/RegistrationViewModel.kt` |
| Current responsibilities | Field validation (name/email/password policy); register; pending onboarding; auto-login; password clear |
| Why too complex | Validation + two network operations. Already partly split (`handleAccountCreated`). |
| Proposed decomposition | `validateAccountForm()` returning field errors; keep `createAccount` as orchestration. Optional: share email/password rules with `LoginViewModel` only if they are already identical (do not invent a validation framework). |
| Criteria | Method length |
| Risk | LOW |

### 6.6 `AuthenticatedDietaryOnboardingViewModel.saveRestrictions`

| | |
| --- | --- |
| Location | `features/dietaryprofile/setup/AuthenticatedDietaryOnboardingViewModel.kt` |
| Current responsibilities | Session/pending gates; empty-selection rule; name length; POST self profile; map `SelfProfileSetupResult`; switch active profile |
| Why too complex | Borderline; many result branches |
| Proposed decomposition | `validateReadyToSubmit()` + existing `completeWithProfile`. Do not extract each `when` arm into empty wrappers. |
| Criteria | Business-logic distribution |
| Risk | LOW |

### 6.7 `DietaryRestrictionViewModel.loadForOwner` / `onSave`

| | |
| --- | --- |
| Location | `DietaryRestrictionViewModel.kt` |
| Current responsibilities | Catalog + selections + dairy presentation + family `me` for edit permission; save with alias stripping |
| Why too complex | `loadForOwner` is a real use-case (load + authorize). Acceptable if `resolveEditAuthorization` stays extracted (already is). |
| Proposed decomposition | No further split required beyond sharing network error helper (F-MOB-09). |
| Criteria | Already reasonable |
| Risk | N/A |

### 6.8 Compose: `CanMakanNavGraph`, `ProfileDrawerContent`, `MatrixGrid`, `ProductDetailScreen`, `ScanFeedbackRow`, `ScannerScreen`

| | |
| --- | --- |
| Location | Listed UI files |
| Current responsibilities | Full screens / large sections |
| Why too complex | File and composable length; mixed camera vs chrome vs matrix vs feedback |
| Proposed decomposition | Section composables with stable parameters (F-MOB-03, F-MOB-08) |
| Criteria | Readability, method length |
| Risk | LOW–MEDIUM |

### 6.9 `FamilyProfileRepository.messageFromError`

| | |
| --- | --- |
| Location | `FamilyProfileRepository.kt` |
| Current responsibilities | Read error body; regex extract `message` |
| Why too complex | Not long; **wrong tool** (fragile parsing) |
| Proposed decomposition | Gson DTO `{ message: String? }` |
| Criteria | Correctness, maintainability |
| Risk | LOW |

---

## 7. Layer Separation Problems

```text
UI → Networking/business logic violation
```

**Mostly absent.** Screens use ViewModels. Exceptions are presentation rules that belong on the client: `RestrictionEditAuthorization` (button label/read-only), `DairyRestrictionPresentation` (catalog display), `ProductFlagCopy` (copy for flags). Keep those out of Spring.

```text
ViewModel → Retrofit (Controller/Service skipping Repository)
```

**`ScannerViewModel` / `ScanFeedbackViewModel` → `CanMakanApiService`.**  
Correction: Scan repository + mapper (F-MOB-02).

```text
Navigation ViewModel → multiple feature repositories (Service god object)
```

**`CanMakanNavGraphViewModel`.**  
Correction: feature-sized collaborators (F-MOB-01). Settings notification preference should live next to Settings or notifications, not only on the shell VM.

```text
Repository → UI/API concept issue
```

**`FamilyProfileRepository.profile` mapping of initials** actually lives in the NavGraph VM (`profileFromActiveResponse`) — UI-oriented initials in a shell VM. Move next to `FamilyProfileMapper` (already used for roster).

```text
Repository → inconsistent application logic
```

**`getFamilyRestrictionSummary` uses `Result` + HTTP code in exception messages; others throw.**  
Correction: F-MOB-07.

```text
DAO → Business logic
```

**No local DAO.** Do not add Room in this plan.

```text
Frontend → Backend business rules
```

**Do not** port dietary assessment, invitation authorization, or account deletion last-admin rules into Compose. Client messages (e.g. Settings `LAST_FAMILY_ADMIN_MESSAGE`) only map backend failure types — keep that mapping thin.

---

## 8. Coupling Problems

| Coupling | Issue | Proposed direction |
| --- | --- | --- |
| UI ↔ networking | Scan VMs depend on Retrofit types (`AssessmentRequest`, etc.) | Depend on repository / `VerdictDetail` |
| Shell VM ↔ family + diet + notifications | Constructor has six dependencies | Split by feature; shell collects flows |
| Settings screen ↔ NavGraph VM | Notification toggle state is not on `SettingsViewModel` | Move preference StateFlow to settings or a small `NotificationPreferenceStore` used by both badge and Settings |
| Family HTTP ↔ exception name | `CreateFamilyException` imported from notifications VM | Rename (F-MOB-07) |
| Tests ↔ `NetworkModule` overloads | Extra `provideOkHttpClient` overloads exist only for tests | Keep unless moving interceptor to its own class makes overloads unnecessary |
| Absolute/relative Retrofit | Scan vs recommendations | F-MOB-05 |
| Application ↔ heartbeat | `CanMakanApplication` EntryPoint for `SessionHeartbeat` | Acceptable; do not add a second DI pattern |
| Static helpers | `ProductFlagCopy`, `FamilyProfileMapper`, `DairyRestrictionPresentation` | Fine — no interfaces |

Do **not** introduce interfaces for every repository. Keep interfaces where Hilt `@Binds` already exists (`DietaryRestrictionRepository`, history/recommendation repos) and add one for scan because two ViewModels plus tests need a seam.

---

## 9. Refactoring Plan

Each step is independently reviewable. Do not change API contracts or UI look-and-feel unless a finding names a defect (F-MOB-06, F-MOB-04 timing).

### Phase 1 — Safety

#### REF-001

| Field | Value |
| --- | --- |
| Priority | P0 |
| Location | `CreateDependantProfileViewModel.kt` + new test |
| Current problem | Mutating VM untested (F-MOB-12) |
| Assessment criterion | Testability, correctness |
| Proposed refactoring | Characterization tests: empty name, missing relationship, success, account change resets state |
| Responsibilities before | Untested `create()` |
| Responsibilities after | Same production code; tests lock behaviour |
| Files affected | New `CreateDependantProfileViewModelTest.kt` |
| Dependencies / consumers | `CreateDependantProfileScreen` |
| Risk | LOW |
| Testing required | The new tests themselves |
| Expected benefit | Safe later family-repo exception rename |

#### REF-002

| Field | Value |
| --- | --- |
| Priority | P0 |
| Location | `CanMakanNavGraph.kt` |
| Current problem | Stale auto-read comment (F-MOB-11) |
| Assessment criterion | Maintainability, correctness of docs-in-code |
| Proposed refactoring | Comment-only fix |
| Responsibilities before / after | Unchanged code |
| Files affected | `CanMakanNavGraph.kt` |
| Risk | LOW |
| Testing required | None |
| Expected benefit | Prevents accidental product change |

#### REF-003

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `ScannerViewModel.parseVerdict` (or mapper after REF-006) |
| Current problem | Unknown verdict → WARNING (F-MOB-06) |
| Assessment criterion | Correctness |
| Proposed refactoring | Treat unknown as ERROR; test `ScannerViewModelTest` |
| Responsibilities before | `parseVerdict` always returns an enum |
| Responsibilities after | Known enums only; else error state |
| Files affected | `ScannerViewModel.kt` and/or mapper; tests |
| Risk | LOW–MEDIUM |
| Testing required | Unknown string, mixed case SAFE/UNSAFE |
| Expected benefit | No false Warning |

Also in Phase 1: run existing `testDebugUnitTest` as a baseline (no code change required to “add” that command in CI — it already runs).

### Phase 2 — Layer Separation

#### REF-004

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `features/product/scan/` (new repository), `NetworkModule.kt` |
| Current problem | F-MOB-02 |
| Assessment criterion | Layer separation, low coupling, testability |
| Proposed refactoring | `ScanRepository` + `ServerScanRepository`; bind in a small Hilt module; DTOs stay in `shared/network` or move next to the repository **without** renaming JSON fields |
| Responsibilities before | ViewModel: HTTP + mapping + UI state |
| Responsibilities after | Repository: HTTP + mapping; ViewModel: ownership + `ScanProcessState` |
| Files affected | New repo/module; `ScannerViewModel`; `ScanFeedbackViewModel`; `NetworkModule` (optional keep providing `CanMakanApiService` for the repo only) |
| Dependencies / consumers | Scanner, Product detail feedback, unit tests |
| Risk | MEDIUM |
| Testing required | Move/adapt `ScannerViewModelTest` and `ScanFeedbackViewModelTest`; add repository tests with fake Retrofit responses (same style as `ServerScanHistoryRepositoryTest`) |
| Expected benefit | Scan matches history/recommendation layering |

#### REF-005

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `CanMakanNavGraphViewModel.kt`, family/notifications packages |
| Current problem | F-MOB-01 |
| Assessment criterion | Layer separation, low coupling, method length |
| Proposed refactoring | Incremental extract (do not rewrite navigation in one commit): (1) `FamilyProfileMapper`/helper for `profileFromActiveResponse`; (2) `FamilyContextLoader` or `FamilyShellViewModel` for reload/switch/create; (3) notification badge + preference methods to `NotificationBadgeCoordinator` or `SettingsViewModel` + shared store; (4) `PendingVerdictStore` (`MutableStateFlow` + account clear). Shell VM delegates. |
| Responsibilities before | One VM does all of the above |
| Responsibilities after | Shell exposes existing `StateFlow` names if possible so `CanMakanNavGraph` stays stable, **or** graph collects from 2–3 VMs with `hiltViewModel()` activity scope documented |
| Files affected | Nav VM, tests, possibly Settings wiring |
| Dependencies / consumers | Entire authenticated shell |
| Risk | HIGH if done as one blob — **split into 2–3 PRs/commits** |
| Testing required | Port slices of `CanMakanNavGraphViewModelTest` with each extract |
| Expected benefit | Family vs notifications changes no longer collide |

#### REF-006

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `FamilyProfileRepository.kt` |
| Current problem | F-MOB-07 |
| Assessment criterion | Readability, maintainability, layer separation |
| Proposed refactoring | Rename exception; Gson error body; unify `getFamilyRestrictionSummary` with throw style; update ViewModels/tests |
| Responsibilities before | Mixed Result/throw + regex |
| Responsibilities after | HTTP adapter + typed errors |
| Files affected | Family repo, summary VM, invite/notifications tests |
| Risk | LOW–MEDIUM |
| Testing required | Existing `FamilyProfileRepositoryTest`, summary VM test |
| Expected benefit | Clearer DAO-equivalent boundary |

#### REF-007

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `CanMakanApiService.kt` |
| Current problem | F-MOB-05 |
| Assessment criterion | Maintainability, correctness |
| Proposed refactoring | Relative paths consistent with other services; contract test |
| Responsibilities before | Mixed path styles |
| Responsibilities after | Single base-URL rule |
| Files affected | API interface + test |
| Risk | MEDIUM |
| Testing required | Retrofit path resolution test against `BuildConfig.BASE_URL` pattern |
| Expected benefit | Safer `BASE_URL` changes |

### Phase 3 — Complexity Reduction

#### REF-008

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `NetworkModule.kt` |
| Current problem | F-MOB-04, long interceptor |
| Assessment criterion | Method length, reliability, performance |
| Proposed refactoring | Extract interceptors; remove blocking sleep; keep retry counts and 4xx policy |
| Responsibilities before | Module builds client and encodes retry protocol |
| Responsibilities after | Named interceptors; module provides |
| Files affected | New interceptor class(es), `NetworkRetryInteractionTest` |
| Risk | MEDIUM |
| Testing required | Retry/no-retry header tests updated for no sleep (use virtual time or immediate retry) |
| Expected benefit | No blocked OkHttp threads |

#### REF-009

| Field | Value |
| --- | --- |
| Priority | P1 |
| Location | `CanMakanNavGraph.kt` |
| Current problem | F-MOB-03 |
| Assessment criterion | Method length, readability |
| Proposed refactoring | Split shell vs destinations; keep route constants |
| Responsibilities before | One composable registers everything |
| Responsibilities after | `CanMakanNavGraph` composes `AuthenticatedDrawerShell` + `AuthenticatedNavHost` |
| Files affected | `navigation/` new files; graph |
| Risk | MEDIUM |
| Testing required | Manual navigation checklist (Phase 6); existing VM tests unchanged |
| Expected benefit | Route changes are local |

#### REF-010

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Large Compose files (F-MOB-08) |
| Current problem | Long composables |
| Assessment criterion | Method length, readability |
| Proposed refactoring | Extract section composables; preserve UI |
| Responsibilities before | Monolithic screens |
| Responsibilities after | Same tree, named children |
| Files affected | Product detail, scanner, matrix, drawer, dietary UIs |
| Risk | LOW–MEDIUM |
| Testing required | Manual UI pass |
| Expected benefit | Scroll-less understanding of each section |

#### REF-011

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | `RegistrationViewModel.createAccount` |
| Current problem | Validation mixed with submit |
| Assessment criterion | Method length |
| Proposed refactoring | `validateAccountForm()` only if tests stay green with identical messages |
| Files affected | `RegistrationViewModel.kt`, `RegistrationViewModelTest` |
| Risk | LOW |
| Testing required | Existing registration tests |
| Expected benefit | Submit method readable in one screen |

### Phase 4 — Coupling Reduction

#### REF-012

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Authenticated screens + `CanMakanNavGraph` |
| Current problem | F-MOB-09 nav callback duplication |
| Assessment criterion | Low coupling, maintainability |
| Proposed refactoring | `AuthenticatedChromeCallbacks` data class passed into screens |
| Responsibilities before | 5+ lambdas per screen |
| Responsibilities after | One chrome object + screen-specific callbacks |
| Files affected | Most `*Screen.kt`, nav graph |
| Risk | LOW (signature churn) |
| Testing required | Compile; no JVM UI tests |
| Expected benefit | Chrome changes in one type |

#### REF-013

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Settings + NavGraph VM |
| Current problem | Preference toggle owned by shell |
| Assessment criterion | Low coupling |
| Proposed refactoring | After REF-005, Settings reads/writes the same preference source as the badge |
| Files affected | `SettingsViewModel`, Settings route, tests |
| Risk | LOW |
| Testing required | Move relevant cases from NavGraph VM test |
| Expected benefit | Settings feature is cohesive |

#### REF-014

| Field | Value |
| --- | --- |
| Priority | P3 |
| Location | `NetworkModule` headers |
| Current problem | F-MOB-14 |
| Assessment criterion | Security (hygiene), maintainability |
| Proposed refactoring | Debug-only ngrok header; honest User-Agent unless blocked by a documented proxy |
| Files affected | Interceptor, possibly `DedicatedRefreshNetworkTest` (refresh client should remain header-light) |
| Risk | LOW–MEDIUM |
| Testing required | Header presence tests for debug vs release if `BuildConfig` is used |
| Expected benefit | Production traffic not impersonating Chrome/ngrok |

### Phase 5 — Maintainability

#### REF-015

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Scanner, family restrictions route |
| Current problem | F-MOB-10 |
| Assessment criterion | Correctness, reliability |
| Proposed refactoring | `collectAsStateWithLifecycle` |
| Files affected | `ScannerScreen.kt`, `CanMakanNavGraph.kt` |
| Risk | LOW |
| Testing required | Manual scanner background/foreground |
| Expected benefit | Lifecycle-safe collection |

#### REF-016

| Field | Value |
| --- | --- |
| Priority | P2 |
| Location | Dietary UIs + ViewModels |
| Current problem | Duplicate `SelectableOptionCard` and network `when` |
| Assessment criterion | Maintainability, readability |
| Proposed refactoring | Shared card; `userMessageForNetworkFailure` |
| Files affected | Two dietary UI files; three ViewModels |
| Risk | LOW |
| Testing required | Restriction/history VM tests still match strings **or** update tests to shared constants |
| Expected benefit | One place to fix timeout copy |

#### REF-017

| Field | Value |
| --- | --- |
| Priority | P3 |
| Location | Sample data, sample repo, ngrok constant, biometric permission, README, Gson lenient |
| Current problem | F-MOB-13, F-MOB-15 |
| Assessment criterion | Maintainability, readability |
| Proposed refactoring | Delete dead code after grep; fix README `applicationId`; remove unused permission; Gson: add a test with a production-shaped payload then tighten if safe |
| Files affected | Listed sample/network/manifest/README |
| Risk | LOW (Gson: MEDIUM — do last and revert if parse tests fail) |
| Testing required | Full `testDebugUnitTest`; optional Gson fixture |
| Expected benefit | Prototype leftovers gone; docs match Gradle |

### Phase 6 — Validation

1. `.\gradlew.bat :app:testDebugUnitTest` from `client/mobile`
2. `.\gradlew.bat :app:assembleDebug` (and release assemble only if `BASE_URL` is available — do not weaken fail-closed release checks)
3. Manual Android: login/register, scan (SAFE and non-SAFE + alternatives), history → detail, feedback, family create/invite/dependant, restriction sheet read-only vs admin, notifications badge, settings delete-account messaging, invitation deep link smoke if feasible
4. Confirm Retrofit scan/recommend/feedback URLs against running backend (no contract change)
5. Confirm web and backend **untouched**

---

## 10. Testing Plan

**Before risky extracts (REF-004, REF-005, REF-008)**

- Run current `CanMakanNavGraphViewModelTest`, `ScannerViewModelTest`, `ScanFeedbackViewModelTest`, `NetworkRetryInteractionTest`, `DedicatedRefreshNetworkTest`, `FamilyProfileRepositoryTest`.
- Add REF-001 dependant VM tests first.

**New / extended tests**

| Area | Tests |
| --- | --- |
| Scan repository | Fake `CanMakanApiService`: validate fail, assess fail, alternatives fail-soft, feedback HTTP codes |
| Unknown verdict | ERROR not WARNING |
| Retry interceptor | No `Thread.sleep`; still retries IOException; still skips 4xx and `X-CanMakan-No-Retry` |
| Retrofit paths | All scan-related methods resolve under `/api/` base |
| Family errors | Gson `message` extraction; summary VM on throw |
| Chrome callbacks | Compile-level only |
| Gson strict | One representative `AssessmentResponse` / family `me` JSON |

**Do not require** CameraX instrumented tests for this plan. Optional smoke later (F-MOB-17).

---

## 11. API / Database Impact

| Surface | This plan |
| --- | --- |
| API contracts | **No change.** Path strings on the client must still hit existing Spring routes. |
| Database schema | **No change.** |
| Database behaviour | **No change.** |
| React consumers | **No change.** |
| Android consumers | Internal module structure and some user-visible strings only where defects are fixed (unknown verdict → error; possibly unified network error wording; retry timing). Invite/scan/family JSON unchanged. |

---

## 12. Files Expected to Change

| File | Planned change | Assessment criterion | Risk |
| ---- | -------------- | -------------------- | ---- |
| `navigation/CanMakanNavGraphViewModel.kt` | Split responsibilities (REF-005) | Layer separation, coupling | HIGH |
| `navigation/CanMakanNavGraphViewModelTest.kt` | Split/port tests | Testability | MEDIUM |
| `navigation/CanMakanNavGraph.kt` | Shell vs routes; lifecycle collect; comment | Method length, correctness | MEDIUM |
| New `navigation/` or `family/` loader types | Family shell extract | Layer separation | MEDIUM |
| New `features/product/scan/data/*ScanRepository*` | HTTP + mapping | Layer separation | MEDIUM |
| `ScannerViewModel.kt` | Use repository; unknown verdict | Layer separation, correctness | MEDIUM |
| `ScanFeedbackViewModel.kt` | Use repository; optional ownership cancel | Layer separation | LOW |
| `ScannerViewModelTest.kt` / `ScanFeedbackViewModelTest.kt` | Fakes vs API | Testability | LOW |
| `shared/network/CanMakanApiService.kt` | Relative paths | Maintainability | MEDIUM |
| `shared/di/NetworkModule.kt` | Interceptors; remove dead URL | Method length, reliability | MEDIUM |
| New retry/UA interceptor class | Extract from module | Method length | MEDIUM |
| `features/family/data/FamilyProfileRepository.kt` | Exception rename, Gson errors | Readability | LOW |
| `FamilyRestrictionSummaryViewModel.kt` | Align error style | Consistency | LOW |
| `family/data/FamilyProfileMapper.kt` | Initials mapping from Nav VM | Layer separation | LOW |
| `CreateDependantProfileViewModelTest.kt` (new) | Characterization | Testability | LOW |
| `ProductDetailScreen.kt` and splits | Section composables | Method length | LOW |
| `ScannerScreen.kt` | Camera extract; lifecycle collect | Method length | LOW |
| `FamilyRestrictionSummaryScreen.kt` | `MatrixGrid` file | Method length | LOW |
| `ProfileDrawerContent.kt` | Section split | Method length | LOW |
| Dietary sheet + onboarding UI | Shared `SelectableOptionCard` | Duplication | LOW |
| `RegistrationViewModel.kt` | Optional `validateAccountForm` | Method length | LOW |
| `SettingsViewModel.kt` | Preference ownership | Coupling | LOW |
| `AndroidManifest.xml` | Remove unused biometric | Hygiene | LOW |
| `client/mobile/README.md` | applicationId, sample-data status | Readability | LOW |
| `ProductSampleData.kt` / sample restriction repo | Delete if unused | Dead code | LOW |
| `docs/code-quality/README.md` | Link this document | Docs | LOW |

---

## 13. Changes NOT Recommended

- **Do not** introduce Gradle feature modules or a brand-new architecture (MVI/Orbit/Clean-Architecture use-case folders) for this assessment.
- **Do not** add Room or a local dietary rules engine.
- **Do not** create interfaces for every class (`FamilyProfileRepository` can stay concrete).
- **Do not** redesign supplied Compose visuals, theme tokens, or mascot usage.
- **Do not** replace EncryptedSharedPreferences with custom crypto because the API is deprecated.
- **Do not** implement client-side RBAC beyond current UX gating (F-MOB-16).
- **Do not** change Spring APIs to “fix” client-side full history lists (same stance as web F-WEB-06).
- **Do not** unify every ViewModel error into one sealed hierarchy in this pass.
- **Do not** remove `AuthAccountKey` / generation guards to “simplify” — they are a correctness feature.
- **Do not** rewrite CameraX/ML Kit (`BarcodeAnalyzer`) except lifecycle collection around it.
- **Do not** enable Firebase/FCM solely because `google-services` is applied; out of scope unless a separate product task.
- **Do not** silently change invite deep-link manifest placeholders or release `BASE_URL` validation.

---

## 14. Expected Assessment Improvement

| Criterion | How this plan helps |
| --- | --- |
| **Readability** | Named scan repository, family exception, section composables, dead sample/ngrok/README cleanup |
| **Maintainability** | Family vs notifications vs scan changes land in feature packages; shared chrome and option cards |
| **Layer separation** | ViewModels stop calling Retrofit for scan/feedback; HTTP error parsing stays in the family adapter; UI mapping (`initials`) joins `FamilyProfileMapper` |
| **Low coupling** | Shell VM fan-out reduced; Settings owns preference UX; screens depend on a chrome callbacks object rather than copying five lambdas |
| **Reasonable method length** | `reloadFamilyContext`, `processBarcode`, OkHttp retry, NavHost, `MatrixGrid`, `ScanFeedbackRow` become explainable units |
| **Business-logic distribution** | Scan pipeline orchestration stays in the VM; HTTP and DTO mapping move down; dietary **assessment** stays on the server; client keeps only presentation (dairy labels, edit hints, flag copy) |
| **Reduced complexity** | Retry policy in a testable interceptor; unknown verdicts fail closed; one error style in the family repository |

Overall target after implementation: about **8/10**, with security staying at least as strong as today (encrypted session, dedicated refresh client, BASIC logs, release HTTPS). The remaining gap vs 10/10 is intentional: no instrumented UI suite, no pagination APIs, and a still-rich authenticated shell — appropriate for a student-team Android client that must stay simple.

---

## Changes checklist

Use after an approved implementation pass (all **unchecked** at plan publication).

- [ ] REF-001 Dependant profile VM tests
- [ ] REF-002 Notifications comment
- [ ] REF-003 Unknown verdict → ERROR
- [ ] REF-004 Scan repository
- [ ] REF-005 Split nav shell ViewModel
- [ ] REF-006 Family HTTP errors
- [ ] REF-007 Retrofit path consistency
- [ ] REF-008 Retry interceptor without `Thread.sleep`
- [ ] REF-009 Split `CanMakanNavGraph`
- [ ] REF-010 Split large screens
- [ ] REF-011 Registration validation extract
- [ ] REF-012 Chrome callbacks type
- [ ] REF-013 Settings notification preference ownership
- [ ] REF-014 Debug ngrok header / User-Agent
- [ ] REF-015 `collectAsStateWithLifecycle`
- [ ] REF-016 Shared dietary card + network messages
- [ ] REF-017 Dead code, README, permission, Gson
- [ ] Phase 6 unit tests + assembleDebug + manual scan/family/auth

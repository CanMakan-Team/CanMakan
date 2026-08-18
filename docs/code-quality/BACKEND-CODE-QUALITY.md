# Backend Code Quality Assessment

Living document for the Spring Boot backend under `server/backend/src/main`.  
Update the **Changes checklist** when work lands; keep findings status notes current.

**Location:** `docs/code-quality/BACKEND-CODE-QUALITY.md` (linked from [`docs/code-quality/README.md`](README.md) and [`server/backend/README.md`](../../server/backend/README.md))  
**Scope:** `com.canmakan.backend` (~313 Java files + SQL/resources). Out of scope: React (`client/web`), Android (`client/mobile`), MySQL schema redesign.  
**Clients:** Web and Android share auth, family, and dietary/profile contracts. Proposed refactors in this plan must **not** rename public JSON fields or change REST paths unless a finding explicitly says otherwise.

**Status of this document:** Assessment and refactoring plan. **F01–F14, F16–F17, F19–F20** are implemented (see checklist). F15 (intra-family profile access) is documented with no behaviour change. F18 (`@PreAuthorize`) is deferred.

---

## 1. Scope Reviewed

| Area | Paths |
| --- | --- |
| Feature packages | `auth`, `family`, `dietaryprofile`, `product` (scan / verdict / recommendation / assessment), `admin`, `analytics`, `knowledgebase`, `user`, `notification`, `session`, `integration` |
| Cross-cutting | `shared` (security, exceptions, persistence config) |
| Config / SQL | `application.properties`, `application-dev.properties`, `src/main/resources/*.sql` |
| Tests | `server/backend/src/test/java` (`mvn verify` / `mvn test`) |

Largest service modules at review time (approximate; used to score method complexity):

| Type | Role |
| --- | --- |
| `family.FamilyService` | UC6–UC11 facade (was a god class; now delegates to `family.service.*`) |
| `product.assessment.AssessmentOrchestrator` | Scan → verdict pipeline (explicitly **not** rewritten) |
| `product.recommendation.RecommendationService` | Substitute ranking / discovery |
| `family.service.FamilyRosterService` | Roster and restriction-summary reads |
| `family.service.FamilyInvitationService` | Invite create/accept/decline |

---

## 2. Architecture Overview

Feature-first vertical packages with classic Spring layering:

`HTTP → Controller → Service → Repository → MySQL`  
External: Open Food Facts, EAN-Search, OpenAI, Tavily, optional ML ranker.

**Strengths:** DTOs at API edges; constructor injection; `open-in-view=false`; JWT + HttpOnly refresh cookie; service-layer family authz; broad tests; parameterized native SQL; scan history `join fetch`.

**Post-refactor additions:** Spring `dev`/`prod` profiles; auth rate limiting; `FamilyRosterService` / `FamilyInvitationService` / related collaborators behind `FamilyService` facade; batched roster queries; SAFE-barcode query for recommendations; `@Valid` on scan DTOs; LAZY associations + narrowed cascades; dead Tier-B bean / stub removed; size-based packaging convention (F19).

**Layer mapping for assessors**

| Assessor term | Spring equivalent | Must not contain |
| --- | --- | --- |
| Controller | `@RestController` in the feature package | Persistence, dietary evaluation, scan verdict rules |
| Service | Feature `*Service` (authz, orchestration, transactions) | HTTP mapping details beyond DTO in/out; raw JDBC string concatenation |
| Repository / DAO | Spring Data / native `@Query` | HTTP status codes, CORS, JWT parsing |
| DTO | Request/response records at the API edge | JPA associations, Hibernate lazy proxies |
| Entity / model | JPA types under `model/` (or product sub-slices) | REST field names invented for the UI |

Do not flatten features into a single global `controller/` / `service/` tree. Do not invent a second authorization engine in controllers when `FamilyAuthorizationService` already owns membership checks.

---

## 3. Assessor Criteria Evaluation

Assessor criteria for this Spring Boot codebase. Scores in **§4** add backend-specific categories (security, performance, API design).

| Criterion | Before plan | After implementation | Main issues (before) | Plan items |
| --- | ---: | ---: | --- | --- |
| Readability | 7/10 | 8/10 | `FamilyService` mixed roster, invite, and history; packaging mixed nest vs flat | F06, F17, F19 |
| Maintainability | 5/10 | 7/10 | Always-on seed/SQL debug; god class; dead Tier-B; uneven packages | F01, F04, F06, F12, F13, F19, F20 |
| Layer separation | 7/10 | 8/10 | Scan validation in controller-style manuals; entities EAGER/cascade too wide | F09, F10, F11 |
| Low coupling | 6/10 | 7.5/10 | Family service owned too many collaborators; roster N+1; recommendation loaded unbounded scans | F06, F07, F08, F12 |
| Reasonable method length | 5/10 | 7/10 | Family god class; assessment/recommendation pipelines remain large by design | F06 (pipeline rewrite out of scope) |
| Business-logic distribution | 7/10 | 8/10 | Authz already in services; scan bean validation was manual; SQL seed in all profiles | F01, F09, F13, F15 documented |
| Complex method refactoring | 5/10 | 7/10 | Family split done; assessment orchestrator left intact | F06; assessment rewrite not recommended |

---

## 4. Quality Scorecard

Scores are for `server/backend` only. **Before plan** is the review snapshot. **After implementation** is F01–F20 except deferred F15/F18.

Do not inflate: security and architecture recovered the most. Error handling, performance, and maintainability remain 7/10 because F15/F18 are open, the assessment pipeline was not rewritten, and catch narrowing (F16) was practical rather than exhaustive. Overall **8/10** is a round-up of ~7.7 across the ten rows below, weighted toward the CRITICAL/HIGH security close-out.

| Category | Before plan | Target after plan | After implementation |
| --- | ---: | ---: | ---: |
| Architecture | 7/10 | 8/10 | 8/10 |
| Correctness | 7/10 | 8/10 | 8/10 |
| Readability | 7/10 | 8/10 | 8/10 |
| Maintainability | 5/10 | 7/10 | 7/10 |
| Modularity | 6/10 | 8/10 | 8/10 |
| Testability | 7/10 | 8/10 | 8/10 |
| Error Handling | 6/10 | 7/10 | 7/10 |
| Security | 5/10 | 8/10 | 8/10 |
| Performance | 6/10 | 7/10 | 7/10 |
| API Design | 7/10 | 8/10 | 8/10 |
| Overall | 6.5/10 | 8/10 | 8/10 |

| Scorecard row | Moved by |
| --- | --- |
| Architecture / Modularity | F06 facade, F17 hygiene, F19 size-based packages, F20 import-only nest |
| Correctness | F07 batch reads, F09 `@Valid`, F10–F11 fetch/cascade, F14 tests |
| Readability | F06, F17, F19, F20 |
| Maintainability | F01/F04/F13 profiles, F06, F12, F19, F20 |
| Testability | F14 characterization tests; existing `FamilyServiceTest` / HTTP tests kept |
| Error Handling | F16 narrower catches; F09 bean validation |
| Security | F01, F02, F03, F04, F05, F13 |
| Performance | F07, F08 |
| API Design | F05 may return **429**; otherwise REST/JSON unchanged |

---

## 5. Findings (F01–F20)

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| F01 | CRITICAL | Always-on SQL seed + demo admins | Done — gated to `dev` |
| F02 | HIGH | Permissive CORS origin patterns with credentials | Done — patterns on `dev` only |
| F03 | HIGH | Invitation tokens logged at INFO | Done |
| F04 | HIGH | Hibernate SQL DEBUG always on | Done — `dev` only |
| F05 | HIGH | No authentication rate limiting | Done |
| F06 | HIGH | `FamilyService` god class | Done — facade + collaborators |
| F07 | HIGH | N+1 on family roster / restriction summary | Done — batch fetch |
| F08 | HIGH | Recommendation unbounded prior scans | Done — SAFE barcode query |
| F09 | MEDIUM | Scan APIs: manual validation vs `@Valid` | Done |
| F10 | MEDIUM | `CascadeType.ALL` on Family → profiles | Done — PERSIST/MERGE |
| F11 | MEDIUM | Default EAGER `@ManyToOne` on DietaryProfile | Done — LAZY + fetch query |
| F12 | MEDIUM | Dead Tier-B LLM + `IngredientResolverStub` | Done — stub removed; Tier-B not a bean |
| F13 | MEDIUM | `ddl-auto=update` | Done — `validate` base / `update` on `dev` |
| F14 | MEDIUM | Missing DietaryProfile HTTP + authz unit tests | Done |
| F15 | MEDIUM | Intra-family profile access (product policy) | Documented — not changed |
| F16 | LOW | Broad `catch (Exception)` | Done — narrowed where practical |
| F17 | LOW | Package hygiene / naming | Done — IngredientEntity→model, UserPreference→user, handlers→exception, Lombok/DTO cleanup |
| F18 | INFO | No `@PreAuthorize` method security | Deferred |
| F19 | LOW | Feature packaging inconsistency (three layout styles) | Done — convention + incremental tidy (P1–P5) |
| F20 | LOW | Residual types still at feature roots after F19 (`auth` properties, `family` relationship helper, `user` projections/service, `notification` repo/service) | Done — package moves + import rewrite only |

### Finding detail (CRITICAL / HIGH)

#### F01

| Field | Value |
| --- | --- |
| Severity | CRITICAL |
| File | `application.properties`, `application-dev.properties`, `04_roles_users.sql` |
| Class/component | Spring SQL init |
| Current problem | Seed scripts (including demo admins) ran in every environment. |
| Why it matters | Production could load known demo accounts and overwrite local data assumptions. |
| Proposed solution | `spring.sql.init.mode=never` by default; `always` on `dev` only. |
| Affected layer | Configuration / security |
| Assessment criterion | Security, maintainability |
| Risk | MEDIUM if `prod` is mis-profiled |
| Blast radius | Local demo data; deploy must set `SPRING_PROFILES_ACTIVE=prod` |

#### F02

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `shared/security/CorsConfig.java`, CORS properties |
| Class/component | CORS filter |
| Current problem | Broad origin **patterns** with `allowCredentials=true` (LAN wildcards) were suitable for local Vite, not production. |
| Why it matters | Credentialed cross-origin requests from unexpected hosts. |
| Proposed solution | Exact origin allow-list in base properties; patterns only on `dev`. |
| Affected layer | HTTP security |
| Assessment criterion | Security |
| Risk | LOW if LAN apps use `dev` |
| Blast radius | Browser web client; Android Retrofit typically sends no Origin |

#### F03

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `family/service/FamilyInvitationService.java` (invite create/accept path) |
| Class/component | Invitation logging |
| Current problem | Raw invitation tokens appeared in INFO logs. |
| Why it matters | Tokens are capability URLs; logs are a secret store. |
| Proposed solution | Log masked email / non-secret ids only (`FamilyDisplayUtil.maskEmail`). |
| Affected layer | Service |
| Assessment criterion | Security |
| Risk | LOW |
| Blast radius | Invite tests that asserted log text |

#### F04

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | JPA `show-sql` / Hibernate logging properties |
| Class/component | Logging |
| Current problem | SQL DEBUG in all profiles. |
| Why it matters | PII and schema noise in production logs. |
| Proposed solution | Hibernate SQL DEBUG on `dev` only. |
| Affected layer | Configuration |
| Assessment criterion | Security, maintainability |
| Risk | LOW |
| Blast radius | Log volume in local runs |

#### F05

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `shared/security/AuthRateLimitFilter.java` |
| Class/component | Filter on login / register / refresh |
| Current problem | No limit on credential-guessing endpoints. |
| Why it matters | Online password attacks against `/api/auth/*`. |
| Proposed solution | In-memory rate limit; **429** when exceeded. |
| Affected layer | Security filter |
| Assessment criterion | Security, API design |
| Risk | LOW (clients must handle 429) |
| Blast radius | Auth HTTP tests; documented 429 |

#### F06

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `family/FamilyService.java` |
| Class/component | `FamilyService` + `family.service.*` |
| Current problem | One class owned roster, invitations, history, and display helpers. |
| Why it matters | Unreasonable method length; high coupling; hard to test one UC without the rest. |
| Proposed solution | Thin facade at feature root; `FamilyRosterService`, `FamilyInvitationService`, `FamilyScanHistoryService`, `FamilyActiveProfileService`, `FamilyAuthorizationService`. |
| Affected layer | Service |
| Assessment criterion | Modularity, method length, low coupling, readability |
| Risk | MEDIUM (delegation bugs) |
| Blast radius | `FamilyServiceTest`, `FamilyInvitationServiceTest`, family HTTP tests |

#### F07

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `family/service/FamilyRosterService.java`, family repositories |
| Class/component | Roster / restriction summary |
| Current problem | Per-member lazy loads (N+1) on household reads. |
| Why it matters | Latency grows with roster size; extra round-trips under `open-in-view=false`. |
| Proposed solution | Batch fetch (`findAllById` and equivalent query grouping). |
| Affected layer | Repository / service |
| Assessment criterion | Performance, correctness |
| Risk | MEDIUM (must keep summary rows identical) |
| Blast radius | Roster and restriction-summary tests |

#### F08

| Field | Value |
| --- | --- |
| Severity | HIGH |
| File | `product/recommendation/` (history / discovery queries) |
| Class/component | Recommendation candidate load |
| Current problem | Prior scans loaded without a SAFE-barcode bound. |
| Why it matters | Memory and query cost on recommendation. |
| Proposed solution | Query SAFE barcodes only for that candidate set. |
| Affected layer | Repository / service |
| Assessment criterion | Performance |
| Risk | MEDIUM (ranking input set changes by design: fewer rows) |
| Blast radius | Recommendation tests |

### Finding detail (MEDIUM / LOW / INFO) — compact

| ID | Criterion | Outcome |
| --- | --- | --- |
| F09 | Layer separation, API design | Scan request DTOs use `@Valid` instead of ad-hoc checks |
| F10 | Correctness | Family → profiles cascade narrowed to PERSIST/MERGE |
| F11 | Correctness, performance | DietaryProfile `@ManyToOne` LAZY + explicit fetch |
| F12 | Low coupling, maintainability | Stub removed; Tier-B LLM not a default bean |
| F13 | Security, maintainability | Base `ddl-auto=validate`; `update` on `dev` |
| F14 | Testability | DietaryProfile HTTP + authz tests added |
| F15 | Business-logic distribution | Policy unchanged; locked by `FamilyAuthorizationServiceTest` |
| F16 | Error handling | External `catch (Exception)` narrowed where practical |
| F17 | Readability, modularity | Types moved to `model` / `user` / `*.exception` |
| F18 | Security | Optional method security; not in this pass |
| F19 | Maintainability, modularity | Size-based package convention; family root thinned |
| F20 | Maintainability, modularity, layer separation | Import-only nest of leftover root types; REST/JSON unchanged |

---

## 6. Feature packaging evaluation

Feature-first packaging is correct for this backend. The remaining issue at review was **uneven nesting** of technical layers (`dto/`, `exception/`, `model/`, `repository/`, `service/`) across features. F19 documented the size-based rule and tidied packages incrementally; it did **not** force one global deep tree.

### Three styles in use

| Style | Packages | Pattern |
| --- | --- | --- |
| Fully nested | `dietaryprofile`, `admin`, `analytics` | Root controller (where applicable); nested `dto/`, `exception/`, `model/`, `repository/`, `service/` |
| Nested layers; thin root | `auth`, `family`, `user`, `notification` | Nested `dto/` / `exception/` / `model/` / `repository/` / `service/` (and `config/` where properties live); controllers and thin facades at feature root |
| Sub-slice or flat | `product.*`, `session`, `integration` | Product: co-located types under `scan/`, `verdict/`, `recommendation/`, `assessment/`, `model/`. `session` and `integration` stay flat (under type-count threshold) |

### Layer folder assessment

| Folder | Assessment |
| --- | --- |
| `dto/` | Strong for HTTP APIs (`auth`, `family`, `dietaryprofile`, `admin`, `analytics`, `user`). Product keeps DTOs in-slice — acceptable. |
| `exception/` | Good when present. Prefer for any feature with multiple domain exceptions or a dedicated `@RestControllerAdvice`. |
| `model/` | Best home for JPA entities. Avoid entities under `repository/`. |
| `repository/` | Clear when nested; product keeps repos in-slice. |
| `service/` | Nested in dietaryprofile/admin/analytics/`family`/`auth`/`user`/`notification`; thin facade may stay at feature root (`FamilyService`, feature controllers). |

### Strengths
- Clear feature ownership (family vs dietary vs product vs admin)
- HTTP DTOs usually separated from JPA entities
- Product sub-slices justified by size
- `shared` stays mostly technical
- Size-based convention documented in backend + feature READMEs (F19)

### Residual notes (not blockers)
- `session` stays flat by design (small)
- Product keeps in-slice DTOs by design (no mass nest)
- `product.recommendation` uses capability folders (`catalog`, `filter`, `ranking`, `discovery`, `history`, `dto`) under the slice

### Explicitly not recommended (packaging)
- Mass-moving `product.*` into nested `dto/` trees (high churn, low value)
- Merging `ScanProduct` / `CatalogProduct` into one entity
- Flattening nested packages (dietaryprofile/admin) to match product, or forcing product to match dietaryprofile, in one pass

---

## 7. Packaging Plan (F19) — completed

Use **size-based conventions**, not one global deep tree. Prefer convention + incremental tidy when a package is already being touched.

### Target convention

| Feature size | Layout |
| --- | --- |
| Large / many types (`family`, `auth`, `dietaryprofile`, `admin`) | Nest `dto/`, `exception/`, `model/`, `repository/`; nest `service/` **or** keep a thin root (controllers + facade only) |
| Multi-capability domain (`product`) | Keep sub-slices; co-locate DTO/service/repo inside each slice |
| Small (`notification`, `session`, `integration`) | Flat OK until ~8–10 types; then add `dto/` + `exception/` first |

### Implementation steps

#### Step P1 — Document the convention (LOW risk) — **done**
**Files:** `server/backend/README.md`, feature package READMEs (`family`, `auth`, `product`, `user`, `admin`, `notification`, `session`, …)  
**Changes:** Describe the three styles and the size-based target rule; refresh stale “partial/foundation” status tables where packaging docs lag.  
**Purpose:** Stop accidental divergent layouts on new code.  
**Assessment criterion:** Maintainability, modularity  
**Risk:** LOW

#### Step P2 — Thin `family/` root (MEDIUM risk) — **done**
Collaborators live in `family.service`; invite/email properties in `family.config`. Controllers + thin `FamilyService` facade remain at root. REST contracts unchanged.  
**Assessment criterion:** Modularity, method length, low coupling

#### Step P3 — Align `user` packaging (LOW–MEDIUM) — **done**
Introduced `user.model` + `user.repository` for `UserAccount`, `UserPreference`, and matching repos.  
**Assessment criterion:** Modularity, layer separation

#### Step P4 — Light tidy for `notification` / `session` (LOW) — **done**
- `notification.exception` + `notification.dto`
- `session` remains flat (documented; under type-count threshold)  
**Assessment criterion:** Maintainability

#### Step P5 — Leave product slice layout alone unless needed — **done**
No mass `product.scan.dto` moves. Dual `ScanProduct` / `CatalogProduct` documented in `product/README.md`.  
**Assessment criterion:** Maintainability (avoid churn)

---

## 7b. Packaging Plan (F20) — completed

F19 left a few types at the feature root after the convention was documented. **F20 is package + import rewrite only.** No method body, REST path, JSON field, or Spring bean-name changes.

| From | To |
| --- | --- |
| `auth.RefreshTokenProperties` | `auth.config` (same pattern as `family.config`) |
| `family.FamilyRelationshipToAdmin` | `family.model` |
| `user.UserPreferenceService` | `user.service` |
| `user.AdminUserSummaryView`, `user.AuthenticationAccountView` | `user.repository` (Spring Data projections) |
| `notification.NotificationService` | `notification.service` |
| `notification.UserNotificationRepository` | `notification.repository` |

**Keep at feature root:** controllers; `FamilyService` facade; `AuthSessionRequestGuard` (HTTP-adjacent to `AuthController`).

**Do not in F20:** mass-move `product.*`; nest `session` or `integration`; rewrite assessment/recommendation.

**Assessment criterion:** Maintainability, modularity, layer separation  
**Risk:** LOW (compile-time imports)  
**Testing required:** `mvn test` in `server/backend`  

---

## 8. Required / Recommended / Optional

### Required (completed)
1. F01, F04, F13 — profile-gated config (Security, maintainability)  
2. F02 — CORS patterns (Security)  
3. F03 — stop logging invite tokens (Security)  
4. F05 — auth rate limit (Security, API design)  
5. F14 — characterization tests (Testability)  
6. F07 — roster/summary batch queries (Performance)  
7. F06 — split FamilyService (Modularity, method length)  

### Recommended (completed)
1. F08 — recommendation SAFE barcode query (Performance)  
2. F09 — scan `@Valid` (Layer separation)  
3. F10–F11 — cascade / LAZY (Correctness)  
4. F12 — dead code (Low coupling)  
5. F16 — narrower catches (Error handling)  

### Optional (deferred)
- F18 method security (admin-only `@PreAuthorize` is the only slice worth considering later)

### F17 package hygiene (completed 2026-08-18)
- Lombok: properties/entities/services; family restriction DTOs → records
- Packages: `IngredientEntity` → `knowledgebase.model`; `UserPreference` → `user`; exception handlers → `*.exception`
- ETL documented via `etl/README.md` (profile-gated offline tool)

### Packaging follow-up (F19 — completed 2026-08-18)
1. Documented size-based packaging convention in backend + feature READMEs (P1)  
2. Thinned `family/` root: collaborators in `family.service`, properties in `family.config` (P2)  
3. Nested `user.model` + `user.repository` (P3)  
4. `notification` `exception/` + `dto/`; `session` documented flat (P4)  
5. Product dual-entity docs only; no mass DTO moves (P5)  

### Packaging follow-up (F20 — completed 2026-08-18)
Import-only nest of leftover root types (`auth.config`, `family.model` helper, `user.service` + projections, `notification.service` / `repository`). Controllers and facades stay at feature roots. Product / session / integration unchanged. 

---

## 9. API and Database Impact

| Area | Result |
| --- | --- |
| REST paths / DTO field names | Unchanged (including F19 / F20 packaging moves) |
| Auth | Rate limit may return **429** on login/register/refresh when exceeded |
| Database schema | No migration |
| Queries | Additive batch/SAFE barcode reads |
| CORS / profiles | Local/LAN needs `dev` (default); prod must set `SPRING_PROFILES_ACTIVE=prod` |
| Java packages (F19, F20) | Import-only moves; no client impact |

---

## 10. Explicitly Not Recommended (unchanged)

- Big-bang rewrite of assessment/recommendation pipeline  
- Renaming public JSON fields  
- Changing intra-family authz without product decision (F15)  
- Unifying login suspended vs invalid messages without AC review  
- Enabling Tier-B LLM by default  
- Mass-moving product types into nested `dto/` trees  
- Unifying `ScanProduct` / `CatalogProduct` into one JPA entity  
- One-shot global re-package of all features to identical folder trees  
- Treating `@RestController` checks as the authorization system (membership stays in `FamilyAuthorizationService`)  
- Adding a parallel “service layer” framework outside Spring

---

## 11. Expected Assessment Improvement

Implementing F01–F20 (except deferred F15/F18) should move the scorecard as in §4:

- **Security:** Demo seed, CORS patterns, SQL DEBUG, and `ddl-auto=update` are `dev`-only; invite tokens are not logged; login/register/refresh are rate-limited.
- **Architecture / modularity / layer separation:** Feature packages remain vertical; family collaborators sit in `family.service`; leftover root types sit in `config` / `model` / `service` / `repository`; scan DTOs use `@Valid`; entities are not EAGER/`CascadeType.ALL` by default.
- **Readability / method length:** `FamilyService` is a facade; invite/roster/history have named types. AssessmentOrchestrator is still a large coordinator by design.
- **Low coupling:** Family no longer inlines every collaborator; dead Tier-B stub is gone; recommendation does not load unbounded prior scans.
- **Performance:** Roster/summary batch reads; SAFE-barcode recommendation query. Server-side pagination for admin user lists is **not** in this plan.
- **Testability:** DietaryProfile HTTP/authz tests landed; existing family tests still pin F15 behaviour.
- **Error handling:** Narrower catches; bean validation for scan requests. Not every `catch` in integration adapters was rewritten.
- **API design:** Public JSON unchanged; only additive **429** on auth rate limit.

---

## 12. Changes checklist

- [x] Phase 0 — Create this document + backend README link (2026-08-17)  
- [x] Phase 1.1 — Characterization tests (F14; testability) (2026-08-17)  
- [x] Phase 1.2 — Spring profiles for safe defaults (F01, F02, F04, F13; security) (2026-08-17)  
- [x] Phase 1.3 — Auth rate limiting (F05; security, API design) (2026-08-17)  
- [x] Phase 2.1 — Scrub invitation token logs (F03; security) (2026-08-17)  
- [x] Phase 3.1 — Batch queries for roster/summary (F07; performance) (2026-08-17)  
- [x] Phase 3.2 — Extract family services behind facade (F06; modularity, method length) (2026-08-17)  
- [x] Phase 4.1 — SAFE barcode query / candidate cost (F08; performance) (2026-08-17)  
- [x] Phase 4.2 — `@Valid` scan DTOs (F09; layer separation) (2026-08-17)  
- [x] Phase 5.1 — LAZY fetch + cascade narrowing (F10, F11; correctness) (2026-08-17)  
- [x] Phase 5.2 — Remove/isolate dead Tier-B and stub (F12; coupling) (2026-08-17)  
- [x] Phase 5.3 — Narrow external `catch (Exception)` (F16; error handling) (2026-08-17)  
- [x] Phase 6 — Final validation: `mvn verify` — **911 tests, 0 failures**; fill scorecard After column (2026-08-17)  
- [x] Phase 7.1 — Document packaging convention (F19 / P1; maintainability) (2026-08-18)  
- [x] Phase 7.2 — Thin `family/` root (F19 / P2; modularity) (2026-08-18)  
- [x] Phase 7.3 — Align or document `user` packaging (F19 / P3; modularity) (2026-08-18)  
- [x] Phase 7.4 — Notification/session exception (and optional dto) tidy (F19 / P4) (2026-08-18)  
- [x] Phase 7.5 — Product dual-entity docs only (F19 / P5) (2026-08-18)  
- [x] Phase 7 validation: `mvn test` — **935 tests, 0 failures** (2026-08-18)  
- [x] Phase 8 — F20 package moves + import rewrite (auth config, family model helper, user service/projections, notification service/repository); `mvn test` — 0 failures (2026-08-18)  

---

## 13. Deferred / Skipped

| Item | Reason |
| --- | --- |
| F15 authz semantics change | Product clarification required; behavior locked by `FamilyAuthorizationServiceTest` |
| F18 method security | Optional; benefit &lt; cost for this pass (admin-only annotations later if needed) |
| Product mass `dto/` re-package | High churn; slice co-location is intentional |
| ScanProduct / CatalogProduct merge | Intentional dual read models of `products` |
| Assessment / recommendation rewrite | High risk; not a packaging or hygiene task |

## Deploy note

Production/staging EC2 deploys set `SPRING_PROFILES_ACTIVE=prod` in `.github/workflows/deploy.yml` (default; override with Environment var `SPRING_PROFILES_ACTIVE`). Local/`mvnw spring-boot:run` and tests use `spring.profiles.default=dev` (SQL seed, LAN CORS patterns, Hibernate SQL DEBUG, `ddl-auto=update`).

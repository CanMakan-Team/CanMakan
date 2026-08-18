# Backend Code Quality Assessment

Living document for the Spring Boot backend under `server/backend/src/main`.  
Update the **Changes checklist** when work lands; keep findings status notes current.

**Location:** `docs/code-quality/BACKEND-CODE-QUALITY.md` (linked from [`server/backend/README.md`](../../server/backend/README.md))  
**Scope:** `com.canmakan.backend` (~313 Java files + SQL/resources)  
**Clients:** React (`client/web`) and Android (`client/mobile`) share auth, family, and dietary/profile contracts.

---

## Architecture Summary

Feature-first vertical packages (`auth`, `family`, `dietaryprofile`, `product`, `admin`, `analytics`, `knowledgebase`, `integration`, thin `shared`) with classic layering:

`HTTP → Controller → Service → Repository → MySQL`  
External: Open Food Facts, EAN-Search, OpenAI, Tavily, optional ML ranker.

**Strengths:** DTOs at API edges; constructor injection; `open-in-view=false`; JWT + HttpOnly refresh cookie; service-layer family authz; broad tests; parameterized native SQL; scan history `join fetch`.

**Post-refactor additions:** Spring `dev`/`prod` profiles; auth rate limiting; `FamilyRosterService` / `FamilyInvitationService` behind `FamilyService` facade; batched roster queries; SAFE-barcode query for recommendations; `@Valid` on scan DTOs; LAZY associations + narrowed cascades; dead Tier-B bean / stub removed.

**Packaging follow-up (F19):** Three layout styles coexist (`fully nested` / `nested DTOs + flat services` / `product sub-slices or flat small features`). See **Feature packaging evaluation** and **Packaging Plan** below — convention first; no big-bang re-package.

---

## Quality Scorecard

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

---

## Findings (F01–F18)

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
| F19 | LOW | Feature packaging inconsistency (three layout styles) | Planned — see Packaging Plan |

---

## Feature packaging evaluation

Feature-first packaging is correct for this backend. The remaining issue is **uneven nesting** of technical layers (`dto/`, `exception/`, `model/`, `repository/`, `service/`) across features.

### Three styles in use

| Style | Packages | Pattern |
| --- | --- | --- |
| Fully nested | `dietaryprofile`, `admin`, `analytics` | Root controller (where applicable); nested `dto/`, `exception/`, `model/`, `repository/`, `service/` |
| Nested DTOs/models; services flat | `auth`, `family` | `dto/`, `exception/`, `model/`, `repository/` nested; many services/controllers at feature root (`family` especially crowded) |
| Sub-slice or flat | `product.*`, `user`, `notification`, `session`, `integration` | Product: co-located types under `scan/`, `verdict/`, `recommendation/`, `assessment/`, `model/`. Small features: mostly flat (user has `dto/` only) |

### Layer folder assessment

| Folder | Assessment |
| --- | --- |
| `dto/` | Strong for HTTP APIs (`auth`, `family`, `dietaryprofile`, `admin`, `analytics`, `user`). Product keeps DTOs in-slice — acceptable. |
| `exception/` | Good when present. Prefer for any feature with multiple domain exceptions or a dedicated `@RestControllerAdvice`. |
| `model/` | Best home for JPA entities. Avoid entities under `repository/`. |
| `repository/` | Clear when nested; product keeps repos in-slice. |
| `service/` | Nested in dietaryprofile/admin/analytics/`family`; thin facade may stay at feature root (`FamilyService`, auth controllers/services). |

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

## Packaging Plan (F19) — completed

Use **size-based conventions**, not one global deep tree. Prefer convention + incremental tidy when a package is already being touched.

### Target convention

| Feature size | Layout |
| --- | --- |
| Large / many types (`family`, `auth`, `dietaryprofile`, `admin`) | Nest `dto/`, `exception/`, `model/`, `repository/`; nest `service/` **or** keep a thin root (controllers + facade only) |
| Multi-capability domain (`product`) | Keep sub-slices; co-locate DTO/service/repo inside each slice |
| Small (`notification`, `session`, `integration`) | Flat OK until ~8–10 types; then add `dto/` + `exception/` first |

### Suggested implementation steps

#### Step P1 — Document the convention (LOW risk) — **done**
**Files:** `server/backend/README.md`, feature package READMEs (`family`, `auth`, `product`, `user`, `admin`, `notification`, `session`, …)  
**Changes:** Describe the three styles and the size-based target rule; refresh stale “partial/foundation” status tables where packaging docs lag.  
**Purpose:** Stop accidental divergent layouts on new code.  
**Risk:** LOW

#### Step P2 — Thin `family/` root (MEDIUM risk) — **done**
Collaborators live in `family.service`; invite/email properties in `family.config`. Controllers + thin `FamilyService` facade remain at root. REST contracts unchanged.

#### Step P3 — Align `user` packaging (LOW–MEDIUM) — **done**
Introduced `user.model` + `user.repository` for `UserAccount`, `UserPreference`, and matching repos.

#### Step P4 — Light tidy for `notification` / `session` (LOW) — **done**
- `notification.exception` + `notification.dto`
- `session` remains flat (documented; under type-count threshold)

#### Step P5 — Leave product slice layout alone unless needed — **done**
No mass `product.scan.dto` moves. Dual `ScanProduct` / `CatalogProduct` documented in `product/README.md`.

---

## Required / Recommended / Optional

### Required (completed)
1. F01, F04, F13 — profile-gated config  
2. F02 — CORS patterns  
3. F03 — stop logging invite tokens  
4. F05 — auth rate limit  
5. F14 — characterization tests  
6. F07 — roster/summary batch queries  
7. F06 — split FamilyService  

### Recommended (completed)
1. F08 — recommendation SAFE barcode query  
2. F09 — scan `@Valid`  
3. F10–F11 — cascade / LAZY  
4. F12 — dead code  
5. F16 — narrower catches  

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

---

## API and Database Impact

| Area | Result |
| --- | --- |
| REST paths / DTO field names | Unchanged (including F19 packaging moves) |
| Auth | Rate limit may return **429** on login/register/refresh when exceeded |
| Database schema | No migration |
| Queries | Additive batch/SAFE barcode reads |
| CORS / profiles | Local/LAN needs `dev` (default); prod must set `SPRING_PROFILES_ACTIVE=prod` |
| Java packages (F19) | Import-only moves; no client impact |

---

## Explicitly Not Recommended (unchanged)

- Big-bang rewrite of assessment/recommendation pipeline  
- Renaming public JSON fields  
- Changing intra-family authz without product decision (F15)  
- Unifying login suspended vs invalid messages without AC review  
- Enabling Tier-B LLM by default  
- Mass-moving product types into nested `dto/` trees  
- Unifying `ScanProduct` / `CatalogProduct` into one JPA entity  
- One-shot global re-package of all features to identical folder trees  

---

## Plan Checklist

- [x] Phase 0 — Create this document + backend README link  
- [x] Phase 1.1 — Characterization tests (F14)  
- [x] Phase 1.2 — Spring profiles for safe defaults (F01, F02, F04, F13)  
- [x] Phase 1.3 — Auth rate limiting (F05)  
- [x] Phase 2.1 — Scrub invitation token logs (F03)  
- [x] Phase 3.1 — Batch queries for roster/summary (F07)  
- [x] Phase 3.2 — Extract family services behind facade (F06)  
- [x] Phase 4.1 — SAFE barcode query / candidate cost (F08)  
- [x] Phase 4.2 — `@Valid` scan DTOs (F09)  
- [x] Phase 5.1 — LAZY fetch + cascade narrowing (F10, F11)  
- [x] Phase 5.2 — Remove/isolate dead Tier-B and stub (F12)  
- [x] Phase 5.3 — Narrow external `catch (Exception)` (F16)  
- [x] Phase 6 — Full `mvn verify` + finalize scorecard / checklists  
- [x] Phase 7.1 — Document packaging convention (F19 / P1)  
- [x] Phase 7.2 — Thin `family/` root (F19 / P2)  
- [x] Phase 7.3 — Align or document `user` packaging (F19 / P3)  
- [x] Phase 7.4 — Notification/session exception (and optional dto) tidy (F19 / P4)  
- [x] Phase 7.5 — Product dual-entity docs only (F19 / P5)  

---

## Changes Checklist

- [x] Phase 0 — Create this document + backend README link (2026-08-17)  
- [x] Phase 1.1 — Characterization tests (2026-08-17)  
- [x] Phase 1.2 — Spring profiles for safe defaults (2026-08-17)  
- [x] Phase 1.3 — Auth rate limiting (2026-08-17)  
- [x] Phase 2.1 — Scrub invitation token logs (2026-08-17)  
- [x] Phase 3.1 — Batch queries for roster/summary (2026-08-17)  
- [x] Phase 3.2 — Extract family services behind facade (2026-08-17)  
- [x] Phase 4.1 — SAFE barcode query / candidate cost (2026-08-17)  
- [x] Phase 4.2 — `@Valid` scan DTOs (2026-08-17)  
- [x] Phase 5.1 — LAZY fetch + cascade narrowing (2026-08-17)  
- [x] Phase 5.2 — Remove/isolate dead Tier-B and stub (2026-08-17)  
- [x] Phase 5.3 — Narrow external `catch (Exception)` (2026-08-17)  
- [x] Phase 6 — Final validation: `mvn verify` — **911 tests, 0 failures** (2026-08-17)  
- [x] Phase 7.1 — Document packaging convention (2026-08-18)  
- [x] Phase 7.2 — Thin `family/` root (2026-08-18)  
- [x] Phase 7.3 — Align or document `user` packaging (2026-08-18)  
- [x] Phase 7.4 — Notification/session exception (and optional dto) tidy (2026-08-18)  
- [x] Phase 7.5 — Product dual-entity docs only (2026-08-18)  
- [x] Phase 7 validation: `mvn test` — **935 tests, 0 failures** (2026-08-18)  

---

## Deferred / Skipped

| Item | Reason |
| --- | --- |
| F15 authz semantics change | Product clarification required; behavior locked by `FamilyAuthorizationServiceTest` |
| F18 method security | Optional; benefit &lt; cost for this pass (admin-only annotations later if needed) |
| Product mass `dto/` re-package | High churn; slice co-location is intentional |
| ScanProduct / CatalogProduct merge | Intentional dual read models of `products` |

## Deploy note

Production/staging EC2 deploys set `SPRING_PROFILES_ACTIVE=prod` in `.github/workflows/deploy.yml` (default; override with Environment var `SPRING_PROFILES_ACTIVE`). Local/`mvnw spring-boot:run` and tests use `spring.profiles.default=dev` (SQL seed, LAN CORS patterns, Hibernate SQL DEBUG, `ddl-auto=update`).

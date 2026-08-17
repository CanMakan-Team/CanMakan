# CanMakan Backend

Java Spring Boot backend for the Barcode AI Ingredient Interpreter.

## Package Philosophy

The backend is organised by **feature** (vertical slices) rather than by technical layer.

Each top-level package represents a cohesive business capability. This makes the codebase easier to navigate, own, and evolve as the product grows.

### Design Principles

1. **Feature over layer**  
   Related behaviour stays together (e.g. scanning, verdict, recommendation and history all live under `product`).

2. **Thin shared package**  
   `shared` contains only technical cross-cutting concerns (config, security infrastructure, exceptions, utilities). No business logic.

3. **Clear boundaries**  
   - Business features → their own packages  
   - External systems → `integration`  
   - Domain knowledge → `knowledgebase`

4. **Pragmatic granularity**  
   Packages are intentionally not too fine-grained. Tightly coupled flows are kept together to reduce friction during early development.

## Package Overview

```
| Package            | Purpose                                              |
|--------------------|------------------------------------------------------|
| `shared`           | Shared technical foundation (config, security, util) |
| `auth`             | Login, logout, tokens, sessions                      |
| `dietaryprofile`   | Individual dietary needs and restrictions            |
| `family`           | Family membership and active profile switching       |
| `product`          | Scanning, verdicts, recommendations, history         |
| `analytics`        | Trends, statistics, AI metrics, exports              |
| `admin`            | Account, role, health and subscription management    |
| `knowledgebase`    | Ingredient aliases, E-numbers, dietary rules         |
| `integration`      | External API clients (Open Food Facts, OpenRouter…)  |
```
See individual package README files for detailed responsibilities.

## Package Status

This repository uses feature-first package boundaries even though implementation depth differs by package.

```
|     Package      |       Status       |                                 Notes                            |
|------------------|--------------------|------------------------------------------------------------------|
| `dietaryprofile` |     Implemented    | Active API, service, repository, and entity mapping              |
| `family`         | Partial (UC8)  | Create circle + `/families/me` live; D2 UNIQUE; JWT principal. Invite/manage/switch → UC9–UC12 |
| `user`           |     Foundation     | User entity mapping used for profile linkage and ownership       |
| `knowledgebase`  |     Foundation     | Domain models available; service APIs in progress                |
| `product`        |     Foundation     | Initial model types available                                    |
| `auth`           |    Partial | Register + JWT login/refresh/logout/me; family/scan require JWT |
| `admin`          |    Planned/partial | Package scaffolded; implementation to expand                     |
| `analytics`      |    Planned/partial | Package scaffolded; implementation to expand                     |
| `integration`    |    Planned/partial | Package scaffolded; implementation to expand                     |
```

## Resource Source Of Truth

- Edit SQL and app config files only in `src/main/resources`.
- The `target/` directory contains generated/copied outputs from build and test runs; do not edit files there.
- Any files under generated build output paths are non-source artifacts.

## Technology

- Java 21
- Maven 3.9.9 through Maven Wrapper 3.3.2
- Spring Boot 3.5.4
- Jar packaging

The initial dependencies are Spring Web, Validation, Spring Boot Actuator, and
Spring Boot Test.

## Run

From `server/backend` on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Database and external-service defaults are available in `application.properties`.
Access-token signing material intentionally has no insecure local fallback.

Requirements for a successful local run:

1. MySQL running on `localhost:3306`
2. A database user that matches the defaults (`root` / empty password), or override with env vars
3. `JWT_SIGNING_SECRET` set to Base64-encoded random signing material of at least 32 bytes

JWT configuration:

| Variable | Default | Purpose |
|----------|---------|---------|
| `JWT_SIGNING_SECRET` | _(required)_ | Base64-encoded HS256 signing material of at least 32 bytes |
| `JWT_ISSUER` | `canmakan` | Access-token issuer |
| `JWT_ACCESS_TTL` | `15m` | Short-lived access-token duration |

Refresh-session configuration:

| Variable | Default | Purpose |
|----------|---------|---------|
| `REFRESH_TOKEN_TTL` | `7d` | Opaque refresh-session and cookie lifetime |
| `REFRESH_COOKIE_NAME` | `canmakan_refresh` | HttpOnly refresh-cookie name |
| `REFRESH_COOKIE_SECURE` | `true` | Require HTTPS for the refresh cookie. For local HTTP (`http://localhost:5173`) set `REFRESH_COOKIE_SECURE=false` or browsers will drop the cookie. |
| `REFRESH_COOKIE_SAME_SITE` | `Lax` | Safe default. Separately hosted HTTPS web/API deployments must explicitly use `None` with Secure cookies, credentialed CORS, exact HTTPS origins, and no origin patterns. |

`POST /api/auth/login` and `POST /api/auth/refresh` return the access token in
JSON and set the opaque refresh token only as an HttpOnly cookie scoped to
`/api/auth`. The default is `SameSite=Lax`. Cross-site HTTPS deployments must
explicitly select `None` and satisfy the stricter startup validation above.
Login, refresh, logout, and `DELETE /api/auth/account` also require the non-secret
session-intent header; browser Origins must exactly match the configured
allow-list, while native clients omit Origin but still send the header.

Optional env vars (only needed when exercising those features):

| Variable | Default | Purpose |
|----------|---------|---------|
| `MYSQL_USERNAME` | `root` | DB user |
| `MYSQL_PASSWORD` | _(empty)_ | DB password |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | `localhost` / `3306` / `canmakan` | DB connection |
| `OPENAI_API_KEY` | `local-dev-placeholder` | Real AI / Tier-3 tool agent |
| `OPENAI_MODEL` | `gpt-4o-mini` | Chat model |
| `CANMAKAN_AI_ENABLED` | `false` | Enable Tier-3 LLM tool agent on WARNING escalate |
| `TAVILY_API_KEY` | `local-dev-placeholder` | External allergen fallback |
| `EAN_SEARCH_API_KEY` | `demo_token` | EAN Search API |

With placeholder API keys the app starts; OpenAI/Tavily features stay inactive until real keys are set.

The application listens on port 8080.

### Enable Tier-3 LLM tool agent

On WARNING escalation, assess can call a ChatClient agent that autonomously uses the five dietary knowledge tools, then the rule engine still decides the verdict.

```powershell
$env:OPENAI_API_KEY = "sk-your-real-key"
$env:CANMAKAN_AI_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

Set both variables in the **same shell** that starts Spring Boot, then restart. Setting them in a different terminal after the JVM is already running has no effect.

**Windows note:** System (Machine) env vars are only loaded into apps at process start. If Cursor/VS Code was already open when you set `JWT_SIGNING_SECRET`, integrated terminals may not see it until you restart the IDE. The VS Code **Run Backend** / **Run Full Stack** tasks re-read Machine/User env into the external backend window so a Cursor restart is not required for those launchers.

On escalate failure the backend logs `Tier-3 escalate skipped ...` and keeps the Tier-1 WARNING (for example when AI is still disabled or the OpenAI call fails).

Default `CANMAKAN_AI_ENABLED=false` keeps assess on Tier-1 rules only. Do not commit real API keys.

### UC5 recommendations (Tier A catalog + Tier C ML + Python ranker)

MVP recommendations use catalog discovery and dietary filtering in Spring, then rank SAFE candidates. LLM discovery is **not** on the request path.

| Variable | Default | Purpose |
|----------|---------|---------|
| `CANMAKAN_RECOMMENDATION_ML_ENABLED` | `true` | Set `false` for Tier A heuristic ranking only |
| `CANMAKAN_RECOMMENDATION_ML_RANKER_URL` | _(empty)_ | Python FastAPI rank service (e.g. `http://127.0.0.1:8091`). Empty = Java ranker fallback |
| `CANMAKAN_RECOMMENDATION_ML_ARTIFACT_PATH` | _(empty)_ | Legacy Java inline vectors (`product_feature_vectors.json`) |

Local demo: run Spring Boot, then from `server/machine-learning/` train and start uvicorn (see `server/machine-learning/README.md`).

Fallback order: Tier A → Tier C tag recall → Python rank (if configured) → Java `MlContentBasedRanker` → heuristic ranker → empty list.

Tan-family demo gold-set overlays: `01f_uc5_demo_gold_set.sql`. Additive tag backfill: `01c_recommendation_substitute_tags.sql` (from `server/machine-learning/scripts/audit_substitute_tags.py`).

`CANMAKAN_RECOMMENDATION_LLM_ENABLED` remains in config for the unused Tier B service (default `false`) and is not called from `RecommendationService`.

### Enable Tavily (external allergen fallback)

Unknown ingredients that miss the local allergen hierarchy can be looked up via Tavily
(one batched search per scan, capped). When `CANMAKAN_AI_ENABLED=true`, a tool-free
ChatClient maps that search text to structured root codes; otherwise a regex parser
is used. With the default `local-dev-placeholder` key, Tavily is skipped.

```powershell
$env:TAVILY_API_KEY = "tvly-your-real-key"
# optional; default is already https://api.tavily.com/search
$env:TAVILY_URL = "https://api.tavily.com/search"
.\mvnw.cmd spring-boot:run
```

Restart the backend after setting the key. Do not commit real API keys.

## Test

```powershell
.\mvnw.cmd test
```

## Health check and smoke assess

Once the application is running (MySQL + seeded data):

```text
GET http://localhost:8080/actuator/health
```

End-to-end validate → assess smoke. Supply a current UC19 access token for a
user authorized to scan the selected profile (defaults: Nutella barcode,
profileId `1`):

```powershell
.\scripts\smoke-assess.ps1 -AccessToken "<jwt>"
# or with overrides:
.\scripts\smoke-assess.ps1 -AccessToken "<jwt>" -Barcode "3017620422003" -ProfileId 1
```

The script prints HTTP status, verdict level, and finding codes. It exits non-zero if health, validate, or assess fails. The backend derives the caller identity from the bearer token, and the selected `profileId` must be authorized for that user.

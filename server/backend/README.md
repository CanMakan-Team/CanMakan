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
| `family`         | Partial (UC8)  | Create circle + `/families/me` live; D2 UNIQUE; temp `X-User-Id`. Auth → UC19; invite/manage/switch → UC9–UC12 |
| `user`           |     Foundation     | User entity mapping used for profile linkage and ownership       |
| `knowledgebase`  |     Foundation     | Domain models available; service APIs in progress                |
| `product`        |     Foundation     | Initial model types available                                    |
| `auth`           |    Partial | Register + pre-JWT login; JWT/SecurityFilterChain → UC19 |
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

Local defaults in `application.properties` mean you do **not** need GitHub Actions secrets to start the app.

Requirements for a successful local run:

1. MySQL running on `localhost:3306`
2. A database user that matches the defaults (`root` / empty password), or override with env vars

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

Set both variables in the **same shell** that starts Spring Boot, then restart. Setting them in a different terminal after the JVM is already running has no effect. If you use `.vscode/run-backend.ps1`, set the variables in that shell first (or as User env vars) so the launched process inherits them.

On escalate failure the backend logs `Tier-3 escalate skipped ...` and keeps the Tier-1 WARNING (for example when AI is still disabled or the OpenAI call fails).

Default `CANMAKAN_AI_ENABLED=false` keeps assess on Tier-1 rules only. Do not commit real API keys.

### Enable Tavily (external allergen fallback)

Unknown ingredients that miss the local allergen hierarchy can be looked up via Tavily.
With the default `local-dev-placeholder` key, that fallback is skipped.

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

End-to-end validate → assess smoke (defaults: Nutella barcode, profileId/userId `1`):

```powershell
.\scripts\smoke-assess.ps1
# or with overrides:
.\scripts\smoke-assess.ps1 -Barcode "3017620422003" -ProfileId 1 -UserId 1
```

The script prints HTTP status, verdict level, and finding codes. It exits non-zero if health, validate, or assess fails. Use a `profileId` / `userId` that exist in the seeded database.

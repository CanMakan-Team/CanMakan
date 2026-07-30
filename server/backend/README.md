# CanMakan Backend

Java Spring Boot backend for the Barcode AI Ingredient Interpreter.

## Package Philosophy

The backend is organised by **feature** (vertical slices) rather than by technical layer.

Each top-level package represents a cohesive business capability. This makes the codebase easier to navigate, own, and evolve as the product grows.

### Design Principles

1. **Feature over layer**  
   Related behaviour stays together (e.g. scanning, verdict, recommendation and history all live under `product`).

2. **Thin common package**  
   `common` contains only technical cross-cutting concerns (config, security infrastructure, exceptions, utilities). No business logic.

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
| `common`           | Shared technical foundation (config, security, util) |
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

The application listens on port 8080.

## Test

```powershell
.\mvnw.cmd test
```

## Health check

Once the application is running:

```text
GET http://localhost:8080/actuator/health
```

Only the Actuator health endpoint is exposed for this initial smoke-test
skeleton.

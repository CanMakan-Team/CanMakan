# CanMakan Backend

This directory contains the initial CanMakan backend skeleton. It deliberately
does not yet implement users, authentication, barcode scanning, ingredient
assessment, databases, rule engines, AI, RAG, or other business features.

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

# CanMakan

CanMakan is an AI-powered barcode ingredient interpreter planned to help users
scan packaged food, understand its ingredients, and receive clear, useful
dietary information.

## Confirmed technology

- Mobile: Android with Jetpack Compose
- Web: React with Vite using JavaScript
- Backend: Spring Boot with Maven and Java 21

The machine-learning, agentic AI, database, and deployment technology choices
remain pending.

## Repository Structure

```text
.
|-- client/
|   |-- mobile/                    # Android Kotlin + Jetpack Compose
|   |   `-- app/src/main/java/sg/edu/nus/iss/canmakan/
|   |       |-- shared/            # DI, network, UI kit, shared models, utils
|   |       |-- features/
|   |       |   |-- auth/
|   |       |   |-- dietaryprofile/
|   |       |   |-- family/
|   |       |   |-- product/       # scan, verdict, recommendation, history, reporting
|   |       |   `-- analytics/     # lightweight / optional on mobile
|   |       |-- navigation/        # root NavHost
|   |       |-- MainActivity.kt
|   |       `-- CanMakanApplication.kt
|   |
|   `-- web/                       # React + Vite + TypeScript
|       `-- src/
|           |-- app/
|           |   `-- router/        # AppRoutes
|           |-- shared/
|           |   |-- api/           # apiClient, apiErrors, shared types
|           |   |-- model/         # truly shared models only
|           |   |-- ui/            # PortalLayout + shared components
|           |   `-- lib/           # optional hooks/utils
|           |-- features/
|           |   |-- auth/
|           |   |-- family/
|           |   |-- admin/
|           |   `-- analytics/
|           |-- mocks/
|           |-- pages/             # temporary login entry pages
|           |-- styles/
|           `-- main.tsx
|
|-- server/
|   |-- backend/                   # Spring Boot, Maven, Java 21
|   |   `-- src/main/java/com/canmakan/backend/
|   |       |-- common/            # config, security, exception, util
|   |       |-- auth/
|   |       |-- dietaryprofile/
|   |       |-- family/
|   |       |-- product/           # scan, verdict, recommendation, history, reporting
|   |       |-- analytics/
|   |       |-- admin/
|   |       |-- knowledgebase/
|   |       `-- integration/       # Open Food Facts, OpenRouter, etc.
|   |-- machine-learning/          # Reserved ML component
|   `-- agentic-ai/                # Reserved Agentic AI and RAG component
|
|-- database/                      # Reserved database area
|-- deployment/                    # Reserved deployment and infrastructure area
|-- docs/
|   |-- architecture/
|   |-- requirements/
|   |-- api/
|   |-- database/
|   `-- sprint/
`-- .github/                       # Pull request and issue templates
```

## Local start

Android debug build (requires JDK 17 and Android SDK 34):

```powershell
cd client/mobile
.\gradlew.bat :app:assembleDebug
```

Web development server:

```powershell
cd client/web
npm install
npm run dev
```

Backend application (requires Java 21):

```powershell
cd server/backend
.\mvnw.cmd spring-boot:run
```

The backend health endpoint is
`http://localhost:8080/actuator/health`.

## DevSecOps Implementation

### Branch Protection

- Require pull request
- No direct pushes to main

### Secrets Management

- Environment variables, credentials, and secrets are included in gitignore to prevent secrets leakage
- Implemented Gitleaks via [`.github/workflows/secret-scan.yml`](.github/workflows/secret-scan.yml)
> Configured Gitleaks to run on: all pull requests, pushes to main <br>
> Uses actions/checkout with full history (fetch-depth: 0) for commit scanning <br>
> Uses GitHub-provided GITHUB_TOKEN <br>

### Dependabot

This repository uses [GitHub Dependabot](https://docs.github.com/en/code-security/dependabot) to keep third-party dependencies current and to surface known security advisories.

Configuration: [`.github/dependabot.yml`](.github/dependabot.yml)

| Ecosystem | Directory | Schedule |
|-----------|-----------|----------|
| npm | `client/web` | Weekly |
| Maven | `server/backend` | Weekly |
| Gradle | `client/mobile` | Weekly |
| GitHub Actions | `/` | Weekly |

> Dependabot opens pull requests for version and security updates.  <br>
> Review and run the relevant CI checks before merging. <br>
> Set PR limits and labels for easier triage <br>

## Current status

The mobile directory contains the supplied UI prototype and sample data. The web
and backend directories are runnable initial skeletons only. Barcode camera
integration, backend business APIs, authentication, databases, machine
learning, agentic AI, and deployment configuration are not implemented.

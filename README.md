# CanMakan

CanMakan is an AI-powered barcode ingredient interpreter planned to help users
scan packaged food, understand its ingredients, and receive clear, useful
dietary information.

## Confirmed technology

- Mobile: Android with Jetpack Compose
- Web: React with Vite using JavaScript
- Backend: Spring Boot with Maven and Java 21

Shared brand colors live under `design-tokens/` (JSON source → generated
Compose `Color.kt` and web `tokens.css`). See `design-tokens/README.md`.
Shared mascot images live under `client/shared/assets/mascot/` and are
referenced by both the Android and web clients.

The machine-learning, agentic AI, database, and deployment technology choices
remain pending.

## Repository Structure

```text
.
|-- design-tokens/                  # Shared color tokens → Compose + CSS
|   |-- colors.json
|   |-- generate.mjs
|   `-- README.md
|
|-- client/
|   |-- shared/                    # Cross-client assets (mascot PNGs)
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
|           |-- styles/            # app.css + generated tokens.css
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
|   |-- machine-learning/          # Python TF-IDF rank API (UC5)
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

Local MySQL on `localhost:3306` and a `JWT_SIGNING_SECRET` environment value are
required (database defaults: user `root`, empty password). External API keys are
optional for startup; see [`server/backend/README.md`](server/backend/README.md).

The backend health endpoint is
`http://localhost:8080/actuator/health`.

Continuous integration builds the backend, web app, and Android app on pushes and pull requests.

## DevSecOps Implementation

### Branch Protection

- Require pull request
- No direct pushes to main
- Require the **Build Test** check (aggregates Gitleaks, Semgrep, Trivy, stack builds, and SonarCloud when configured)
- Production deploys use GitHub Environment **`production`** (`main` only) via `vars.DEPLOY_ENVIRONMENT`

### Secrets Management

- Environment variables, credentials, and secrets are included in gitignore to prevent secrets leakage
- Gitleaks runs inside [`.github/workflows/ci.yml`](.github/workflows/ci.yml) (`gitleaks` job)
> Checkout uses full history (`fetch-depth: 0`) <br>
> Gitleaks 8.21.2 with `--config .gitleaks.toml`; allowlists the test JWT and `google-services.json` (Firebase client key). Fingerprints in [`.gitleaksignore`](.gitleaksignore) <br>

### Continuous Integration

Implemented via [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

- Runs on pull requests and pushes to `develop` and `main`
- Security jobs always run; stack builds are path-filtered on `ubuntu-latest`

| Job | What it does |
|-----|----------------|
| Gitleaks | Secret scanning |
| Semgrep | SAST (`semgrep/semgrep:1.173.0 --config p/default`; needs network; skips via [`.semgrepignore`](.semgrepignore)) |
| Trivy fs | SCA vulns only (CRITICAL/HIGH fails the job; secrets are Gitleaks); CycloneDX SBOM artefact |
| Trivy config | GitHub Actions / `.github` YAML misconfiguration |
| Backend | Maven `verify` against job-local MySQL 8 (not RDS), Java 21, JaCoCo; uploads `backend-jar`; SonarCloud `canmakan-backend` |
| Web | `npm ci` + Vitest with coverage + `npm run build` (Node 24); SonarCloud `canmakan-web` |
| Mobile | Gradle `assembleDebug testDebugUnitTest` + unit-test coverage XML, then `sonar`; SonarCloud `canmakan-mobile` |
| Build Test | Required aggregator |

Playwright E2E: [`.github/workflows/e2e.yml`](.github/workflows/e2e.yml) on PRs and pushes to `develop` when `client/web/**` changes. Production web deploy re-runs Playwright in [`.github/workflows/deploy-frontends.yml`](.github/workflows/deploy-frontends.yml).

> Backend CI runs `mvn verify` against an ephemeral MySQL 8 service (not RDS) and uploads the verified JAR. Production backend deploy waits for that CI run on `main` and SCP’s the artefact (no `skipTests` rebuild). Runtime env vars still come from GitHub secrets and are forwarded to EC2 at JAR start. Deploy jobs use Environment `production` (`vars.DEPLOY_ENVIRONMENT`). <br>
> SonarCloud runs after each stack’s tests when `SONAR_TOKEN` is set (org `canmakan-team` is in source). A failed quality gate fails that build job and **Build Test**. Semgrep remains SAST. <br>
> Web job: `VITE_API_BASE_URL`, `VITE_USE_MOCK_API`, optional `FIREBASE_APP_DISTRIBUTION_URL` → `VITE_FIREBASE_APP_DISTRIBUTION_URL` (also forwarded to the backend for invite-email “mobile” links). Mobile job: optional `MOBILE_BASE_URL` → `BASE_URL`, optional `CANMAKAN_INVITES_PUBLIC_BASE_URL`. <br>
> Android SDK is provisioned via `android-actions/setup-android` <br>

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

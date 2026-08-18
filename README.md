# CanMakan

CanMakan is an AI-powered barcode ingredient interpreter planned to help users
scan packaged food, understand its ingredients, and receive clear, useful
dietary information.

## Confirmed technology

- Mobile: Android with Jetpack Compose
- Web: React with Vite using TypeScript
- Backend: Spring Boot with Maven and Java 21
- Ranker: Python FastAPI (`server/machine-learning`), Docker image on GHCR with the API

Shared brand colors live under `design-tokens/` (JSON source → generated
Compose `Color.kt` and web `tokens.css`). See `design-tokens/README.md`.
Shared mascot images live under `client/shared/assets/mascot/` and are
referenced by both the Android and web clients.

Staging and production API runtime is Docker on EC2 (see `CICD-PIPELINE.md` and `deployment/README.md`).

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
|   |       |   |-- account/
|   |       |   |-- dietaryprofile/
|   |       |   |-- family/
|   |       |   |-- notifications/
|   |       |   |-- session/
|   |       |   `-- product/       # scan, verdict, recommendation, history
|   |       |-- navigation/
|   |       |-- MainActivity.kt
|   |       `-- CanMakanApplication.kt
|   |
|   `-- web/                       # React + Vite + TypeScript
|       `-- src/
|           |-- app/               # router (AppRoutes), error boundary
|           |-- shared/            # api, ui, validation, lib
|           |-- features/
|           |   |-- auth/
|           |   |-- account/
|           |   |-- family/
|           |   |-- admin/
|           |   `-- analytics/
|           |-- mocks/
|           |-- pages/             # login / register entry pages
|           |-- styles/            # app.css + generated tokens.css
|           `-- main.tsx
|
|-- server/
|   |-- backend/                   # Spring Boot, Maven, Java 21
|   |   |-- Dockerfile             # Temurin JRE + CI-verified JAR
|   |   `-- src/main/java/com/canmakan/backend/
|   |       |-- shared/            # config, security, exception
|   |       |-- auth/
|   |       |-- user/
|   |       |-- session/
|   |       |-- dietaryprofile/
|   |       |-- family/
|   |       |-- notification/
|   |       |-- product/           # scan, assessment, recommendation
|   |       |-- analytics/
|   |       |-- admin/
|   |       |-- knowledgebase/     # Dietary tools (in-process MCP-style)
|   |       |-- ai/                # Tier-3 LLM evidence (not a separate deploy)
|   |       |-- etl/
|   |       `-- integration/
|   |-- machine-learning/          # UC5 Python TF-IDF ranker (FastAPI). Spring calls
|   |   |                          #   POST /rank after filterAcceptable; empty URL or
|   |   |                          #   downtime → Java ranker fallback. CI: pytest 80%,
|   |   |                          #   SonarCloud canmakan-ml, train from 01_products.sql,
|   |   |                          #   image + Trivy, GHCR canmakan-ml; CD sidecar on EC2
|   |   |                          #   (canmakan-ml:8091)
|   |   |-- Dockerfile             # python:3.12-alpine; joblib baked in after train
|   |   |-- requirements.txt       # fastapi, uvicorn, sklearn, pytest
|   |   |-- sonar-project.properties
|   |   |-- pytest.ini
|   |   |-- src/canmakan_ml/       # api.py (GET /health, POST /rank), ranker, features
|   |   |-- scripts/               # export_products.py, train_ranker.py, evaluate.py
|   |   |-- tests/                 # unit + API tests (tiny fixtures, not prod joblib)
|   |   `-- artifacts/             # tfidf_ranker.joblib not committed; CI trains it
|   `-- agentic-ai/                # Reserved (no container/CD). Assess agent is
|                                  #   in-process Spring: knowledgebase/mcp tools +
|                                  #   ai/ Tier-3 ChatClient. See
|                                  #   docs/architecture/mcp-agent-architecture.md
|
|-- docs/
|   |-- architecture/
|   |-- requirements/
|   |-- api/
|   |-- code-quality/
|   `-- sprint/
`-- .github/                       # CI/CD as code (secrets stay on CD workflows, not PR CI)
    |-- workflows/
    |   |-- ci.yml                 # PR + push to develop/main: Gitleaks, Semgrep,
    |   |                          #   Trivy fs + config, path-filtered builds, Sonar,
    |   |                          #   Docker images + Trivy image, Build Test gate.
    |   |                          #   GHCR push of canmakan-backend / canmakan-ml
    |   |                          #   only on push to develop/main (not on PRs)
    |   |-- e2e.yml                # Playwright when client/web changes.
    |   |                          #   PR to develop/main; push to develop only
    |   |                          #   (production web E2E is in deploy-frontends)
    |   |-- deploy.yml             # After successful CI *push* on develop/main:
    |   |                          #   pull SHA images from GHCR, SSH ubuntu@EC2,
    |   |                          #   docker run, health check, Nginx blue/green.
    |   |                          #   develop → staging Environment; main → production
    |   |-- deploy-frontends.yml   # Push to develop/main (web/mobile paths):
    |   |                          #   Vite → Firebase Hosting; signed APK →
    |   |                          #   Firebase App Distribution (staging vs production)
    |   |-- dast.yml               # Nightly cron (18:00 UTC) + workflow_dispatch:
    |   |                          #   OWASP ZAP baseline vs staging web + authenticated API
    |   |-- load-test.yml          # Weekly Sunday cron (19:00 UTC) + dispatch:
    |   |                          #   Grafana k6 vs staging API (P95 target 500ms)
    |   |-- sync-branches.yml      # Push to main: open PR main → develop (hotfixes back)
    |   `-- triage.yml             # Issues opened/edited: keyword labels
    |-- scripts/
    |   |-- deploy-backend-container.sh  # EC2: docker login/pull/run + ML sidecar
    |   `-- k6-load-test.js        # k6 scenario used by load-test.yml
    |-- CODEOWNERS                 # * @Codemelia @K4i-Z3r (required on protected branches)
    |-- dependabot.yml             # Weekly Monday: npm (web), Maven, Gradle, Actions
    |-- pull_request_template.md
    `-- ISSUE_TEMPLATE/
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
| Backend | Maven `verify` against job-local MySQL 8 (not RDS), Java 21, JaCoCo; Docker image + Trivy; uploads `backend-jar`; SonarCloud `canmakan-backend` |
| Machine learning | pytest coverage gate; SonarCloud `canmakan-ml`; train ranker; Docker image + Trivy; GHCR `canmakan-ml` on `develop`/`main` |
| Web | `npm ci` + Vitest with coverage + `npm run build` (Node 24); SonarCloud `canmakan-web` |
| Mobile | Gradle `assembleDebug testDebugUnitTest` + unit-test coverage XML, then `sonar`; SonarCloud `canmakan-mobile` |
| Build Test | Required aggregator |

Playwright E2E: [`.github/workflows/e2e.yml`](.github/workflows/e2e.yml) on PRs and pushes to `develop` when `client/web/**` changes. Production web deploy re-runs Playwright in [`.github/workflows/deploy-frontends.yml`](.github/workflows/deploy-frontends.yml).

> Backend CI runs `mvn verify` against an ephemeral MySQL 8 service (not RDS) and uploads the verified JAR. Production/staging deploy waits for that CI run on `main`/`develop` and pulls the GHCR **container** (no `skipTests` rebuild). The UC5 FastAPI ranker is the same CD workflow. Runtime env vars still come from GitHub Environment secrets and are forwarded to EC2 at `docker run`. <br>
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

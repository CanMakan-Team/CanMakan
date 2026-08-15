# CI/CD Pipeline Architecture Design

## 1. Overview
The continuous integration and continuous deployment (CI/CD) architecture leverages **GitHub Actions** to automate the building, testing, security scanning, and deployment of the CanMakan full-stack application.
The pipeline employs a monorepo strategy utilising **path-based filtering** and **concurrency controls** to independently route deployments for the Spring Boot backend, React web frontend, and Kotlin Android mobile application without executing unnecessary jobs. It now features an integrated End-to-End (E2E) testing workflow to ensure web application stability prior to deployment.

## 2. Implemented Architecture
### A. Code Quality & Security (CI)
* **Branch-Gated Workflow:** The pipeline utilizes a `develop` branch for integration. Branch protection rules enforce the `Build Test` status check to pass on both the `develop` and `main` branches prior to merging[cite: 2]. CodeQL is also enabled as part of these Branch Protection Rules[cite: 2].
* **Static Application Security Testing (SAST):** Semgrep is implemented in `ci.yml` to automatically analyze source code for security vulnerabilities[cite: 2].
* **Software Composition Analysis (SCA):** Trivy is implemented in `ci.yml` to scan dependencies and file systems for critical and high-severity vulnerabilities[cite: 2].
* **Secret Scanning:** Automated scanning workflows intercept and audit pull requests for accidentally committed credentials, API keys, and sensitive data.
* **Dependency Management:** GitHub Dependabot is configured (`dependabot.yml`) to audit and update vulnerable repository dependencies routinely.
* **Build Assembly Checks:** PRs must pass compilation and build steps before merging to the `main` branch to prevent broken code from entering the deployment pipeline.

### B. Backend Deployment (`deploy.yml`)
* **Trigger:** Push to `main` with changes in `server/backend/**`.
* **Build:** Configures JDK 21 (Temurin) and executes a clean Maven package.
* **Deploy (AWS EC2 - Blue/Green Deployment):**
  * Transfers the compiled `.jar` artefact to the EC2 instance via SCP.
  * Implements a Blue/Green deployment mechanism by running two instances of the Spring Boot app on a single AWS-EC2 instance using ports 8080 and 8081[cite: 2].
  * Utilizes an Nginx reverse proxy to silently switch traffic to the newly deployed instance only after it successfully passes health checks, ensuring zero downtime during releases[cite: 2].

### C. End-to-End Testing (E2E)
* **Web App Testing (Playwright)**
  * **Trigger:** Push or Pull Request to `main` with changes in `client/web/**`.
  * **Execution:** Installs Node.js 24, resolves dependencies, caches Playwright browsers, and runs the E2E test suite (`npx playwright test`).
  * **Test Coverage:**
    * Authentication and Route Guarding: Unauthenticated users are redirected to login.
    * Authentication and Route Guarding: Valid credentials grant access to the portal.
    * Authentication and Route Guarding: Sign out clears the session and redirects to login.
    * Authentication and Route Guarding: Session persists across page reloads.
    * Verify responsiveness of CanMakan Web Navigation Elements.
  * **Reporting:** Uploads `playwright-report` as a GitHub artefact (retained for 30 days) for debugging.

### D. Frontend Deployments (`deploy-frontends.yml`)
* **Web App (React/Vite -> Firebase Hosting)**
  * **Trigger:** Successful completion of the "E2E Playwright Tests" workflow (`workflow_run`) with detected changes in `client/web/**`.
  * **Build:** Configures Node.js, resolves dependencies, and executes `npm run build`. GitHub Variables (e.g., `VITE_API_BASE_URL`) are injected into the environment during build-time to replace Vite placeholders.
  * **Deploy:** Pushes the compiled static `dist` folder to Firebase Hosting using the Firebase Extended action.

* **Mobile App (Kotlin -> Firebase App Distribution)**
  * **Trigger:** Successful workflow run with detected changes in `client/mobile/**`.
  * **Build:** Configures JDK 21 (Temurin), grants Gradle execution permissions, and assembles the release APK.
  * **Deploy:** Shreds decoded keystore post-build for security, then uploads the `app-release.apk` to Firebase App Distribution for the `qa-team` testing group.

---

## 3. Pipeline Architecture Diagram

```mermaid
graph TD
    A[Push/Merge to Main/Develop Branch] --> B{Path Filter Detection}

    %% Security Flow
    A --> S[Secret Scanner, Semgrep SAST, Trivy SCA & Dependabot]

    classDef wideBox width:350px;
    class A,S wideBox;

    %% Backend Flow
    B -->|server/backend/**| C[Backend Job]
    C --> D[Setup JDK 21]
    D --> E[Maven Build JAR]
    E --> F[SCP Transfer to AWS EC2]
    F --> G[Deploy to Idle Port 8080/8081]
    G --> H[Health Check]
    H --> H2[Nginx Traffic Switch]
    H2 --> LIVE_B((Backend Live))

    %% Web Flow
    B -->|client/web/**| W_E2E[E2E Playwright Tests Job]
    W_E2E -->|On Success| I[Web Deploy Job]
    I --> J[Setup Node.js]
    J --> K[Inject Env Vars]
    K --> L[Vite Build Static Assets]
    L --> M[Deploy via Firebase CLI]
    M --> N((Web Frontend Live))

    %% Mobile Flow
    B -->|client/mobile/**| O[Mobile Job]
    O --> P[Setup JDK 21]
    P --> Q[Gradle Assemble Release APK]
    Q --> R[Upload to Firebase App Distribution]
    R --> T((Mobile Ready for QA))

    %% Styling Classes
    classDef backend fill:#e3f2fd,stroke:#1565c0,stroke-width:2px,color:#333;
    classDef web fill:#fff3e0,stroke:#e65100,stroke-width:2px,color:#333;
    classDef mobile fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,color:#333;
    classDef live fill:#fce4ec,stroke:#388e3c,stroke-width:2px,color:#333;
    classDef testing fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#333;

    %% Class Assignments
    class C,D,E,F,G,H,H2 backend;
    class I,J,K,L,M web;
    class W_E2E testing;
    class O,P,Q,R mobile;
    class LIVE_B,N,T live;

```

---

## 4. Identified Gaps & Future Enhancements

While the current pipeline fulfils standard CI/CD requirements, several architectural gaps exist that can be optimised for a production-grade environment.

### Gap 1: Direct Host Execution (Lack of Containerization)

* **Current State:** The JAR file runs directly on the EC2 OS, making it vulnerable to environment drift (e.g., Java version mismatches).
* **Future Enhancement:** Introduce **Docker**. Containerize the Spring Boot application and update the GitHub Action to push the image to a registry (like Docker Hub or AWS ECR). Deploying a container ensures the application runs exactly as it did during testing.

### Gap 2: Skipped Automated Testing in CD

* **Current State:** The backend build executes `mvn package -DskipTests`, bypassing unit and integration tests during deployment to speed up execution.
* **Future Enhancement:** Split the backend pipeline into discrete `Test` and `Deploy` jobs. Run Unit/Integration tests automatically, and only allow the SCP deployment to proceed if the `Test` job passes.

### Gap 3: Manual Database Schema Management

* **Current State:** There is no automated workflow for executing database schema changes (DDL) on the AWS RDS instance.
* **Future Enhancement:** Integrate a database migration tool like **Flyway** or **Liquibase** into the Spring Boot backend. This allows the application to automatically and safely apply version-controlled SQL scripts upon startup.

### Gap 4: Mobile App Delivery Limits

* **Current State:** The mobile application is delivered to Firebase App Distribution, requiring manual download by testers.
* **Future Enhancement:** Integrate **Fastlane** into the GitHub Actions mobile workflow to automate signing, screenshot generation, and direct publishing to the Google Play Store internal testing tracks.

```

```

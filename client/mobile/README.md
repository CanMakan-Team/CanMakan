# CanMakan Mobile

Android client for scanning packaged food and presenting dietary verdicts from the Spring Boot API. The Jetpack Compose screens were originally supplied by Kwok Heng from the team's approved mockups; scan, history, family, and auth now call the live backend.

## Current status

Live slices: UC18 registration, UC19 session, dietary restrictions, family (create/invite/switch), barcode scan/assess, scan history, notifications. An authenticated USER without a dietary profile enters the consumer shell; Scanner and History stay reachable with in-context setup actions, and profile-dependent network calls stay disabled until a positive, account-owned profile is active.

UC18 registration remains account creation only. After `201`, Android uses the normal login repository/session store and opens optional authenticated profile setup. Set Up Later creates no profile; profile failure leaves both account and session intact.

## Design Principles

1. **Feature packages match backend & web names** for easier cross-platform reasoning.
2. [`shared`](app/src/main/java/sg/edu/nus/iss/canmakan/shared/di/README.md) contains only technical shared code (network, DI, UI kit, utils).
3. Business logic and screens live inside their feature.
4. Start as a single module. Extract real Gradle modules later only if needed.

## Feature Overview

| Feature | Purpose |
| --- | --- |
| [`auth`](app/src/main/java/sg/edu/nus/iss/canmakan/features/auth/README.md) | Login, logout, session handling |
| [`account`](app/src/main/java/sg/edu/nus/iss/canmakan/features/account/README.md) | Notification preference and delete account |
| [`dietaryprofile`](app/src/main/java/sg/edu/nus/iss/canmakan/features/dietaryprofile/README.md) | User dietary preferences and constraints |
| [`family`](app/src/main/java/sg/edu/nus/iss/canmakan/features/family/README.md) | Family members and active profile switching |
| [`product`](app/src/main/java/sg/edu/nus/iss/canmakan/features/product/scan/README.md) | Scanning, verdicts, recommendations, history |
| [`notifications`](app/src/main/java/sg/edu/nus/iss/canmakan/features/notifications/README.md) | Account inbox |

## Technology

- Android with Jetpack Compose
- Package/application ID: `sg.edu.nus.iss.canmakan`
- Compile SDK and target SDK: 37
- Minimum SDK: 26
- Android Gradle Plugin: 8.5.0
- Kotlin: 1.9.24
- Java and Kotlin JVM target: 21
- Gradle Wrapper: 8.7

## Build and run

Prerequisites:

- JDK 21 (Android builds remain on JVM 21)
- Android SDK Platform 34
- Android Studio or an Android SDK configured through the normal local
  environment

From `client/mobile` on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

To run the app interactively, open this directory in Android Studio, select an
Android device or emulator running API 26 or newer, and run the `app`
configuration. Keep machine-specific SDK paths in an untracked
`local.properties` file. If your backend is not on the default emulator host,
add a `BASE_URL` entry such as `BASE_URL=http://192.168.1.50:8080/api/` in
`client/mobile/local.properties` (or pass `-PBASE_URL=...` when building).

Cleartext HTTP is supported only by the Debug build for emulator and local
development. Release builds have no emulator fallback and require an explicitly
configured `BASE_URL` with an HTTPS scheme, a host, and a trailing slash. For
example, supply the deployment-managed value without storing it in the project:

```powershell
.\gradlew.bat :app:assembleRelease "-PBASE_URL=$env:CANMAKAN_RELEASE_API_URL"
```

`assembleRelease` / `bundleRelease` fail closed when the value is missing,
malformed, uses HTTP, or lacks the Retrofit-required trailing slash. Unit tests
do not require a release HTTPS URL; they still use your local `BASE_URL` for
debug.

CI runs `testDebugUnitTest` then Gradle `sonar` for `canmakan-mobile`. Notification
preference logic is covered in `FamilyProfileRepositoryTest` and
`CanMakanNavGraphViewModelTest`. Sonar coverage exclusions omit Compose screens
(`*Screen*.kt`), sheets, nav graphs, shared UI widgets, `MainActivity`,
`BarcodeAnalyzer`, `AndroidSystemNotifier`, Hilt modules, and generated DI; those
Kotlin files remain in Semgrep/Sonar issue scans. Launcher images stay under
`app/src/main/res`. Mascot PNGs live in `client/shared/assets/mascot/drawable/`
and are wired in as an extra Android `res` directory at
`client/shared/assets/mascot/` (resource-type folders only). Those binaries are excluded from
Sonar analysis (they are not UTF-8 sources). Generated Hilt/Dagger
output is not a coverage target.
See `local.properties.example`. The backend listens on `0.0.0.0:8080` so the
debug build can reach emulator (`10.0.2.2`) and LAN endpoints. Native Retrofit
does not use browser CORS. The main/release network-security configuration
blocks cleartext globally; only the debug resource permits local HTTP.
Android login, refresh, logout, and self-service account deletion requests also
send the backend's non-secret session-intent header. Native calls do not need an
Origin header and are not identified by User-Agent.

# CanMakan Mobile

The CanMakan mobile client is an Android UI prototype for scanning packaged food
and presenting dietary and ingredient information. This initial Jetpack Compose
UI implementation was supplied by Kwok Heng from the team's approved mobile
screenshots.

## Current status

The project currently contains the supplied UI prototype, including the scanner,
scan history, product detail, dietary-requirement editor, profile drawer, bottom
navigation, theme, and supporting sample models and sample data.

Backend integration exists for selected vertical slices, including UC18 user
registration, dietary restrictions, family profile reads, scan assessment and
scan history. Some supplied screens still contain prototype-only callbacks or
sample presentation data.

The application root now validates the encrypted authentication session before
showing either the Login/Registration flow or the consumer mobile flow. UC18
registration remains account creation only. After `201`, Android uses the normal
login repository/session store and opens optional authenticated profile setup.
Set Up Later creates no profile; profile failure leaves both account and session
intact.

An authenticated USER without a dietary profile enters the normal consumer
shell. Scanner and History remain reachable and present in-context setup actions,
while their profile-dependent network operations remain disabled until a positive,
account-owned profile is active.

## Design Principles

1. **Feature packages match backend & web names** for easier cross-platform reasoning.
2. **core** contains only technical shared code (network, DI, UI kit, utils).
3. Business logic and screens live inside their feature.
4. Start as a single module. Extract real Gradle modules later only if needed.

## Feature Overview

```
| Feature            | Purpose                                      |
|--------------------|----------------------------------------------|
| `auth`             | Login, logout, session handling              |
| `account`          | Notification preference and delete account   |
| `dietaryprofile`   | User dietary preferences and constraints     |
| `family`           | Family members and active profile switching  |
| `product`          | Scanning, verdicts, recommendations, history |
| `analytics`        | Lightweight trends / stats (optional)        |
```

## Technology

- Android with Jetpack Compose
- Package/application ID: `com.example.canmakan`
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
Kotlin files remain in Semgrep/Sonar issue scans. Launcher and mascot images are
excluded from Sonar analysis (they are not UTF-8 sources). Generated Hilt/Dagger
output is not a coverage target.
See `local.properties.example`. The backend listens on `0.0.0.0:8080` so the
debug build can reach emulator (`10.0.2.2`) and LAN endpoints. Native Retrofit
does not use browser CORS. The main/release network-security configuration
blocks cleartext globally; only the debug resource permits local HTTP.
Android login, refresh, logout, and self-service account deletion requests also
send the backend's non-secret session-intent header. Native calls do not need an
Origin header and are not identified by User-Agent.

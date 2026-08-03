# CanMakan Mobile

The CanMakan mobile client is an Android UI prototype for scanning packaged food
and presenting dietary and ingredient information. This initial Jetpack Compose
UI implementation was supplied by Kwok Heng from the team's approved mobile
screenshots.

## Current status

The project currently contains the supplied UI prototype, including the scanner,
scan history, product detail, dietary-requirement editor, profile drawer, bottom
navigation, theme, and supporting sample models and sample data.

Barcode camera integration and backend integration are not implemented yet. All
product and profile information shown by the prototype is sample data.

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

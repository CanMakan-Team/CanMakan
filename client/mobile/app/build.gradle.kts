import com.android.build.api.dsl.ApplicationExtension
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val configuredBaseUrl = localProperties.getProperty("BASE_URL")
    ?: project.findProperty("BASE_URL")?.toString()

val debugBaseUrl = configuredBaseUrl
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "http://10.0.2.2:8080/api/"
val normalizedDebugBaseUrl = if (debugBaseUrl.endsWith("/")) {
    debugBaseUrl
} else {
    "$debugBaseUrl/"
}
val releaseBaseUrl = configuredBaseUrl?.trim().orEmpty()

fun buildConfigString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

fun validateReleaseBaseUrl(value: String) {
    if (value.isBlank()) {
        throw GradleException(
            "Release BASE_URL is required. Supply an explicit HTTPS URL ending in '/'."
        )
    }
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        throw GradleException("Release BASE_URL must be a valid HTTPS URL ending in '/'.")
    }
    if (!uri.scheme.equals("https", ignoreCase = true)) {
        throw GradleException("Release BASE_URL must use HTTPS.")
    }
    if (uri.host.isNullOrBlank()) {
        throw GradleException("Release BASE_URL must include a host.")
    }
    if (!value.endsWith("/")) {
        throw GradleException("Release BASE_URL must end in '/'.")
    }
    if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
        throw GradleException(
            "Release BASE_URL must not contain user info, a query, or a fragment."
        )
    }
}

val validateReleaseBaseUrlTask = tasks.register("validateReleaseBaseUrl") {
    group = "verification"
    description = "Fails closed unless the Release API base URL is explicitly configured for HTTPS."
    doLast {
        validateReleaseBaseUrl(releaseBaseUrl)
    }
}

// Keep validation on shipping tasks only. Unit tests depend on release manifest/
// resource packaging (UC19 transport checks) and must tolerate a local HTTP
// BASE_URL used by the debug build.
tasks.matching { task ->
    task.name == "assembleRelease" || task.name == "bundleRelease"
}.configureEach {
    dependsOn(validateReleaseBaseUrlTask)
}

// UC19 unit verification reads Android Gradle Plugin's merged variant outputs.
tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(
        "processDebugManifest",
        "processReleaseManifest",
        "packageDebugResources",
        "packageReleaseResources",
    )
}

extensions.configure<ApplicationExtension> {
    namespace = "sg.edu.nus.iss.canmakan"
    compileSdk = 37

    defaultConfig {
        applicationId = "sg.edu.nus.iss.canmakan"
        minSdk = 26
        targetSdk = 37

        // CMK-55 Dynamically Assign Versioning from CI/CD, Falling Back to Local Defaults
        versionCode = if (project.hasProperty("versionCode")) {
            project.property("versionCode").toString().toIntOrNull() ?: 1
        } else {
            1
        }

        versionName = if (project.hasProperty("versionName")) {
            project.property("versionName").toString()
        } else {
            "1.0.0-dev"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // CMK-55: Define the Release Keystore Signing Configuration
    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEYSTORE_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "BASE_URL",
                buildConfigString(normalizedDebugBaseUrl),
            )
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", buildConfigString(releaseBaseUrl))
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.AMAZON)
    }
}

dependencies {
    // 1. Core Android & Jetpack Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)

    // 2. Icon sets used for the menu, notification, back, close, and other icons.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // 3. CameraX (For real-time barcode scanning feed)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // 4. Google ML Kit (Barcode Scanning)
    implementation(libs.barcode.scanning)

    // 5. Networking (Retrofit & OkHttp to connect to Java SpringBoot)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)

    // 6. Security & Cryptography (Biometric Authentication)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // 7. Coroutines (Async Operations)
    implementation(libs.kotlinx.coroutines.android)

    // 8. Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // 9. Hilt, Compose, Timber
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.timber)

    // 10. FireBase SDK
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
}

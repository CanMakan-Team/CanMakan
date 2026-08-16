// Top-level build file where common configuration options for all
// modules in this project are declared.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("org.sonarqube") version "7.3.1.8318"
}

sonar {
    properties {
        property("sonar.projectKey", "canmakan-mobile")
        property("sonar.projectName", "canmakan-mobile")
        property("sonar.organization", "canmakan-team")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sourceEncoding", "UTF-8")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${rootProject.projectDir}/app/build/sonar-coverage/jacoco.xml",
        )
        // Binaries are not UTF-8 sources; skip analysis so the scanner does not warn on icons.
        property(
            "sonar.exclusions",
            listOf(
                "**/*.webp",
                "**/*.png",
                "**/*.jpg",
                "**/*.jpeg",
                "**/*.gif",
            ).joinToString(","),
        )
        property(
            "sonar.coverage.exclusions",
            listOf(
                "**/*Screen*.kt",
                "**/*Sheet.kt",
                "**/*NavGraph.kt",
                "**/CanMakanApp.kt",
                "**/MainActivity.kt",
                "**/ProfileDrawerContent.kt",
                "**/BarcodeAnalyzer.kt",
                "**/AndroidSystemNotifier.kt",
                "**/shared/ui/**",
                "**/*Module.kt",
                "**/*_Factory*",
                "**/*_HiltModules*",
                "**/Hilt_*",
                "**/Dagger*",
                "**/*_GeneratedInjector*",
                "**/*MembersInjector*",
            ).joinToString(","),
        )
    }
}

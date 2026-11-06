import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("projectConfig")
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.android.compose.screenshot") version "0.0.1-alpha15" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin.core}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${Versions.Dagger.core}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.Kotlin.core}")
        classpath("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.8")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        listOf("n2k02theme", "n2k02updater").forEach { library ->
            maven {
                url = uri("https://maven.pkg.github.com/N2K02/$library")
                credentials {
                    username = "N2K02"
                    password = providers.environmentVariable("N2K02_PACKAGES_TOKEN").orNull
                }
                content { includeGroup("dev.n2k02") }
            }
        }
    }
}

// JVM tests run under Robolectric on the build host, not on Android.
// The embedded ADB client needs conscrypt-android in the real APK, but that AAR's
// Java classes call System.loadLibrary("conscrypt_jni"), which only exists for
// Android ABIs. When that AAR leaks into a host test runtime, Robolectric fails
// before any test executes (hundreds of identical UnsatisfiedLinkErrors).
//
// Keep the production dependency untouched. Replace ONLY JVM test runtime's
// Android JNI provider with the official host OpenJDK uber jar, in every module
// (including modules which gain app-common-io transitively via test helpers).
subprojects {
    configurations.configureEach {
        if (name.endsWith("UnitTestRuntimeClasspath")) {
            exclude(group = "org.conscrypt", module = "conscrypt-android")
        }
    }
    pluginManager.withPlugin("com.android.application") {
        dependencies.add("testRuntimeOnly", "org.conscrypt:conscrypt-openjdk-uber:2.7.0")
    }
    pluginManager.withPlugin("com.android.library") {
        dependencies.add("testRuntimeOnly", "org.conscrypt:conscrypt-openjdk-uber:2.7.0")
    }
}

// --- Code coverage (Kover) -------------------------------------------------------------------
// Aggregates unit-test coverage for the core library modules into one merged report. Run:
//   ./gradlew :koverHtmlReport       (merged HTML, at build/reports/kover/html/index.html)
//   ./gradlew :koverXmlReport        (merged XML, for CI)
//   ./gradlew :koverPrintCoverage    (prints the merged % to the console)
// Per-module reports use the variant-suffixed tasks instead, e.g.
//   ./gradlew :app-common:koverHtmlReportDebug
// The package-scoped coverage gate lives in app-common/build.gradle.kts (koverVerifyDebug).
apply(plugin = "org.jetbrains.kotlinx.kover")

dependencies {
    "kover"(project(":app-common"))
    "kover"(project(":app-common-shell"))
    "kover"(project(":app-common-io"))
    "kover"(project(":app-common-root"))
    "kover"(project(":app-common-adb"))
    "kover"(project(":app-tool-corpsefinder"))
    "kover"(project(":app-tool-systemcleaner"))
    "kover"(project(":app-tool-appcleaner"))
    "kover"(project(":app-tool-deduplicator"))
    "kover"(project(":app-tool-squeezer"))
    "kover"(project(":app-tool-analyzer"))
    "kover"(project(":app-tool-swiper"))
    "kover"(project(":app-tool-appcontrol"))
    "kover"(project(":app-tool-scheduler"))
}

configure<KoverProjectExtension> {
    reports {
        filters {
            excludes {
                // Generated / boilerplate — not meaningful to measure.
                annotatedBy(
                    "dagger.Module",
                    "dagger.internal.DaggerGenerated",
                    "javax.annotation.processing.Generated",
                )
                classes(
                    "*_Factory", "*_Factory\$*", "*_MembersInjector",
                    "Hilt_*", "*.Hilt_*", "*.Dagger*", "hilt_aggregated_deps.*", "*_HiltModules*",
                    "*.BuildConfig", "*.R", "*.R\$*", "*.databinding.*",
                    // AIDL-generated binder code (Stub / Stub$Proxy / Default) across all modules.
                    "*\$Stub", "*\$Stub\$*", "*\$Default",
                )
            }
        }
    }
}

tasks.register("clean").configure {
    delete("build")
}

tasks.register("testToolModules") {
    description = "Run unit tests for all app-tool-* library modules"
    dependsOn(subprojects.filter { it.name.startsWith("app-tool") }.map { ":${it.name}:testDebugUnitTest" })
}

tasks.register("testCommonModules") {
    description = "Run unit tests for all app-common-* library modules"
    dependsOn(subprojects.filter { it.name.startsWith("app-common") }.map { ":${it.name}:testDebugUnitTest" })
}
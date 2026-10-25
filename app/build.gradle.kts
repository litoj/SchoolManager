import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cz.litoj.schlmgr"
    compileSdk = 37

    defaultConfig {
        applicationId = "cz.litoj.schlmgr"
        minSdk = 30
        targetSdk = 37
        versionCode = 69
        versionName = "2.0.4"
        signingConfig = signingConfigs.getByName("debug")
    }

    // Release signing values live in keystore.properties, which is not in VCS.
    // The release build type always uses this config, so release is never built
    // with the debug key; a missing file fails the release build.
    val keystoreProperties = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreProperties.load(FileInputStream(keystoreFile))
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
        }
    }

    // Only en and cs ship; this drops the many locales pulled in by libraries.
    androidResources {
        localeFilters += listOf("en", "cs")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The app is distributed as an APK (GitHub releases), not through Play;
    // the dependency-metadata blob is dead weight here.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        // Robolectric reads JVM class files while it instruments; the system's
        // newest JDK can be newer than its ASM supports, so the tests always
        // run on 21 whatever JVM the daemon uses
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // The CI gate only runs lint, so report the CI severity locally: a
        // warning here is what fails the gate, not just an error.
        warningsAsErrors = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// The unit test runs report to the shell: every failed test with its full
// exception, every class one summary line, the whole run one total line —
// the XML results stay an IDE artifact, not the only place to read the
// outcome from.
tasks.withType<Test>().configureEach {
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            // a class suite: one line; the run's root: the total; a framework's
            // own aggregate suite between them stays silent
            when {
                suite.className != null ->
                    println("${suite.className}: ${result.testCount} tests, " +
                        "${result.failedTestCount} failed, ${result.skippedTestCount} skipped, " +
                        "${result.endTime - result.startTime} ms")
                suite.parent == null ->
                    println("Total: ${result.testCount} tests, ${result.failedTestCount} failed, " +
                        "${result.skippedTestCount} skipped")
            }
        }
    })
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hjson)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    // The component tests drive real gestures on a composed list (Robolectric hosts
    // the activity the rule needs, so the manifest artifact is a debug dependency).
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The app is Material 2 throughout — material 3 only ever arrives transitively
// (the debug ui-tooling pulls it in). Dropping it keeps the classpath honest and
// keeps the material2/material3 mixing lint check from firing on the tooling's
// own dependency (a plain dependency exclude does not survive the artifact's
// android variant).
configurations.configureEach {
    exclude(group = "androidx.compose.material3", module = "material3")
}

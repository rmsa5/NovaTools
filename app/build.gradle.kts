import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ktlint)
}

// Version information derived from Git, so every build shows exactly what it was built from.
// The short hash matches the commit IDs in Android Studio's Git > Log tab.
val gitCommitCount: Provider<Int> = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.map { it.trim().toInt() }
val gitShortHash: Provider<String> = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
    .standardOutput.asText.map { it.trim() }
val gitHasChanges: Provider<Boolean> = providers.exec {
    commandLine("git", "status", "--porcelain")
    // Read-only status check: don't take Git's index lock while Android Studio may be using it
    environment("GIT_OPTIONAL_LOCKS", "0")
}.standardOutput.asText.map { it.isNotBlank() }

/** Build time to the minute, shown in the app so two builds of the same uncommitted code can be told apart. */
abstract class BuildTimeSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
val buildTime: Provider<String> = providers.of(BuildTimeSource::class.java) {}

android {
    namespace = "de.langerhans.odintools"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.rmsa5.novatools"
        minSdk = 33
        targetSdk = 36
        // Always increases: one step per commit
        versionCode = gitCommitCount.get()
        // e.g. "0.1.0-c9e0cd3", with ".dirty" when built with uncommitted changes
        versionName = "0.1.0-${gitShortHash.get()}" + if (gitHasChanges.get()) ".dirty" else ""
        buildConfigField("String", "BUILD_TIME", "\"${buildTime.get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            initWith(buildTypes.getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ktlint {
    reporters {
        reporter(ReporterType.SARIF)
    }
    relative.set(true)
}

dependencies {
    // Compose BOM specifics
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)

    // Normal imports
    implementation(libs.bundles.app)
    debugImplementation(libs.bundles.appDebug)
    ksp(libs.bundles.appKsp)
    testImplementation(libs.bundles.appUnitTest)
    androidTestImplementation(libs.bundles.appAndroidTest)
}

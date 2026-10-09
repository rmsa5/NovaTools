import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "de.langerhans.odintools"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.rmsa5.novatools"
        minSdk = 33
        targetSdk = 36
        versionCode = 11
        versionName = "1.3.1"

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

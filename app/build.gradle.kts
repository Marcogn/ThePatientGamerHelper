import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.marcogn.thepatientgamerhelper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marcogn.thepatientgamerhelper"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OAuth 2.0 "Web application" client ID (Google Cloud Console > APIs & Services >
        // Credentials, same project as the Drive appdata scope + the companion Android OAuth
        // client's SHA-1 — see CLAUDE.md, "Phase 4" section). Used by Credential Manager
        // (GetGoogleIdOption.serverClientId) for "Sign in with Google". Not a secret in the
        // strictest sense (Google doesn't require it kept confidential, and it ends up baked
        // into the APK regardless), but kept out of version control on request: read from the
        // gitignored local.properties (key DRIVE_OAUTH_WEB_CLIENT_ID) rather than a committed
        // resource. Falls back to the placeholder DriveAuthManager.isConfigured() already knows
        // how to detect when the property is absent (e.g. on CI, which has no local.properties).
        resValue("string", "google_oauth_web_client_id", driveOAuthWebClientId())
    }

    // The release keystore's SHA-1 is what's registered on the Android OAuth client in Google
    // Cloud Console (Sign in with Google) — unlike a machine-local debug.keystore (regenerated,
    // with a different SHA-1, every time it's missing), this one is generated once and stored as
    // a GitHub Actions secret so build-apk.yml produces the same signature on every run. See
    // CLAUDE.md, "Phase 4" section, "Persistent release signing" note.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Enables the debug-only seed data flow (see debug/DebugSeeder.kt).
            buildConfigField("boolean", "SEED_DEBUG_DATA", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "SEED_DEBUG_DATA", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Left unsigned (Android's default) when the release secrets aren't present — e.g. a
            // local ./gradlew assembleRelease with no CI secrets — same graceful-fallback pattern
            // as driveOAuthWebClientId() below, rather than failing the build outright.
            if (!System.getenv("RELEASE_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    // Fase 4 — Backup cloud Google Drive
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Fase 5 — Tema (DataStore) e lingua per-app (AppCompatDelegate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Local dev builds read local.properties; CI has no such file (it's gitignored, never checked
// out) so it instead passes the same value via the DRIVE_OAUTH_WEB_CLIENT_ID env var, sourced
// from a GitHub Actions repository secret — see .github/workflows/build-apk.yml and
// android-ci.yml. Either source missing/blank falls back to the existing placeholder. The
// placeholder is inlined rather than a top-level val: android { defaultConfig { ... } } above
// evaluates immediately, before the script reaches any later top-level property's initializer,
// so a top-level val referenced from here would still be null at that point (a real bug hit in
// CI — see CLAUDE.md, "Phase 4" section).
fun driveOAuthWebClientId(): String {
    val fromEnv = System.getenv("DRIVE_OAUTH_WEB_CLIENT_ID")
    if (!fromEnv.isNullOrBlank()) return fromEnv

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { localProperties.load(it) }
    }
    return localProperties.getProperty("DRIVE_OAUTH_WEB_CLIENT_ID") ?: "[TO_COMPLETE]"
}

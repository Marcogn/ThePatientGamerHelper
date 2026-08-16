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
        versionCode = 1
        versionName = "0.1.0"

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

private const val OAUTH_PLACEHOLDER = "[TO_COMPLETE]"

fun driveOAuthWebClientId(): String {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { localProperties.load(it) }
    }
    return localProperties.getProperty("DRIVE_OAUTH_WEB_CLIENT_ID") ?: OAUTH_PLACEHOLDER
}

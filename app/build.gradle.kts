import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Backend config lives in local.properties (gitignored). The publishable key is
// safe in a shipped APK — row-level security is what protects the data — but
// keeping it out of the repo means a fork gets its own project, not ours.
val backend = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Release signing: credentials come from environment variables first, then
// local.properties. For a signed release set (never commit them):
//   local.properties keys: ironvellum.keystore.path, ironvellum.keystore.password,
//                          ironvellum.key.alias, ironvellum.key.password
//   env equivalents:       IRONVELLUM_KEYSTORE_PATH, IRONVELLUM_KEYSTORE_PASSWORD,
//                          IRONVELLUM_KEY_ALIAS, IRONVELLUM_KEY_PASSWORD
// With anything missing (or the keystore file absent) the release build still
// configures and simply produces an unsigned APK. Resolved at the top level so
// the release validation task can report the same state the signing block sees.
val keystorePath = System.getenv("IRONVELLUM_KEYSTORE_PATH")
    ?: backend.getProperty("ironvellum.keystore.path")
val keystorePassword = System.getenv("IRONVELLUM_KEYSTORE_PASSWORD")
    ?: backend.getProperty("ironvellum.keystore.password")
// Named *Value to avoid shadowing SigningConfig.keyAlias/keyPassword inside create("release").
val keyAliasValue = System.getenv("IRONVELLUM_KEY_ALIAS") ?: backend.getProperty("ironvellum.key.alias")
val keyPasswordValue = System.getenv("IRONVELLUM_KEY_PASSWORD") ?: backend.getProperty("ironvellum.key.password")
val keystoreFile = keystorePath?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
val signingComplete = !keystorePassword.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank() &&
    !keyPasswordValue.isNullOrBlank() &&
    keystoreFile?.isFile == true

android {
    namespace = "com.ironvellum.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ironvellum.app"
        minSdk = 29
        // 36 (Android 16) is the newest STABLE level and Play's floor from
        // Aug 2026. 37 is Android 17 beta: a production upload targeting a
        // non-final platform is not distributable. compileSdk may stay ahead.
        targetSdk = 36
        // versionCode MUST be incremented for every Play Store upload; a reused
        // versionCode is rejected by the store. Keep versionName in sync with
        // the release tag. No git-derived scheme — bump it by hand.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${backend.getProperty("supabase.url", "")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_KEY",
            "\"${backend.getProperty("supabase.key", "")}\"",
        )
        // Google sign-in needs the OAuth *Web* client id (not the Android one):
        // Credential Manager sends it as the audience, and Supabase validates
        // the resulting ID token against the same id. Blank disables the button.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${backend.getProperty("google.webClientId", "")}\"",
        )
    }

    if (signingComplete) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingComplete) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Ships the en-XA / ar-XB pseudolocale resources. Without them a
            // per-app locale of ar-XB is filtered out as unsupported and falls
            // back to English, so an RTL check silently measures an LTR layout
            // and reports no problems — which is what happened the first time
            // this was tried.
            isPseudoLocalesEnabled = true
        }
    }
    sourceSets {
        // Exported Room schema JSONs, consumed by MigrationTestHelper on device.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Fail fast on a misconfigured release: a release APK built with blank Supabase
// or Google credentials ships cloud/sign-in/social features silently dead.
// Debug builds keep working with blanks. Scoped via dependsOn on the release
// tasks (not configuration time) so configuring assembleDebug never fails on
// absent release secrets.
val validateReleaseBackend by tasks.registering {
    doLast {
        val missing = buildList {
            if (backend.getProperty("supabase.url", "").isBlank()) add("supabase.url")
            if (backend.getProperty("supabase.key", "").isBlank()) add("supabase.key")
            if (backend.getProperty("google.webClientId", "").isBlank()) add("google.webClientId")
        }
        check(missing.isEmpty()) {
            "Release build requires backend credentials. " +
                "Missing local.properties keys: ${missing.joinToString(", ")}. " +
                "(Debug builds do not need them.)"
        }
        // Warn, don't fail: an unsigned assembleRelease must stay usable as a
        // local smoke build, so silence about app-release-unsigned.apk is the
        // thing to remove, not the build itself.
        if (!signingComplete) {
            logger.lifecycle(
                "WARNING: release will be UNSIGNED (app-release-unsigned.apk). " +
                    "For a signed build set local.properties keys: " +
                    "ironvellum.keystore.path, ironvellum.keystore.password, " +
                    "ironvellum.key.alias, ironvellum.key.password " +
                    "(or env IRONVELLUM_KEYSTORE_PATH, IRONVELLUM_KEYSTORE_PASSWORD, " +
                    "IRONVELLUM_KEY_ALIAS, IRONVELLUM_KEY_PASSWORD)."
            )
        }
    }
}
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    .configureEach { dependsOn(validateReleaseBackend) }
tasks.matching { it.name == "packageRelease" }.configureEach { mustRunAfter(validateReleaseBackend) }

// Export Room's schema JSON for migration testing and history.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Compose UI tests. The BOM is applied to the androidTest classpath too, so
    // ui-test tracks the same Compose version the app is built against.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}

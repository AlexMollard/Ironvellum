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

android {
    namespace = "com.monarch.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.monarch.app"
        minSdk = 29
        targetSdk = 37
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

    // Release signing: credentials come from environment variables first, then
    // local.properties. For a signed release set (never commit them):
    //   local.properties keys: monarch.keystore.path, monarch.keystore.password,
    //                          monarch.key.alias, monarch.key.password
    //   env equivalents:       MONARCH_KEYSTORE_PATH, MONARCH_KEYSTORE_PASSWORD,
    //                          MONARCH_KEY_ALIAS, MONARCH_KEY_PASSWORD
    // With anything missing (or the keystore file absent) the release build
    // still configures and simply produces an unsigned APK.
    val keystorePath = System.getenv("MONARCH_KEYSTORE_PATH")
        ?: backend.getProperty("monarch.keystore.path")
    val keystorePassword = System.getenv("MONARCH_KEYSTORE_PASSWORD")
        ?: backend.getProperty("monarch.keystore.password")
    // Named *Value to avoid shadowing SigningConfig.keyAlias/keyPassword inside create("release").
    val keyAliasValue = System.getenv("MONARCH_KEY_ALIAS") ?: backend.getProperty("monarch.key.alias")
    val keyPasswordValue = System.getenv("MONARCH_KEY_PASSWORD") ?: backend.getProperty("monarch.key.password")
    val keystoreFile = keystorePath?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
    val signingComplete = !keystorePassword.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank() &&
        keystoreFile?.isFile == true
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
// credentials ships the whole social feature silently dead. Debug builds keep
// working with blanks.
val validateReleaseBackend by tasks.registering {
    doLast {
        val missing = buildList {
            if (backend.getProperty("supabase.url", "").isBlank()) add("supabase.url")
            if (backend.getProperty("supabase.key", "").isBlank()) add("supabase.key")
        }
        check(missing.isEmpty()) {
            "Release build requires Supabase credentials. Add to local.properties: " +
                missing.joinToString(", ")
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
}

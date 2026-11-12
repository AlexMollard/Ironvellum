plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ironvellum.watchprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ironvellum.watchprobe"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-probe"
    }
    buildFeatures { buildConfig = true }
    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            applicationIdSuffix = ".phone"
            buildConfigField("boolean", "IS_WATCH", "false")
        }
        create("wear") {
            dimension = "device"
            applicationIdSuffix = ".wear"
            buildConfigField("boolean", "IS_WATCH", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { warningsAsErrors = true }
}

dependencies {
    // MIT-licensed reference Noise implementation; no Google Play services dependency.
    implementation("org.signal.forks:noise-java:0.1.1")
    testImplementation(libs.junit)
}

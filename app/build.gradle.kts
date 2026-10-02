import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing. keystore.properties (gitignored) holds storeFile, storePassword, keyAlias and keyPassword;
// without it release builds fall back to the debug key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.paul.kodiremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.paul.kodiremote"
        minSdk = 30
        targetSdk = 36
        // CI passes -Pkodiremote.versionCode=<run number> (and versionName from a v* tag) so successive builds can update each other.
        versionCode = providers.gradleProperty("kodiremote.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("kodiremote.versionName").getOrElse("1.0")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Compose is much slower in debuggable, unshrunk builds; install this one to use the app
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Debug key unless a release keystore is configured, so it installs over the debug build
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
}

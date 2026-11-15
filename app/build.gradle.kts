plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jagastech.stepwgndsq"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jagastech.stepwgndsq"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0-UltraLight-ZeroBloat"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// ZERO external dependencies! Uses 100% built-in Android OS classes so the APK is < 50 KB
// and consumes less than 8 MB of RAM on a 4GB Android car stereo.
dependencies {
}

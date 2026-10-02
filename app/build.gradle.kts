plugins {
    id("com.android.application")
}

android {
    namespace = "com.dottak.inputdetect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dottak.inputdetect"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
}

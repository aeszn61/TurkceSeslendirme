plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.turkceseslendirme.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.turkceseslendirme.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // ONNX modeli sıkıştırılmadan paketlensin
    androidResources { noCompress += "onnx" }
}

dependencies {
    implementation(project(":tts-tr"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}

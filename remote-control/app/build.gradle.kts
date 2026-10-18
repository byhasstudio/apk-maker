plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.byhasstudio.remotectl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byhasstudio.remotectl"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        getByName("debug") {
            // Keystore debug yang DI-PIN (dikomit ke repo, bukan rahasia --
            // ini standar untuk keystore debug) supaya SHA-1 aplikasi selalu
            // sama di setiap build GitHub Actions. Kalau tidak di-pin,
            // runner CI yang baru tiap kali jalan akan membuat keystore debug
            // baru secara otomatis dengan SHA-1 berbeda tiap build -- yang
            // bikin Google Sign-In selalu gagal (SHA-1 terdaftar jadi tidak
            // cocok lagi).
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}

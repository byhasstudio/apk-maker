plugins {
    id("com.android.application")
}

android {
    namespace = "com.byhasstudio.crosshair"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byhasstudio.crosshair"
        minSdk = 26
        targetSdk = 34
        versionCode = 10000
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            // Keystore debug yang sama di setiap build (workflow menyalinnya dari
            // remote-control/app/debug.keystore). Tanpa tanda tangan yang konsisten,
            // update otomatis akan ditolak Android ("aplikasi tidak terpasang").
            val ks = file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
        encoding = "UTF-8"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}

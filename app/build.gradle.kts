plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.socramtv.calctaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.socramtv.calctaller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // Lo mínimo posible: la app entera es una WebView. core-ktx y
    // activity-ktx solo dan las utilidades modernas (OnBackPressedCallback,
    // registerForActivityResult) sin arrastrar AppCompat/Material, que aquí
    // no hacen falta (no hay ActionBar, ni botones, ni layouts XML).
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}

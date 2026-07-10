plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Firebase Firestore pulls in the FULL Guava library (via gRPC), which
// already contains com.google.common.util.concurrent.ListenableFuture —
// the same class CameraX needs. If the lightweight "listenablefuture" stub
// artifact also ends up on the classpath (many libraries declare it as a
// transitive dependency), you get two different jars defining the same
// class, and Kotlin refuses to compile with a "conflicting dependencies"
// error. Excluding the stub lets the one real Guava (from Firestore) win.
configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

android {
    namespace = "com.yourname.pillpantry"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourname.pillpantry"
        minSdk = 26
        targetSdk = 34
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Real ListenableFuture (see the configurations.all exclude above, which
    // stops the conflicting lightweight stub from also being present).
    implementation("com.google.guava:guava:33.2.1-android")

    // ML Kit on-device barcode scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Firebase (Auth + Firestore)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Retrofit for Open Food Facts lookups
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Gson is also used directly for backup import/export JSON (see
    // data/BackupModels.kt) — declared explicitly rather than relying on
    // Retrofit's transitive dependency.
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines (+ bridging Firebase Tasks/Play Services callbacks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Best-effort background job for the daily portion decay (see
    // PortionDecayWorker) — WorkManager can't guarantee an exact fire time,
    // which is why FirebaseRepository.applyMissedPortionDecrements also
    // runs as catch-up logic on every app launch.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

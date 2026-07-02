plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.coinswapmobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.coinswapmobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64-v8a = most physical phones; armeabi-v7a = older ARM devices.
        // x86_64 emulator is not supported until libcoinswap_mobile.so is built for it.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Rust .so files built by `cargo ndk -o app/src/main/jniLibs ...` live here.
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")
    androidResources {
        noCompress += listOf("so")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

    dependencies {
        implementation("androidx.core:core-ktx:1.15.0")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
        implementation("androidx.activity:activity-compose:1.10.1")
        implementation("androidx.navigation:navigation-compose:2.8.5")
        implementation("androidx.compose.material:material-icons-extended:1.6.7")
        implementation("androidx.compose.animation:animation:1.6.7")
        implementation(platform("androidx.compose:compose-bom:2024.12.01"))
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-graphics")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.material3:material3")
        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

        // Native coinswap taker library — build from coinswap-ffi/coinswap-kotlin, then restore
        // this line and run:  ./gradlew :lib:publishToMavenLocal -PlocalBuild=true
        // implementation("org.coinswap:coinswap-kotlin:1.0.0")

        androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
        debugImplementation("androidx.compose.ui:ui-tooling")
    }
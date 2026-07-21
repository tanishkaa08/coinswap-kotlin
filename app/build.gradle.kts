plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.coinswapmobile"
    compileSdk = 35

    val demoRegtestHost = (project.findProperty("demoRegtestHost") as? String)?.trim().orEmpty()

    defaultConfig {
        applicationId = "com.example.coinswapmobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optional: gradlew assembleDebug -PdemoRegtestHost=203.0.113.10
        buildConfigField("String", "DEMO_REGTEST_HOST", "\"$demoRegtestHost\"")

        // Official ARM64 UniFFI .so from coinswap-ffi
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            // Unpack .so so JNA can find libcoinswap_ffi.so
            useLegacyPackaging = true
        }
    }

    // Generated UniFFI Kotlin + native libs from coinswap-ffi (do not edit those files).
    // Default: sibling checkout ../coinswap-ffi. Override with -PcoinswapFfiRoot=/path/to/coinswap-ffi
    val coinswapFfiRootProp = (project.findProperty("coinswapFfiRoot") as? String)?.trim().orEmpty()
    val ffiCheckout = if (coinswapFfiRootProp.isNotEmpty()) {
        rootProject.file(coinswapFfiRootProp)
    } else {
        rootProject.file("../coinswap-ffi")
    }
    val ffiLibRoot = ffiCheckout.resolve("coinswap-kotlin/lib")
    require(ffiLibRoot.resolve("src/main/kotlin/org/coinswap/coinswap.kt").isFile) {
        "Missing generated UniFFI sources at ${ffiLibRoot.absolutePath}. " +
            "Expected coinswap-ffi checkout (sibling ../coinswap-ffi or -PcoinswapFfiRoot=...). " +
            "Generate bindings before building."
    }
    sourceSets {
        getByName("main") {
            kotlin.srcDir(ffiLibRoot.resolve("src/main/kotlin"))
            jniLibs.srcDirs(
                ffiLibRoot.resolve("src/main/jniLibs"),
                "src/main/jniLibs",
            )
        }
    }

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Required by generated UniFFI Kotlin (loads libcoinswap_ffi via JNA)
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

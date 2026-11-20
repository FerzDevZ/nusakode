plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "id.kodenusa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.kodenusa.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 23
        versionName = "0.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ditandatangani kunci debug milik mesin build: cukup untuk dibagikan lewat GitHub
            // Releases, dan membuat APK rilis bisa langsung dipasang tanpa langkah apksigner manual.
            // Kunci ini TIDAK boleh dipakai untuk Play Store (ADR-019).
            signingConfig = signingConfigs.getByName("debug")
            ndk {
                // Rilis hanya untuk HP: aapt2 x86/x86_64 (3,3 MB) dibuang. Emulator tetap memakai debug.
                // Jalankan dengan `-PsemuaAbi` untuk menguji kode yang sudah diminifikasi di emulator.
                if (!project.hasProperty("semuaAbi")) {
                    abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Dipakai kartu "Bagikan APK" untuk menandai rilis: buildConfig.VERSION_NAME.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,NOTICE*,LICENSE*}", "DebugProbesKt.bin")
        // aapt2 dikemas sebagai libaapt2.so supaya Android mengekstraknya ke nativeLibraryDir,
        // satu-satunya folder yang boleh dieksekusi di Android 10+ (ADR-021). Tanpa legacy
        // packaging, pustaka dibiarkan terkompresi di dalam APK dan tidak pernah diekstrak.
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:build"))
    implementation(project(":core:project"))
    implementation(project(":core:ai"))
    implementation(project(":core:git"))
    implementation(project(":core:diagnose"))
    implementation(project(":build:tier0"))
    implementation(project(":core:ui"))
    implementation(project(":core:editor"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.launcher)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

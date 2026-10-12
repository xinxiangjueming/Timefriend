plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.timefriend"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.timefriend"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 仅保留实际支持的语言资源（默认 values=简体中文 + 4 套翻译），
        // 裁掉依赖库（androidx/miuix 等）自带的其他 30+ 种语言
        resConfigs("en", "ja", "ko", "zh-rTW")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // miuix UI
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)

    // Haze（弹窗外部 scrim 同窗口采样模糊，对齐 SportLink v1 稳定版）
    implementation(libs.haze)

    // OkHttp for WebDAV
    implementation(libs.okhttp)

    // Encrypted storage for WebDAV credentials
    implementation(libs.androidx.security.crypto)

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

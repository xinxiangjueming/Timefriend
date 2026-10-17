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
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 仅保留实际支持的语言资源（默认 values=简体中文 + 4 套翻译），
        // 裁掉依赖库（androidx/miuix 等）自带的其他 30+ 种语言
        resConfigs("en", "ja", "ko", "zh-rTW")
    }

    // 正式签名密钥库（项目根 timefriend，PKCS12，别名 key0；敏感文件勿入库）
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("timefriend")
            storePassword = "1234567890"
            keyAlias = "key0"
            keyPassword = "1234567890"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    // zstd-jni — Room 库文件备份压缩（Zstandard，压缩率优于 gzip/deflate）
    implementation("com.github.luben:zstd-jni:1.5.7-19@aar")

    // 小米 Vela interconnect（LiFD）手机端 SDK（xms-wearable）。
    // AAR 由官方 Vela 文档站 interconnect 页面的 interconnect_dev_test_demo.zip 提供，
    // 解压后把 xms-wearable-lib_1.4_release.aar 放到 app/libs/ 下（见 app/libs/README.md）。
    // AAR 自带 consumer proguard 规则，开启 minify 无需额外 keep。
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 签名配置从项目根目录的 keystore.properties 读取（该文件不提交版本库）
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "io.ads.peeler"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // modules.lsposed.org 要求仓库名等于 applicationId。
        // 使用 io.github.<用户名> 前缀，避免反向域名验证。
        // 源码包名/namespace 仍是 io.ads.peeler，和 applicationId 解耦。
        applicationId = "io.github.lnzx.peeler"
        minSdk = 29
        targetSdk = 37
        // 版本
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            proguardFiles("proguard-rules.pro")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 日志里的 build 标识直接用 APK 的 versionName，避免代码常量与 build.gradle 漂移。
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    compileOnly(libs.annotation)
    // libxposed 模块只在编译时需要 API 桩，切记用 compileOnly
    compileOnly(libs.api)
}
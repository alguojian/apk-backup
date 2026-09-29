import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.tiqu.extractor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tiqu.extractor"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 生产签名：存在 keystore.properties 时使用
    val signingFile = rootProject.file("keystore.properties")
    if (signingFile.exists()) {
        val values = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = rootProject.file(values.getProperty("storeFile"))
            storePassword = values.getProperty("storePassword")
            keyAlias = values.getProperty("keyAlias")
            keyPassword = values.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (signingFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // 调试包继续用 debug 签名
        }
    }
}

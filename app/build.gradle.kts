plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 版本可由 CI 通过 -PappVersionName=1.2.3 覆盖(打 tag 时自动取 tag 号)
val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.0.0"
val appVersionCode = appVersionName.split(".")
    .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    .take(3).reduceIndexed { index, acc, value -> if (index == 1) acc * 100 + value else acc * 1000 + value }

android {
    namespace = "com.fan.moneytoolbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fan.moneytoolbox"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // 便捷签名: keystore 随仓库提交,保证每次 CI 构建的 APK 签名一致,可以直接覆盖安装。
    // 如需换成私密签名,见 README「签名配置」一节(通过 GitHub Secrets 注入)。
    signingConfigs {
        create("release") {
            val ks = rootProject.file("keystore/moneybox.keystore")
            if (ks.exists()) {
                storeFile = ks
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "moneybox2026"
                keyAlias = System.getenv("KEY_ALIAS") ?: "moneybox"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "moneybox2026"
            }
        }
    }

    // 只出两个 ABI 包: 32 位 armeabi-v7a 与 64 位 arm64-v8a
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
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
            if (rootProject.file("keystore/moneybox.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    packaging {
        resources.excludes += "META-INF/*.version"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
}

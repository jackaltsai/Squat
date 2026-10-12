plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.heartchen.squat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.heartchen.squat"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Play Console 的「DEX code optimization is below our threshold」
            // （Obfuscation 1%，門檻 25%，Fix by Feb 2027）就是因為這裡原本是 false ——
            // 壓縮/混淆/優化整個沒跑。
            //
            // AGP 9.3+ 的新 DSL：`enable = true` 同時開啟程式碼與資源優化，
            // 並**內建一組等同 proguard-android-optimize.txt 的預設 keep 規則**，
            // 所以不需要再寫 `proguardFiles`。
            // 自訂 keep 規則放在 `src/<variant>/keepRules/*.keep`
            // （見 app/src/release/keepRules/squat.keep —— 那條是必要的，不是保險）。
            optimization {
                enable = true
            }
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
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.pose.detection.accurate)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
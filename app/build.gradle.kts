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
        versionCode = 7
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Play Console 的「DEX code optimization is below our threshold」
            // （Obfuscation 1%，門檻 25%，Fix by Feb 2027）就是因為這裡原本是 false ——
            // 壓縮/混淆/優化整個沒跑。
            //
            // 🔴 2026-10-20：`6 (1.0)` 開了這個之後**實機啟動就 crash**。
            // 根因不在專案程式碼裡，而是 **R8 full mode**（AGP 8 以後預設開啟）
            // 破壞了 ML Kit 的元件註冊機制：
            //
            // ```
            // NullPointerException: 'LazyInstanceMap.get(Object)' on a null object reference
            //     at PoseDetectorImpl.<init>  ← PoseDetection.getClient
            //     ← PoseAnalyzer.<init>(PoseAnalyzer.kt:21)
            // ```
            //
            // ML Kit 沿用 Firebase 的 `ComponentRegistrar`：實作類別只被
            // `AndroidManifest.xml` 的 `<meta-data>` 引用、靠 `Class.forName` 載入，
            // R8 看不到那條引用鏈。**修法在 `gradle.properties`：
            // `android.enableR8.fullMode=false`**（一行）。
            //
            // ⚠️ **不是因為 ML Kit 被改名。** 實測 release 的 mapping 裡
            // ML Kit 的類別一個都沒被改名（它自己附的 consumer keep 規則在保護），
            // 所以壞掉的是 full mode 更激進的**優化**，不是混淆。
            // 也因此**不要**改成 keep 整個 `com.google.mlkit.**` ——
            // 那會讓 DEX 的大宗完全不混淆，很可能還是過不了 Play 的 25% 門檻。
            //
            // 📌 驗證迴圈：在 `debug` 也開 `optimization { enable = true }`，
            // 用 `./gradlew installDebug` 兩分鐘跑一輪，完全不碰 Play
            // （Android Studio 會用本機 mapping 自動還原 Logcat 的 trace）。
            // 把 R8 的驗證綁進 Play 的發布迴圈會變成一天一次 —— 10/16~10/20 就是這樣耗掉的。
            //
            // ✅ 2026-10-20 debug+R8 六動作實機通過：啟動正常、六個動作都計得到、
            //    `exerciseType` 欄完全沒被混淆、膝內夾閘門正確、分級邏輯全符。
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
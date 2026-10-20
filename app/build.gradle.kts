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
        // ⚠️⚠️ 診斷用，**修好 R8 之後要刪掉** ⚠️⚠️
        //
        // 2026-10-20：`6 (1.0)` 開了 R8 之後實機啟動就 crash，而 debug build
        // （不跑 R8）正常。先前是把「驗證 R8」綁進 Play 的發布迴圈 ——
        // 每試一次要「出包→上傳→等審核一天→安裝→測試」，四天只試了一次。
        //
        // 在 debug 也開 R8，就能用 `./gradlew installDebug` 兩分鐘跑完一輪，
        // 完全不碰 Play。debug 用 debug 金鑰自動簽章，也不會誤傳上去。
        //
        // 💡 Android Studio 會用本機的 mapping 檔**自動還原** Logcat 的 stack trace，
        // 所以這樣抓到的 trace 可能直接就是真實類別名，不必手動 retrace。
        //
        // mapping 檔在 app/build/outputs/mapping/debug/mapping.txt
        debug {
            optimization {
                enable = true
            }
        }

        release {
            // Play Console 的「DEX code optimization is below our threshold」
            // （Obfuscation 1%，門檻 25%，Fix by Feb 2027）就是因為這裡原本是 false ——
            // 壓縮/混淆/優化整個沒跑。
            //
            // ⚠️ **2026-10-20 退回 false** —— `enable = true` 的 `6 (1.0)` 在實機
            // **啟動就 crash**，而 debug build（不跑 R8）同一份原始碼完全正常。
            //
            // ```
            // NullPointerException: 'java.lang.Object yp.d(java.lang.Object)'
            //                        on a null object reference
            //     at y41.<init> … at h4.onMeasure → View.measure
            // ```
            // 底部是 onMeasure、中間是 Compose 組合器內部 —— 第一次組合時建構某個
            // 物件呼叫了 null（`吃 Object 回 Object` 的簽章通常是 Function1.invoke）。
            // **確切是哪一個還沒查出來**（需要用 mapping.txt 還原）。
            //
            // 退版的理由是時程不是技術：R8 的截止是 **2027-02**（還有三個多月），
            // 而 Play 上掛著會 crash 的版本就**不能開始招募**，
            // 12 人 × 14 天的時鐘才是論文的關鍵路徑。每一輪 R8 嘗試都要
            // 「出包→上傳→等審核一天→安裝→測試」，10/16~10/20 已經耗掉四天。
            //
            // 📌 重試時的下一步寫在 `RELEASE_CHECKLIST.md`：先用 mapping.txt
            // retrace 這段 trace 拿到真實類別名，不要再憑猜測補 keep 規則。
            //    `app/src/release/keepRules/squat.keep` **刻意留著** ——
            //    那條 enum 規則本身驗證過是對的（mapping 顯示常數全部保留）。
            optimization {
                enable = false
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
# Google Play 上架前準備清單

> 適用專案：深蹲智慧健身輔助系統（`com.heartchen.squat`）
> 目前狀態（2026-09-07）：**「Finish setting up your app」100% 完成**。App 端也修正了骨架疊圖 UX（一律顯示，不用開除錯模式）並重新建置簽署過 `.aab`。Closed testing 的 release 已建立，**卡在 Testers 名單，預計星期四才開始加測試人員**。

## 📍 下次接續從這裡開始（星期四）

Play Console → 這個 App → **Test and release → Testing → Closed testing → Alpha → Testers 分頁**

目前進度：
- [x] Release 已建立：`1.0 (1) - 初版測試`（用含骨架疊圖修正的新版 `.aab`），狀態 "Not yet sent for review"
- [x] Countries/regions 已設定：Taiwan
- [ ] **Testers 分頁**：已選 **Email lists**，但**還沒點 "Create email list" 建立名單**（星期四從這裡繼續）
- [ ] Testers 名單建好後，記得回到 **Publishing overview** 點 **Send changes for review**，把 release + 國家 + 測試人員設定一起送審
- [ ] 送審通過後才會產生「Join on the web」的測試連結，分享給 12 位測試人員

之後還要：
1. **Closed testing** 需要至少 12 人 opted-in、連續跑滿 14 天才能申請 Production
2. **Production** — 條件滿足後申請，届時要回答幾題關於這次封閉測試的問題

詳見下方第 8 節。

---

## 1. 應用程式身分與版本

- [x] `app_name`（首頁圖示下方標籤）已從預設的 `Squat` 改為 `深蹲教練`，與 `docs/STORE_LISTING.md` 的商店標題語系一致
- [x] `applicationId`（`com.heartchen.squat`）— 確認為最終值，**上架後無法更改**，目前值可用
- [x] Icon 已 push 並檢查：
  - [x] Adaptive icon 背景原本仍是 Android Studio 預設綠色格線範本（`ic_launcher_background.xml` 沒被正確覆蓋），已修正為白色純底，跟 legacy icon / Play 商店圖示的白色背景一致
  - [x] Play 商店用的 512×512 icon 已存在：`app/src/main/ic_launcher-playstore.png`（Image Asset Studio 自動產生）
- [x] `versionCode 1` / `versionName "1.0"` 適合作為首次上架版本，之後每次上傳遞增 `versionCode` 即可，暫不需調整

---

## 2. 簽署金鑰（Signing）

- [x] 已產生正式 **Upload Key**（`~/keystores/squat-release.jks`）並成功產生第一個已簽署的 `.aab`（透過 Android Studio Generate Signed App Bundle 精靈）
  - 實測確認：這個精靈**不會**改動 `app/build.gradle.kts`，是獨立於 Gradle 的一次性簽署流程；之後每次出新版本要重跑一次精靈，詳見 `docs/SIGNING_SETUP.md`
- [ ] 金鑰檔案、密碼、別名（alias）**妥善備份**到至少兩個安全位置（提醒過，待你確認已完成）
- [x] `.gitignore` 已排除 `*.jks` / `*.keystore` / `keystore.properties`，避免金鑰誤入版控
- [ ] 啟用 **Play App Signing**（Google 代管正式簽署金鑰，Upload Key 僅用於上傳）
- [ ] 確認 `release` buildType 是否要開啟 R8/ProGuard（目前 `optimization.enable = false`），若開啟需測試 ML Kit / CameraX / Room 相關 class 是否被誤刪，必要時補 `proguard-rules.pro` 規則

---

## 3. 建置產出（Build）

- [ ] 以 `.aab`（Android App Bundle）格式建置，非 `.apk`（Play 要求新 App 必須用 AAB）
- [ ] `minSdk 24` / `targetSdk 37` 確認為最新要求（Play Console 會定期要求 targetSdk 提高至最新版）
- [ ] Release 版本實機測試（非 Debug build），確認：
  - [ ] CameraX 預覽正常
  - [ ] ML Kit Pose Detection 正常運作（release 模式下模型下載/推論不受影響）
  - [ ] Room 資料庫讀寫正常
- [ ] 檢查 App 大小是否合理（ML Kit accurate model 較大，注意 AAB 分拆後各 ABI 的下載大小）

---

## 4. 權限與隱私（本 App 重點：CAMERA 權限）

- [x] `AndroidManifest.xml` 僅宣告必要的 `CAMERA` 權限與相機硬體 feature，無多餘權限
- [x] 已確認：`pose-detection-accurate` 模型為 **bundled model，直接打包在 App 安裝檔內**，不需連網下載、不會將影像傳送到外部伺服器（官方文件：ML Kit pose detection 使用 bundled 依賴，模型隨 App 一起發布）
- [x] App 內權限說明（rationale UI）已存在（`MainActivity.kt` 的 `CameraPermissionRationale`），已補上「影像僅用於即時姿態分析，不會被儲存、錄影或上傳」文字，與隱私權政策一致
- [x] **隱私權政策（Privacy Policy）**：草稿已完成 → `docs/PRIVACY_POLICY.md`（原始內容）/ `docs/privacy-policy.html`（可直接發布的網頁版）
  - [x] 已透過 GitHub Pages 發布，公開 URL：**`https://jackaltsai.github.io/Squat/privacy-policy.html`**（Play Console → App content → Privacy Policy 直接填這個網址）
  - [x] 內容已涵蓋：相機用途、影像資料不離開裝置、Room 本地訓練紀錄的儲存與刪除方式、除錯模式資料僅存本機、聯絡方式

---

## 5. Play Console — Data Safety（資料安全）表單

- [ ] 是否收集資料：勾選「相機」，用途「App functionality」，**資料不會離開裝置**（相機影像僅即時運算、不儲存不外傳）
- [ ] 是否收集個人資料：訓練紀錄（時間戳記、深度達成率、模式等）僅存 Room 本地資料庫，勾選「資料不會離開裝置」
- [x] 第三方 SDK 是否傳輸資料出裝置：已確認 ML Kit pose-detection-accurate 為 bundled model，**不需連網下載模型**，可在表單中如實勾選「App 不需要網路連線即可運作核心功能」
- [ ] 資料加密（傳輸中/靜態）與使用者刪除資料的方式（例如 App 內清除紀錄功能，或解除安裝即清除）

---

## 6. Play Console — 商店資訊（Store Listing）

- [x] App 名稱、簡短說明（80 字內）、完整說明（4000 字內）→ 已填入 Play Console Store listing（草稿見 `docs/STORE_LISTING.md`）
- [x] App icon（512×512）→ 用 `app/src/main/ic_launcher-playstore.png` 上傳
- [x] Feature Graphic（1024×500）→ 已生成並上傳，`docs/feature-graphic.png`（深藍到青綠漸層 + App icon 圓角卡片 + 標語，特意避開 Android 官方綠色範本色）
- [x] 手機截圖 5 張已上傳（達到 4 張推薦資格門檻）：選擇訓練模式、校正流程、深蹲動作中、紅色警示回饋（蹲太淺了）、綠色達標回饋（深度達標！）
- [ ] 若有平板/摺疊裝置支援，準備對應尺寸截圖（非必填，目前跳過）
- [x] App 分類：已設定 Health & Fitness
- [x] 聯絡 Email / 官網：`hata.s520@gmail.com` / `https://jackaltsai.github.io/Squat/`

---

## 7. Play Console — 內容分級與合規

**App content 底下 10 項聲明已於 2026-09-02 全部完成（Play Console → Policy and programs → App content → Actioned 分頁可見）：**

- [x] **Privacy policy**：填了 `https://jackaltsai.github.io/Squat/privacy-policy.html`
- [x] **Sign in details**：選 No（不需要登入）
- [x] **Ads**：選不含廣告
- [x] **Content ratings**：完成 IARC 問卷，Category 選 "All Other App Types"，全區域分級結果都是最低年齡（All ages / Everyone / PEGI 3 / 3+）
- [x] **Target audience and content**：選 18 and over
- [x] **Data safety**：完整走完 5 步問卷 —— 相機/訓練紀錄皆不符合「Collected」定義（未離開裝置）不用揭露；唯獨相機即時影像串流因屬於 "ephemeral processing" 規則，勾選 Photos and videos → Videos → Collected（非 Shared）→ ephemeral → App functionality。公開商店頁面最終顯示 "No data collection declared"、"No data shared with third parties"
- [x] **Government apps**：選 No
- [x] **Financial features**：選「My app doesn't provide any financial features」
- [x] **Health apps**：勾選 "Activity and fitness"（不勾 Medical 分類，符合「非醫療器材」定位）
- [x] **Advertising ID**：選 No（不使用 AAID）

---

## 8. 測試階段（Testing Track）

- [ ] 建立 **Internal Testing**（內部測試）先行驗證上傳流程與安裝
- [ ] **Closed Testing**（必經關卡，2026-09-06 確認條件）：
  - [ ] Publish a closed testing release（把 `.aab` 傳上去）
  - [ ] 至少 12 名測試人員 opted-in（目前 0 人）
  - [ ] 讓這 12 人連續測試至少 14 天
  - 這步最花時間，**建議提前規劃找 12 位測試人員**（朋友/家人/健身社群皆可，只要願意加入 opt-in 連結），14 天等待期可以跟其他準備工作平行進行
- [ ] 收集 Pre-launch report（Play Console 自動跑的相容性/穩定性測試）結果，確認無 Crash
- [ ] Closed testing 跑完後，Apply for production 會問幾個關於這次封閉測試的問題（Dashboard 上有 "Preview questions" 連結可以先看內容）

---

## 9. 開發者帳號與帳務

- [x] Google Play Console 開發者帳號已註冊（個人帳戶，Google 帳號 `zykofans@gmail.com`）
- [x] 一次性註冊費（USD $25）已繳納並扣款成功
- [ ] 若未來規劃付費功能/App，需設定收款帳戶（目前無 IAP 需求可略過）
- [x] **App 項目已建立**：`深蹲教練` / `com.heartchen.squat`，Play Console Dashboard 已可見，接下來走 Internal testing → Finish setting up your app（Store listing）→ Closed testing → Production 路徑

**歷史記錄（2026-08-24）：**
- `hata.s520@gmail.com` 底下原本有一個舊開發人員帳戶（ChungDa Tsai），因久未使用被 Google 永久關閉，且該 Google 帳號無法重新申請（Google 政策：一個帳號只能對應一個開發人員帳戶），註冊費也未退還
- 改用 `zykofans@gmail.com` 走全新註冊流程，中途因缺付款方式暫停過一次，之後確認可付款後完整走完 Account type → Payments profile → Public developer profile → About you → Apps → How Google contacts you → Terms，並成功 **Create account and pay**
- 「Other Google accounts」問題誠實選了 Yes，並驗證聲明過 `hata.s520@gmail.com`（避免被判定隱瞞帳號關聯）

**接下來要注意：**
- [x] Verify access to an Android mobile device — 已用手機掃 QR code、登入 Play Console App 完成驗證
- [x] **身分驗證（Verify your identity）已通過** — 第一次用身分證上傳被拒（只拍到正面，地址在背面沒拍到），重新上傳含背面地址頁後送審，**比預期快很多就通過了**（原本 Google 說可能要幾天）
- [x] Contact phone number 驗證 — 已隨帳戶設定完成解鎖並通過
- [x] **帳戶完全設定完成**：首頁「Finish setting up your developer account」提示卡已消失，**Create app** 按鈕已從灰轉藍可點擊
- [ ] 帳戶開通後盡快建立第一個 App 項目（就算還沒準備好上傳），避免久未使用又被關閉 ← **下一步**

---

## 10. 上架後追蹤（Nice to have）

- [ ] 規劃版本更新節奏，對應 `CLAUDE.md` 的 M1~M4 里程碑（建議每個里程碑穩定後才推正式更新，避免狀態機/校正邏輯 bug 影響已上線使用者）
- [ ] 準備意見回饋管道（App 內回饋表單或 Email），因為深度達成率、膝內夾判定門檻仍需依真實使用者資料調參（對應 `CLAUDE.md` 第 6 節）
- [ ] 考慮加入基本的 Crash 回報（如 Firebase Crashlytics），方便上線後除錯
- [ ] `ui/theme/Color.kt` 目前仍是 Compose 範本預設的紫色調（`Purple80` / `PurpleGrey80` / `Pink80`），與「深蹲教練」品牌無關。建議等你的自訂 icon push 上來後，依 icon 主色重新設計 App 內主題色，這是視覺設計決定，不由我自行更動配色

---

## 目前專案狀態對照

| 項目 | 狀態 |
|---|---|
| App Icon | ✅ 已完成並推送，adaptive icon 背景 bug（誤留綠色格線範本）已修正 |
| 隱私權政策 | ✅ 已發布：`https://jackaltsai.github.io/Squat/privacy-policy.html` |
| Release 簽署設定 | ✅ 已產生正式金鑰並成功輸出第一個已簽署 `.aab`；待你確認金鑰檔案/密碼已備份到安全位置 |
| Data Safety 表單內容 | ✅ 已確認 ML Kit 為 bundled model，不連網、資料不離開裝置 |
| 商店文案 | ✅ 已填入 Play Console（`docs/STORE_LISTING.md`） |
| 商店截圖 | ✅ 5 張已上傳（校正流程、深蹲動作中、紅/綠色回饋、模式選擇） |
| Feature Graphic | ✅ 已生成並上傳（`docs/feature-graphic.png`） |
| Play 開發者帳號 | ✅ 完全設定完成（`zykofans@gmail.com`），身分驗證通過 |
| App Store Listing | ✅ 100% 完成，狀態 "Ready to send for review" |
| Closed Testing | ⏳ 尚未開始，需要 12 名測試人員、連續 14 天 |

### 這台環境做不到、需要你本人操作的項目
- ~~產生正式簽署金鑰~~ ✅ 已完成
- ~~在 GitHub 網頁介面開啟 Pages~~ ✅ 已完成，隱私權政策已上線
- ~~Play Console 各項表單實際勾選送出、開發者帳號註冊與繳費~~ ✅ 已完成
- ~~實機截圖~~ ✅ 已完成
- **找 12 名 Closed Testing 測試人員**（朋友/家人/健身社群），並維持連續 14 天測試 — 這是接下來唯一的大關卡

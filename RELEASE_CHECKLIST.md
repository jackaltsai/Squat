# Google Play 上架前準備清單

> 適用專案：深蹲智慧健身輔助系統（`com.heartchen.squat`）
> 目前狀態：`versionCode 1` / `versionName "1.0"`，App Icon 你已在本機完成但尚未 push，尚未建立正式簽署金鑰。

---

## 1. 應用程式身分與版本

- [x] `app_name`（首頁圖示下方標籤）已從預設的 `Squat` 改為 `深蹲教練`，與 `docs/STORE_LISTING.md` 的商店標題語系一致
- [x] `applicationId`（`com.heartchen.squat`）— 確認為最終值，**上架後無法更改**，目前值可用
- [ ] Icon：你已在本機 Android Studio 完成，**待 push 上來**才能在此確認：
  - [ ] Adaptive icon 前景/背景在圓形、方形、圓角方形皆無裁切跑版
  - [ ] Play 商店用的 **512×512 高解析 icon**（PNG，32-bit）另外準備好，供 Play Console 上傳（icon push 上來後我可以幫你確認/處理）
- [x] `versionCode 1` / `versionName "1.0"` 適合作為首次上架版本，之後每次上傳遞增 `versionCode` 即可，暫不需調整

---

## 2. 簽署金鑰（Signing）

- [ ] 產生正式 **Upload Key** 並設定 `release` buildType 簽署 → 完整操作步驟見 `docs/SIGNING_SETUP.md`
  - 本專案用 AGP `9.3.1` 新版宣告式 DSL，建議用 **Android Studio 的 Generate Signed App Bundle 精靈**產生 keystore 並自動寫入 `build.gradle.kts`，不要手動猜語法改檔案
- [ ] 金鑰檔案、密碼、別名（alias）**妥善備份**（遺失將無法更新已上架的 App）
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
  - [ ] 透過 GitHub Pages 發布 `docs/privacy-policy.html`，取得公開 URL 填入 Play Console
    1. GitHub repo → Settings → Pages
    2. Source 選 "Deploy from a branch"，Branch 選這個分支、資料夾選 `/docs`
    3. 存檔後取得網址（格式類似 `https://jackaltsai.github.io/Squat/privacy-policy.html`），填入 Play Console → App content → Privacy Policy
  - [x] 內容已涵蓋：相機用途、影像資料不離開裝置、Room 本地訓練紀錄的儲存與刪除方式、除錯模式資料僅存本機、聯絡方式

---

## 5. Play Console — Data Safety（資料安全）表單

- [ ] 是否收集資料：勾選「相機」，用途「App functionality」，**資料不會離開裝置**（相機影像僅即時運算、不儲存不外傳）
- [ ] 是否收集個人資料：訓練紀錄（時間戳記、深度達成率、模式等）僅存 Room 本地資料庫，勾選「資料不會離開裝置」
- [x] 第三方 SDK 是否傳輸資料出裝置：已確認 ML Kit pose-detection-accurate 為 bundled model，**不需連網下載模型**，可在表單中如實勾選「App 不需要網路連線即可運作核心功能」
- [ ] 資料加密（傳輸中/靜態）與使用者刪除資料的方式（例如 App 內清除紀錄功能，或解除安裝即清除）

---

## 6. Play Console — 商店資訊（Store Listing）

- [x] App 名稱、簡短說明（80 字內）、完整說明（4000 字內）→ 草稿見 `docs/STORE_LISTING.md`（含分類、內容分級填答方向、廣告聲明草稿，建議上架前找人校對文案）
- [ ] Feature Graphic（1024×500）
- [ ] 手機截圖至少 2 張（建議 4~8 張，涵蓋：相機骨架偵測畫面、狀態機計次畫面、校正流程、三色回饋畫面、訓練歷程頁）
- [ ] 若有平板/摺疊裝置支援，準備對應尺寸截圖
- [ ] App 分類：建議「健康與健身」（Health & Fitness）
- [ ] 聯絡 Email / 官網（可放隱私權政策頁）

---

## 7. Play Console — 內容分級與合規

- [ ] 完成 **內容分級問卷（Content Rating）**（填答方向草稿見 `docs/STORE_LISTING.md`）
- [ ] **目標對象與內容（Target Audience）**：確認是否適合兒童（本 App 屬一般健身工具，通常設定為一般成人/不特定年齡）
- [x] **廣告聲明**：依賴清單無廣告 SDK，應勾選「不含廣告」
- [ ] **App 存取權限（App Access）**：若 App 無需登入即可完整使用，勾選「所有功能皆可不受限存取」
- [ ] **政府/健康相關聲明**：由於涉及運動姿勢回饋，建議在說明中註明「非醫療器材，僅供健身輔助參考，如有身體不適請諮詢專業人士」，避免被歸類為醫療器材相關審查
- [ ] 美國出口法規合規聲明（Export Compliance，Play Console 上傳時會詢問，App 未使用加密技術則直接勾選標準選項）

---

## 8. 測試階段（Testing Track）

- [ ] 建立 **Internal Testing**（內部測試）先行驗證上傳流程與安裝
- [ ] 若為新開發者帳號，Google 要求 **封閉測試（Closed Testing）至少 12 名測試人員、連續 14 天** 才能申請正式上線 Production，需提前規劃時程
- [ ] 收集 Pre-launch report（Play Console 自動跑的相容性/穩定性測試）結果，確認無 Crash

---

## 9. 開發者帳號與帳務

- [ ] Google Play Console 開發者帳號已註冊（個人或組織，注意組織帳號需 D-U-N-S 驗證，耗時較長）
- [ ] 一次性註冊費（USD $25）已繳納
- [ ] 若未來規劃付費功能/App，需設定收款帳戶（目前無 IAP 需求可略過）

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
| App Icon | ⚠️ 你在本機 Android Studio 已完成，但**尚未 commit/push**，此分支的 `res/mipmap-*` 仍是預設綠色機器人範本圖示 |
| 隱私權政策 | ⏳ 草稿已完成（`docs/PRIVACY_POLICY.md`），待用 GitHub Pages 發布公開 URL |
| Release 簽署設定 | ⏳ 操作步驟已寫好（`docs/SIGNING_SETUP.md`），待你在 Android Studio 跑一次精靈產生金鑰 |
| Data Safety 表單內容 | ✅ 已確認 ML Kit 為 bundled model，不連網、資料不離開裝置 |
| 商店文案 | ✅ 草稿已完成（`docs/STORE_LISTING.md`），待校對 |
| 商店截圖 | ❌ 尚未準備（需實機操作各畫面截圖） |
| Play 開發者帳號 | ⏳ 待確認是否已註冊 |

### 這台環境做不到、需要你本人操作的項目
- 產生正式簽署金鑰（需要 Android Studio + 本機/實機環境，這裡沒有 Android SDK 可驗證建置）
- 在 GitHub 網頁介面開啟 Pages（repo 設定變更，沒有對應 API 工具可自動做）
- Play Console 各項表單實際勾選送出、開發者帳號註冊與繳費
- 實機截圖（需要真的跑起 App 操作各畫面）

# Google Play 上架前準備清單

> 適用專案：深蹲智慧健身輔助系統（`com.heartchen.squat`）
> 目前狀態：`versionCode 1` / `versionName "1.0"`，App Icon 已完成，尚未建立正式簽署金鑰。

---

## 1. 應用程式身分與版本

- [ ] 確認 App 名稱（`app_name`）為正式上架名稱，且與 Play 商店標題一致
- [ ] 確認 `applicationId`（`com.heartchen.squat`）為最終值 — **上架後無法更改**
- [ ] Icon 已完成（見前次對話），需同時確認：
  - [ ] Adaptive icon 前景/背景在圓形、方形、圓角方形皆無裁切跑版
  - [ ] Play 商店用的 **512×512 高解析 icon**（PNG，32-bit，含 alpha）另外準備好，供 Play Console 上傳
- [ ] `versionCode` / `versionName` 規劃版號策略（例如每次上傳遞增 versionCode）

---

## 2. 簽署金鑰（Signing）

- [ ] 產生正式 **Upload Key**（`keytool -genkey -v -keystore ... -keyalg RSA -keysize 2048 -validity 10000`）
- [ ] 金鑰檔案、密碼、別名（alias）**妥善備份**（遺失將無法更新已上架的 App）
- [ ] 在 `app/build.gradle.kts` 設定 `signingConfigs` + `release` buildType 指向該金鑰
  - 目前 `release` block 只設定了 `optimization { enable = false }`，尚未接上簽署設定
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

- [ ] `AndroidManifest.xml` 中 `CAMERA` 權限用途明確（僅用於即時姿態偵測，**不錄影、不上傳影像**）
- [ ] 確認 ML Kit Pose Detection 是否為 **純本地端（on-device）推論**，不會將影像傳送到外部伺服器 → 這點直接影響 Data Safety 表單填寫
- [ ] App 內是否有「首次使用相機前」的權限說明（rationale UI），建議加上簡短說明「此 App 使用相機進行姿勢偵測，影像不會被儲存或上傳」
- [ ] **隱私權政策（Privacy Policy）**：
  - [ ] 撰寫隱私權政策網頁（需公開可存取的 URL，Play Console 必填）
  - [ ] 內容需涵蓋：使用相機的目的、影像資料是否離開裝置、Room 本地訓練紀錄的儲存與刪除方式、聯絡方式
  - [ ] 若之後加入「除錯模式匯出 CSV/JSON」功能，需在政策中說明該資料僅存於裝置本地

---

## 5. Play Console — Data Safety（資料安全）表單

- [ ] 是否收集資料：勾選「相機」（即時姿態分析，若不儲存影像則說明用途為「App functionality」且不外傳）
- [ ] 是否收集個人資料：訓練紀錄（時間戳記、深度達成率、模式等）若僅存 Room 本地資料庫，需標示「資料不會離開裝置」
- [ ] 是否有第三方 SDK 傳輸資料出裝置：確認 ML Kit 是否需要首次下載模型（會連網），若有需在表單中誠實揭露網路存取行為
- [ ] 資料加密（傳輸中/靜態）與使用者刪除資料的方式（例如 App 內清除紀錄功能，或解除安裝即清除）

---

## 6. Play Console — 商店資訊（Store Listing）

- [ ] App 名稱、簡短說明（80 字內）、完整說明（4000 字內）
- [ ] Feature Graphic（1024×500）
- [ ] 手機截圖至少 2 張（建議 4~8 張，涵蓋：相機骨架偵測畫面、狀態機計次畫面、校正流程、三色回饋畫面、訓練歷程頁）
- [ ] 若有平板/摺疊裝置支援，準備對應尺寸截圖
- [ ] App 分類：建議「健康與健身」（Health & Fitness）
- [ ] 聯絡 Email / 官網（可放隱私權政策頁）

---

## 7. Play Console — 內容分級與合規

- [ ] 完成 **內容分級問卷（Content Rating）**
- [ ] **目標對象與內容（Target Audience）**：確認是否適合兒童（本 App 屬一般健身工具，通常設定為一般成人/不特定年齡）
- [ ] **廣告聲明**：確認 App 是否含廣告（目前依賴清單無廣告 SDK，應勾選「不含廣告」）
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

---

## 目前專案狀態對照

| 項目 | 狀態 |
|---|---|
| App Icon | ✅ 已完成 |
| 隱私權政策 | ❌ 尚未撰寫 |
| Release 簽署設定 | ❌ 尚未設定 |
| Data Safety 表單內容 | ⏳ 待確認 ML Kit 是否連網下載模型 |
| 商店截圖/文案 | ❌ 尚未準備 |
| Play 開發者帳號 | ⏳ 待確認是否已註冊 |

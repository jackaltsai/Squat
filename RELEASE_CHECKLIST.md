# Google Play 上架前準備清單

> 適用專案：深蹲智慧健身輔助系統（`com.heartchen.squat`）
> 目前狀態（2026-09-10）：**14 項變更已全部送審，狀態 Changes in review。等 Google 審核結果。**
> App 端三輪調整（僅前鏡頭／準備倒數＋停止鍵＋CSV 分享／CSV 原始值欄位＋校正品質檢查）皆已實機測試通過，合併為 `versionCode 3` 上傳。
> 招募文已備妥（`docs/THREADS_RECRUITMENT.md`），**只差審核通過後的 opt-in 連結**。

## 📍 下次接續從這裡開始：**等審核通過後**

### ~~步驟 0：實機測試~~ ✅ 已於 2026-09-10 完成

`f80d0d9` 這批改動這台環境無法編譯驗證（沒有 Android SDK），已由本人實機跑過 debug build 確認：

- [x] 相機直接開前鏡頭，畫面沒有左右顛倒（骨架線貼合身體）
- [x] 校正 2 下後出現 **「準備 → 3 → 2 → 1 → 開始！」**，字夠大、2 公尺外看得到，語音正常
- [x] 倒數期間站起來不會被算成第一下
- [x] 訓練中右上角紅色 **「■ 停止」**，不會跟框位警告或其他提示重疊
- [x] 按停止 → 出現結束摘要（次數／達標比例／膝內夾比例）
- [x] 點 **「分享研究資料（CSV）」** → 系統分享選單正常開啟，**FileProvider 沒有閃退**
- [x] CSV 內容可正常開啟
- [x] 「重新開始」乾淨回到選模式

### ~~步驟 0b：補測 Room / 匯出那批~~ ✅ 已於 2026-09-10 完成

- [x] **Room migration**：覆蓋安裝後匯出全部歷史，共 **187 筆**、最早回溯到 2026-08-21。舊紀錄的三個新欄位是空的、新紀錄有值 —— `ALTER TABLE ADD COLUMN` 正確執行，**沒有資料遺失**
- [x] **匯出全部歷史紀錄**：16 個場次全部倒出，分析腳本正確切分
- [x] **CSV 新欄位**：`dNow` / `duser` / `kneeValgusRatio` 都有值
- [x] **校正一致性檢查有作用**：相隔 90 秒的兩場，Duser = 0.1919 vs 0.1806，**只差 6.3%**（改版前是 0.98 vs 1.25 的天差地遠）
- [x] **分級邏輯**：187 筆全部重算相符

### ~~步驟 1：build 並上傳~~ ✅ 已完成

- [x] Generate Signed App Bundle → release，`3 (1.0)`，24.1 MB
- [x] 上傳取代 Closed testing 的 `2 (1.0)`
- [x] 4 個 device support 警告已確認為**預期內**：改用前鏡頭後排除沒有前鏡頭的裝置（Phone −177／Tablet −126／Car 歸零），同時因為後鏡頭改為非必要而新增裝置（Chromebook 10 → 69）。淨損約 1%，換掉的是「裝了也不能用」的裝置

### ~~步驟 2：測試人員設定~~ ✅ 已完成並送審

- [x] Countries/regions：Taiwan
- [x] **改用 Google Groups，不用 Email lists**（理由見下方「為什麼是 Google Group」）
  - 群組：`squat-coach-testers@googlegroups.com`
  - 權限：**所有人皆可加入**；查看成員／張貼內容／查看會話一律限**版主**
  - Feedback 信箱：`hata.s520@gmail.com`
- [x] **已送審 14 項變更**，狀態 `Changes in review`（Managed publishing off，通過後自動生效）

---

### ⏳ 步驟 3：等審核通過 ← **現在卡在這裡**

Google 會寄信通知，首次送封閉測試通常幾小時到幾天。審核期間 Play Console 不用動。

通過後依序做：

- [ ] Closed testing → Alpha → Testers → **Copy link** 取得 opt-in 連結
      （格式通常是 `https://play.google.com/apps/testing/com.heartchen.squat`，**以 Play Console 顯示的為準**）
- [ ] 把連結填進 `docs/THREADS_RECRUITMENT.md` 的 `<OPT_IN_LINK>` 佔位符
- [ ] **用無痕視窗**把 opt-in 連結與群組連結各點一次，確認陌生人真的打得開（不是只有你登入時才行）
- [ ] 自己用**非開發者帳號**（`hata.s520@gmail.com`）走完整動線：加入群組 → opt-in → 點「成為測試人員」→ 從 Play 商店安裝
- [ ] 生 QR code 與招募圖（App 截圖 + 三步驟 + QR）
- [ ] 在 Threads 發文招募 12 位

### 步驟 4：盯住 opt-in 人數

- [ ] **≥12 人完成 opt-in**（加入群組 ≠ opt-in，見下方說明）
- [ ] 從人數達標起算，連續 **14 天**
- [ ] 期間留意 Pre-launch report 有無 crash
- [ ] 條件滿足後 Apply for production

### 為什麼是 Google Group 而不是 Email list

| | Email list | Google Group |
|---|---|---|
| 公開招募 | ❌ 只有名單上的 email 能 opt in，得先私訊蒐集陌生人 Gmail | ✅ 貼連結就好，不碰個資 |
| 中途加人 | ❌ **上傳新 CSV 會覆蓋舊名單、切斷現有 opt-in、14 天倒數歸零** | ✅ 只管理群組成員，Play Console 不用動，計時不中斷 |

### Threads 招募注意事項（會導致帳號停權的紅線）

- ❌ 假帳號 / 模擬器湊人數 —— Play Services 會做硬體檢查（x86 偽裝 ARM、缺加速度計），抓到是**開發者帳號記點**
- ❌ 付費買測試者、tester exchange 互測交換
- ⚠️ 請人「為了測試新辦 Gmail」—— Google 依帳號歷史給 trust weight，burner 帳號可能不採計
- ✅ 發文務必寫明：**請用你平常在用的 Google 帳號**

### 🔑 最常見的卡關：加入群組 ≠ 已 opt in

加進群組只是取得資格。每個人**必須自己**打開 opt-in 連結 → 登入 → 點「成為測試人員」，沒點就不算數，Play Console 會一直顯示「Have at least 12 testers opted-in」。招募文一定要把這步寫清楚。

### ⚠️ 兩批人不要混淆

| | Threads 脆友 | 論文受試者 |
|---|---|---|
| 目的 | 湊滿 Play 的 12人14天 | 蒐集研究資料 |
| 人數 | ≥12（建議不把自己算進去） | 約 30 |
| 需要 | 裝了、開來用過 | **知情同意書**、統一拍攝距離/高度/光照/背景 |

脆友的資料**不可當論文數據** —— 沒有同意書、拍攝條件不受控。

### 之後
1. **Closed testing** 需要至少 12 人 opted-in、連續跑滿 14 天才能申請 Production
2. **Production** — 條件滿足後申請，屆時要回答幾題關於這次封閉測試的問題

### 版本對照

| versionCode | 內容 | 狀態 |
|---|---|---|
| 1 (1.0) | 首版 | 已被取代 |
| 2 (1.0) | 骨架疊圖一律顯示、警告訊息移到下方 | 已上傳 Closed testing，存檔中 |
| **3 (1.0)** | **+ 僅前鏡頭、準備倒數、停止鍵、CSV 分享、CSV 原始值欄位、校正品質檢查、匯出全部歷史** | ✅ 已上傳並送審，**審核中** |

**這輪修的 App bug（2026-09-09）：**
- 骨架線條/關鍵點疊圖從「除錯模式才顯示」改成一律顯示，方便使用者確認有沒有被偵測到
- 「偵測不到人/腳踝」等警告訊息原本跟頂部右上角控制項（切換鏡頭、除錯模式）重疊，後來又發現置中會跟站姿校正倒數、深度回饋等疊圖衝突，最後改放在畫面**下方**才不會跟任何東西相撞
- 「切換鏡頭」從常駐顯示改成只在**選擇訓練模式**畫面出現（訓練開始後不會有人中途切鏡頭）

**這輪修的 App 調整（2026-09-10，commit `f80d0d9`）：**
- 校正完 2 下後新增 **「準備 → 3 → 2 → 1 → 開始！」倒數**（畫面置中大字 180sp + 語音 + 嗶聲），倒數期間不計次
- 訓練中新增 **停止按鈕**（右上控制列，紅底），按下後結束並顯示本次摘要
- 摘要頁可 **「分享研究資料（CSV）」**：走 Android 系統分享選單，由使用者自己選收件者
  - ⚠️ **App 絕不自動上傳/寄出** — 這是為了不牴觸已審過的 Data safety 宣告與隱私權政策，詳見下方第 10 節
- 新增 `FileProvider`（API 24+ 不能直接傳 `file://`），路徑只開放 `exports/` 與 `debug_logs/`
- 修正 Manifest 相機 feature：`android.hardware.camera` 在 API 21+ 專指**後**鏡頭，既然改成只用前鏡頭，改為 require `camera.any` + `camera.front`，避免只有前鏡頭的裝置被 Play 商店排除
- 隱私權政策（`.md` 與已發布的 `.html`）新增 **3.1 由您主動發起的資料分享**，生效日期更新為 2026-09-10

**上一輪的 App 調整（2026-09-10，commit `35e091b`）：**
- **改為固定使用前鏡頭，「切換鏡頭」開關整個移除**。理由：深度回饋顏色、校正倒數、框取警告全都只出現在螢幕上，用後鏡頭等於背對畫面、什麼提示都看不到，這個選項對使用者沒有實際價值
- `isFrontCamera` 的鏡像處理（`PoseAnalyzer` → `PoseFrame` → `PoseOverlay`）保留不動
- `versionCode` 2 → 3（2 已被 Play Console 佔用）

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

---

## 10. 「按停止後把 CSV 傳給開發者」的 Play 政策評估（2026-09-10）

### 結論：**用系統分享選單可以，App 自動寄出不行。**

| 做法 | 是否合規 | 說明 |
|---|---|---|
| ❌ App 自動把 CSV 上傳伺服器 / 自動寄 email 給開發者 | **違規** | 這算 Play 定義的 data collection & sharing，必須事先揭露並取得使用者同意。而且會直接牴觸**已經審過**的 Data safety 宣告（宣告「不收集、不分享任何資料」）與已發布的隱私權政策（宣告全程離線）。改了要重送審。 |
| ✅ 使用者自己按「分享」→ 系統分享選單 → 自選收件者 | **合規** | Play 官方明文把「由使用者發起、且使用者知道資料會被傳出」的傳輸排除在 collection/sharing 之外（user-initiated transfer）。Data safety 宣告不用改，隱私權政策也不用改結論。 |

實作採用 ✅ 這條：`SessionExporter.share()` 開 `Intent.ACTION_SEND` chooser，App 不決定收件者。

### 附帶已完成
- 隱私權政策新增 3.1 節明確說明此功能（`.md` + 已發布的 `.html`）
- 摘要頁按鈕下方直接寫出「會開啟系統分享選單，由你自己選擇要傳給誰。App 不會自動上傳或寄出任何資料。」

### ⚠️ 研究倫理提醒（非 Play 規定，但論文需要）
CSV 是受試者的運動資料。依 `CLAUDE.md` 4.3 節的倫理規劃，蒐集前仍需：
- 取得受試者的知情同意（書面同意書），說明會蒐集什麼、用途、如何匿名化
- 檔名/內容目前只有時間戳記，沒有姓名，但**時間 + 你手上的名單就能還原身分**，分析前建議先做編號對照表並分開保管
- 若學校有 IRB/研究倫理審查要求，需先送審通過再蒐集

Play 不會管這一塊，但論文口試會問。

---

## 11. 已討論但決定暫緩的項目

### 台語語音（2026-09-10 評估，決定延到 1.1）

**結論：不能只改一行 `Locale`，Android 內建 TTS 沒有台語。**

目前 `PoseDetectionScreen.kt` 是 `tts.language = Locale.TAIWAN`（`zh-TW`，華語）。
`TextToSpeech` 只能念「已安裝引擎支援的語言」，而 Google TTS 引擎的語言清單裡**沒有**閩南語（`nan`）。
寫成 `Locale("nan", "TW")` 會回傳 `LANG_NOT_SUPPORTED` 然後 fallback 回華語，等於無效。

| 方案 | 可行性 | 代價 |
|---|---|---|
| ① 靠第三方台語 TTS 引擎 | 差 | 要使用者另外裝 App + 進系統設定切換預設引擎；且多數台語 App 是「自己朗讀」而非註冊成系統 TTS engine，本 App 叫不到 |
| ② 雲端台語 TTS API（意傳、聯經數位等） | 可行但成本高 | **會推翻已審過的宣告**：要加 `INTERNET` 權限、改隱私權政策、重填 Data safety 送審；還有即時性延遲與 API 費用 |
| ③ **預錄台語音檔打包進 App** | ✅ 最務實 | 要準備音檔 |

**建議走 ③**，理由是要念的句子數量很少且固定：
- `深度達標！` / `再蹲深一點` / `蹲太淺了`
- `膝蓋往外一點`
- `請站直不動，準備校正站姿基準` / `請完成兩次深蹲，校正基準深度`
- `準備` / `3` / `2` / `1` / `開始` / `訓練結束`
- 框位警告訊息
- ⚠️ **計次數字**是唯一麻煩的（目前是 `speak(sm.repCount.toString())`），要錄 1～30 或自己寫台語數字組合規則

作法：音檔放 `res/raw/`，用 `SoundPool` 播放取代 `tts.speak()`。離線、零延遲、發音品質可控、**不用動隱私權政策與 Data safety**。

**另一個非技術問題**：台語不是「華語漢字用台語音念」。`深度達標` 硬念會變文讀音，聽起來很生硬。真要做，句子本身要重寫成台語慣用說法，**需要懂台語的人潤稿**。

**下一步（若要開始）**：可以先做不需要音檔的部分 —— 把散在各處的 `tts.speak()` 抽成一個 `VoicePrompt` 抽象層 + 「語音：華語／台語」設定開關，音檔備好再接上去。這個改動不影響現有行為，也不碰任何已審過的宣告。

### 其他技術債（不影響上架）
- `ui/theme/Color.kt` 仍是 Compose 專案範本的紫色配色，沒有配合 App 主題調整
- `release` buildType 的 R8/ProGuard 仍關閉（`optimization.enable = false`）；若要開啟需測試 ML Kit / CameraX / Room 是否被誤刪 class
- 商店文案未經母語人士潤稿

---

## 12. 實測資料的兩個觀察（2026-09-10）

> 來源：2026-09-10 晚間匯出的 187 筆歷史紀錄 + 20 筆帶原始值的新紀錄。
> **兩項都不會 crash、不影響上架。**
> ⚠️ 問題 A 的原始結論**已於同日撤回** —— 保留全文是為了記錄推論錯在哪裡。

### ~~🔴 問題 A：膝內夾判定測到的是雜訊~~ ❌ 此結論已於同日撤回

**原始判斷（錯誤）**：同一人相隔 3～4 秒的連續 10 下，`kneeValgusRatio` 在 −1.228 ~ +0.462 之間跳動（全距 1.69），門檻只有 0.15，因此推論「雜訊是訊號的 11 倍，判定等於擲骰子」。

**為什麼錯**：該推論建立在「人的姿勢不可能在連續兩下之間變這麼多」這個前提。經受測者確認，**當時是刻意在變換姿勢**（部分下故意內夾、部分下故意把膝蓋外推）。前提不成立，結論不成立。

**改用「刻意變換姿勢」重讀同一批資料**：

| 分組 | 比值範圍 |
|---|---|
| 判定為內夾（+） | +0.32 ~ +0.46 |
| 明顯膝外推（−） | −1.03 ~ −1.23 |
| 中間地帶 | −0.23 ~ +0.15 |

這是**乾淨的分離**，不是雜訊。另一場（20:07）10 下全為 false（−0.54 ~ +0.04），與正常深蹲一致。

**教訓**：沒有 ground truth 標註，無法從數值本身分辨「感測雜訊」與「受測者刻意製造的變異」。這正是論文第 6 節必須做人工標註的理由。

**真正值得追的線索**：`rep 5 = +0.149`，門檻 `0.15`，**差 0.001**。若該下確實是刻意內夾，就是一次被門檻擦邊擦掉的漏判 —— 指向**門檻值偏高**，而非公式錯誤。

**下一步（校準門檻，不是改公式）**：
1. 重錄一組，**逐下記錄「正常」或「故意內夾」**作為 ground truth
2. 用 CSV 的 `kneeValgusRatio` 欄位掃描候選門檻（0.10 / 0.12 / 0.15 …），算 Accuracy / F1
3. 樣本量足夠後再談是否要改公式

**保留但未證實的程式碼觀察**（`KneeValgus.kt`，目前**未修改**）：守門條件 `hipWidth / legLength < 0.2` 的 `legLength` 取自當幀，蹲低時 `legLength` 變小會讓守門變鬆。這是讀碼推得的疑慮，**目前沒有資料證實它造成實際問題**，在有驗證集之前不應更動。

### 🟠 問題 B：校正檢查擋得住「不一致」，擋不住「一致地淺」

| 場次 | Duser | p 平均 |
|---|---|---|
| 20:07 | 0.1919 | **1.040** ✅ |
| 20:09 | 0.1806 | **1.476** ❌ |

20:09 那場兩下校正深蹲很一致（通過 15% 檢查），但**兩下都偏淺**：校正 dNow≈0.18，訓練平均 dNow≈0.267，深了 48%，10 下全綠。

一致性 ≠ 代表性。使用者校正時在試探，因為語音只說「請完成兩次深蹲，校正基準深度」，沒告訴他要蹲多深。

**v4 修法**：文案改成明確指示深度，例如「**請用你平常訓練的深度**，完成兩次深蹲」。
（`duser` 現已寫入 CSV，事後仍可用 `tools/analyze_sessions.py` 把平均 p 偏離 1 的場次標出來複核。）

---

## 13. 審核期間可以做／刻意不做的事（2026-09-10）

### 已備妥、等連結就能用
- `docs/THREADS_RECRUITMENT.md` —— 主文、步驟留言、補充留言、人數卡住時的追蹤留言，以及發文前檢查與「不要寫的內容」（不得提供報酬／不得說裝了就好／不得請人新辦帳號）
- 待補：`<OPT_IN_LINK>` 佔位符

### 刻意**不**做：v4 膝內夾修正
第 12 節的問題 A 結論已撤回。在拿到**逐下標註的 ground truth** 之前不動 `KneeValgus.kt` —— 沒有驗證集就改公式，等於用猜的換掉一個目前沒有證據說它壞掉的東西。

要推進這件事，需要的是**資料**不是程式碼：
1. 錄一組 10～20 下，**逐下記錄「正常」或「故意內夾」**
2. 匯出 CSV，用 `kneeValgusRatio` 欄位掃描候選門檻，算 Accuracy / F1
3. 屆時再判斷該調門檻還是改公式

### 其他技術債（不影響上架）
- `ui/theme/Color.kt` 仍是 Compose 範本紫色配色
- `release` buildType 的 R8/ProGuard 仍關閉（`optimization.enable = false`）
- 商店文案未經母語人士潤稿
- 金鑰備份到第二個安全位置 —— **尚未確認完成**（第 2 節）

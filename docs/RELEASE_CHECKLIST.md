# 上架準備清單

> 建立日期：2026-08-21
> 目前狀態：M1–M4 已完成並實機驗證（見 `app/CLAUDE.md`）。
> 這份清單分兩條路線：**A. 論文受試者測試用（快很多，優先做）**、**B. 正式上架 Google Play（比較久，之後再做）**。

---

## 目前專案基本資料

| 項目 | 目前值 |
|---|---|
| applicationId | `com.heartchen.squat` |
| minSdk | 24 |
| targetSdk | 37 |
| compileSdk | 見 `app/build.gradle.kts` |
| versionCode / versionName | 1 / "1.0" |
| 簽章設定 | 尚未設定 release keystore（目前只有 debug 簽章） |

---

## A. 論文受試者測試用（M5 資料蒐集階段，建議優先）

論文 M5 階段（app/CLAUDE.md 第 6 節）需要約 30 名志工用手機測試，**不一定需要上架 Google Play**，直接發簽好名的 APK 安裝檔給受試者側載安裝即可，比走 Play 審核快很多。

### 任務清單
- [ ] 產生正式的 release keystore（`keytool -genkeypair ...`），**妥善保管**，之後 Google Play 上架也要用同一把
- [ ] 在 `app/build.gradle.kts` 設定 `signingConfigs` + `buildTypes.release`，串接 keystore
- [ ] 確認 release build 開啟 R8/ProGuard 後功能仍正常（尤其 ML Kit Pose Detection、Room 反射相關的 keep rules 需要測試，不能只測 debug build）
- [ ] 確認除錯模式（六點疊圖 / CSV 研究模式）預設關閉，且開關對受試者夠不容易誤觸
- [ ] 確認研究用 CSV 是否要保留（若要蒐集標註資料，這階段可能反而要故意開啟）
- [ ] 建立受試者同意書、資料匿名化與刪除機制說明（對應 CLAUDE.md 4.3 節倫理規劃）
- [ ] 用 `./gradlew assembleRelease` 或 `bundleRelease` 產出簽好名的安裝檔
- [ ] 實際在多台不同廠牌/畫面比例手機上測試（不只自己的測試機），確認鏡頭座標、UI 排版都正常
- [ ] 準備安裝說明（如何側載 APK、需要開啟「允許安裝未知來源應用程式」）

### 驗收標準
簽好名的 APK 能在至少 2-3 台不同廠牌手機上正常安裝、跑完一組完整流程（校正 → 訓練 → 查看歷程）不崩潰。

---

## B. 正式上架 Google Play（之後有需要再做）

如果之後想讓一般大眾也能下載（不只論文受試者），才需要走這條路。比 A 麻煩很多，建議 M5 資料蒐集、論文口試都告一段落後再排時間做。

### B1. 帳號與法務
- [ ] 註冊 Google Play 開發人員帳號（一次性 US$25）
- [ ] 撰寫隱私權政策（Privacy Policy）並掛在一個公開網址上 — Play 上架必填，即使沒存人臉影像，也要說明會用到相機、姿態關鍵點資料、TTS
- [ ] 若做健康/運動相關功能宣稱，確認符合 Google Play 健身類 App 政策（例如加註「僅供參考，非醫療器材」的免責聲明）
- [ ] 決定 App 名稱是否有商標/命名衝突

### B2. App 本身要補的東西
- [ ] 正式 App icon（多種解析度）與 Feature Graphic（Play 商店用的宣傳圖）
- [ ] App 截圖（至少手機版；不同螢幕尺寸建議都準備）
- [ ] 完整/簡短說明文字（繁體中文為主，可考慮加英文）
- [ ] 確認 targetSdk 符合 Google Play 目前規定的最低要求（隨時間會提高，上架前要重新確認）
- [ ] release keystore + `signingConfigs`（同 A 段，若 A 已做可直接沿用）
- [ ] 改用 Android App Bundle（`.aab`）而非 APK 上傳
- [ ] 加入 Crash 回報工具（例如 Firebase Crashlytics），方便上架後追蹤問題（非必填但強烈建議）
- [ ] 針對低階/不同廠牌裝置多做相容性測試

### B3. Play Console 上架流程
- [ ] 建立 App 於 Play Console，填寫「商店資訊」（Store Listing）
- [ ] 完成「內容分級問卷」（Content Rating Questionnaire）
- [ ] 完成「資料安全性」表單（Data Safety）— 需誠實申報有使用相機、蒐集姿態座標等資料
- [ ] 完成「App 內容」（廣告 / 應用程式存取權限 / 目標對象與內容 / 新聞應用程式等一系列申報）
- [ ] 因為要求 `CAMERA` 權限，確認有依 Play 政策說明用途（敏感權限聲明表單，若被要求）
- [ ] 先發「內部測試」（Internal Testing）或「封閉式測試」（Closed Testing）— 新開發人員帳號通常需要至少 12 名測試人員、持續 14 天的封閉測試才能申請正式版發佈資格，要提早排時間
- [ ] 通過測試後才提交「正式版」（Production）給 Google 審核
- [ ] 審核通過、正式上架

### 驗收標準
App 在 Google Play 上可被搜尋到、下載、安裝、正常使用，Data Safety 與隱私權政策內容與實際行為一致。

---

## 建議順序

1. 先做 **A 段**（keystore + release build + 多機實測），讓論文 M5 資料蒐集能盡快開始，這是目前最急的事。
2. A 段跑順之後，再視情況決定要不要做 **B 段**（Google Play 正式上架），這通常是論文口試/研究完成後才需要考慮的事。

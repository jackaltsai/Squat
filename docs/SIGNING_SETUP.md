# Release 簽署設定操作步驟

> 本專案使用 AGP `9.3.1`（新版宣告式 Gradle DSL，`android { compileSdk { ... } }` / `buildTypes { release { optimization { ... } } }`）。
> 這個 DSL 版本較新，直接手動編輯 `app/build.gradle.kts` 加簽署設定容易寫錯語法且此環境無 Android SDK 可驗證建置，
> 因此改用 **Android Studio 內建精靈**產生，由 IDE 自動寫入正確語法，風險最低。

## 步驟

1. 用 Android Studio 開啟本專案
2. 上方選單：**Build → Generate Signed App Bundle / APK**
3. 選擇 **Android App Bundle**（Play 商店要求格式），按 Next
4. 在 Key store path 區塊按 **Create new...**：
   - **Key store path**：選一個專案外部的安全路徑保存（例如 `~/keystores/squat-release.jks`，**不要**放進這個 git repo）
   - **Password**：設定 keystore 密碼（務必記錄在密碼管理工具中）
   - **Alias**：例如 `squat-release-key`
   - **Alias 密碼**：可與 keystore 密碼相同或不同，一併記錄
   - **Validity (years)**：預設 25 年，建議維持或拉長（Google 建議至少到 2033 年後）
   - 填寫憑證基本資料（姓名/組織/城市/國家代碼，不影響功能，可用真實資訊）
   - 按 OK 產生 `.jks` 檔案
5. 回到精靈，確認 Key store path / password / alias 都已帶入，按 **Next**
6. Destination Folder 選輸出位置，Build Variants 勾選 **release**，Signature Versions 勾選 **V1 + V2**（預設即可）
7. 按 **Finish**（或 **Create**），Android Studio 會直接產生已簽署的 `.aab` 檔案（例如 `app/release/app-release.aab`）

**實測更正**：Generate Signed App Bundle 精靈**不會**修改 `app/build.gradle.kts`，它是獨立於 Gradle 建置腳本的一次性簽署流程——金鑰只留在你本機指定的 `.jks` 路徑，`build.gradle.kts` 完全不受影響。這其實是好事：金鑰資訊不會意外被寫進任何會進版控的檔案裡。代價是每次要出新版本，都要重新跑一次這個精靈（Key store path 選同一個 `.jks` 檔案即可，Android Studio 通常會記住最近用過的路徑）。

## 之後的重要備份動作

- [x] 產生的 `.jks` 檔案要備份到至少兩個安全位置（例如密碼管理工具的安全附件 + 加密雲端硬碟），**遺失此檔案將無法再更新已上架的 App**
- [x] 記錄 keystore 密碼、alias、alias 密碼（建議存入密碼管理工具，不要存在純文字檔或 git）
- [x] 確認 `.gitignore` 已排除 `*.jks` / `*.keystore` / `keystore.properties`（本 repo 已加入，見根目錄 `.gitignore`）；`app/release/` 建置產出資料夾已加入 `app/.gitignore`
- [ ] 上傳第一個 `.aab` 到 Play Console 後，**啟用 Play App Signing**（Google 代管正式簽署金鑰，你手上的 `.jks` 僅作為之後上傳版本用的 Upload Key）

## 若要改用 CI / 命令列建置（非必要，之後有需要再做）

如果之後想用 `./gradlew bundleRelease` 命令列或 CI 自動建置簽署版本（而不是每次手動跑精靈），才需要真的把 `signingConfigs` 寫進 `app/build.gradle.kts`，讀取 `keystore.properties`（不進 git）裡的密碼。這塊等有實際需求、且能在有 Android SDK 的環境驗證語法時再做，避免無法測試就手動改建置腳本。

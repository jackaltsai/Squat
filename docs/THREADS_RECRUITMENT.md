# Threads 測試員招募文

## 素材
- `docs/threads-recruit.png`（1080×1350，Threads 直式）— 由 `tools/make_recruit_image.py` 產生
- `docs/qr-opt-in.png`（單獨的 QR code）

## ⚠️ 待決：需不需要 Join on the web 這一步？

Play Console 給了兩個連結：

| 連結 | 網址 |
|---|---|
| Join on Android | `https://play.google.com/store/apps/details?id=com.heartchen.squat` |
| Join on the web | `https://play.google.com/apps/testing/com.heartchen.squat`（**待與 Play Console 的 Copy link 核對**）|

Play Console 對前者的描述是「Testers can join your test using Google Play on Android」，字面上暗示 Android 的 Play 商店 App 自己就能完成加入，不一定要先走 web opt-in。**尚未驗證。**

**決定性測試**（用一個還沒 opt-in 的帳號，順序不可顛倒）：
1. 加入群組
2. **不要**開 Join on the web 連結（開了按下去該帳號就 opt-in，測試失效）
3. 直接在 Android 的 Play 商店 App 開 Join on Android 連結

- App 出現可安裝 → web 連結不需要，招募文縮成 **2 步驟**，QR 改編商店連結
- 「找不到你要的項目」→ web 連結必要，維持下方 **3 步驟**版本


> ⚠️ 使用前先把兩處佔位符換成真實網址：
> - `<OPT_IN_LINK>` → Play Console → Closed testing → Alpha → Testers → **Copy link**（審核通過後才會亮）
>   格式通常是 `https://play.google.com/apps/testing/com.heartchen.squat`，**但以 Play Console 顯示的為準**
> - `<GROUP_LINK>` → `https://groups.google.com/g/squat-coach-testers`（先自己用無痕視窗開一次，確認陌生人點得進去）

---

## 主文（Threads 上限 500 字，這則約 280 字）

```
【徵 12 位 Android 測試員 🙏】

我做了一個用手機前鏡頭偵測深蹲的 App。

把手機立在前面兩公尺，它會自動幫你計次、判斷這一下蹲得夠不夠深、膝蓋有沒有往內夾，用綠黃紅三色即時回饋。

全程在你手機本機運算 —— 不連網、不錄影、不上傳，關掉就沒了。

這是我的論文專題。要上架 Google Play，規定必須先有 12 個人做滿 14 天封閉測試，所以來拜託大家。

需要你做的：
✅ 有 Android 手機
✅ 裝起來，這 14 天偶爾開來蹲個幾下
✅ 免費、無廣告、不用註冊帳號

⚠️ 請用你平常在用的 Google 帳號
（Google 會擋新辦的帳號，用新帳號幫不到我）

參加方式看下面第一則留言 👇
```

## 第一則留言（步驟）

```
參加步驟，三步驟五分鐘內搞定：

1️⃣ 先加入測試群組
<GROUP_LINK>

2️⃣ 再點這裡，按「成為測試人員」
<OPT_IN_LINK>
※ 這步最多人漏掉。沒按這個按鈕就不算數，我這邊會顯示人數不足。

3️⃣ 回 Google Play 商店搜尋「深蹲教練」安裝

裝好後隨便蹲個五下就行，不用每天。有任何問題直接留言問我 🙏
```

## 第二則留言（補充，選用）

```
幾個先講：

📱 目前只有 Android，iPhone 沒辦法（Play 商店的規定就是這樣）
📏 需要一點空間，手機立著、人站前面約 2 公尺，全身要入鏡
🔋 會開相機，跑久一點手機會溫溫的，正常
🩺 這不是醫療器材，只是健身輔助參考

測試期間如果覺得哪裡怪、卡住、閃退，都很歡迎告訴我，這對我論文超有幫助 🙏
```

---

## 追蹤用留言（人數卡住時再發）

```
更新：目前已經有 N 位完成 opt-in，感謝各位 🙏
還差 M 位就達標了！

提醒已經加入群組的朋友：記得還要點下面這個連結，按「成為測試人員」才算數喔
<OPT_IN_LINK>
```

---

## 發文前檢查

- [ ] 兩個連結都換成真的，並用**無痕視窗**各點一次確認陌生人打得開
- [ ] 自己先用非開發者帳號（`hata.s520@gmail.com`）走完整動線
- [ ] 附 2～3 張 App 截圖（綠色達標回饋、紅色提示、骨架偵測畫面最直觀）
- [ ] 發文時間避開深夜，週間晚上 8～10 點觸及較好

## 不要寫的內容（會踩到 Play 政策）

- ❌ 不要提供任何形式的報酬、抽獎、互相下載
- ❌ 不要說「裝了就好，不用開」—— Google 看的是實際使用，且這是幫倒忙
- ❌ 不要請人為此新辦 Google 帳號

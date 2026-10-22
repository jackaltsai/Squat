/**
 * 建立「深蹲教練」封閉測試的回饋表單（Google Apps Script）。
 *
 * ## 用法
 * 1. 開 https://script.google.com → 新增專案
 * 2. 把這整個檔案的內容貼進 `Code.gs`（覆蓋原本的 `myFunction`）
 * 3. 上方函式選 `createFeedbackForm` → 執行 → 第一次會要求授權（允許它建立表單）
 * 4. 執行紀錄會印出兩個網址：編輯用的、以及發給測試人員的
 *
 * ## 為什麼用程式碼而不是手動點
 * 申請 Production 的第一部分明文要問「**回饋意見的收集方式**」。
 * 表單的定義留在 git 裡，那一題就有具體答案可寫，而不是事後回想。
 *
 * ## 題目的設計理由（不是泛泛的「好不好用」）
 * - 第 2 題直接對應 Play 問的「**測試人員是否使用了所有可用的功能**」——
 *   六個動作逐一勾選，不要讓它變成事後回想。
 * - 第 3、4 題問的是**這個專案實際出過問題**的地方：計次漏算
 *   （舉手曾經 0 下、高抬腿右腳延後）、提示互相矛盾
 *   （「請往前站」與「請往後站」拉扯過十天）。
 * - 第 6 題讓「參與度」從自述變成**可稽核的資料** ——
 *   匯出的 CSV 每一列都有 exerciseType / localTime / depthRatio。
 *   「參與度不足」是 Play 明載的獨立退件理由。
 *
 * ⚠️ 刻意**不**收集 email、不要求登入：論文 4.3 的隱私規劃是「只存骨架關鍵點與
 * 判定結果」，表單這一側也不該多收個資。要追沒回覆的人就靠第 1 題的暱稱。
 */
function createFeedbackForm() {
  const form = FormApp.create('深蹲教練 封閉測試回饋');

  form.setDescription(
    '謝謝你幫忙測試「深蹲教練」。這份問卷大約兩分鐘。\n\n' +
    '請盡量具體 —— 「原地高抬腿時右腳抬了要等一下才算到」這種回答，' +
    '比「還不錯」有用一百倍。'
  );

  // 不收 email、不要求登入（見檔案開頭的隱私說明）
  form.setCollectEmail(false);
  form.setLimitOneResponsePerUser(false);
  form.setProgressBar(true);

  form.addTextItem()
    .setTitle('你的暱稱或稱呼')
    .setHelpText('只用來知道誰還沒回覆，不會寫進論文')
    .setRequired(true);

  form.addTextItem()
    .setTitle('你的手機型號')
    .setHelpText('例如 Pixel 7、Samsung A54、小米 13。設定 → 關於手機看得到')
    .setRequired(true);

  form.addCheckboxItem()
    .setTitle('這段期間你做過哪些動作？（可複選）')
    .setHelpText('沒做過的就不要勾 —— 誠實比好看重要')
    .setChoiceValues([
      '深蹲',
      '坐站練習',
      '原地高抬腿',
      '踮腳尖',
      '雙臂高舉',
      '擴胸推掌',
      '有看過「訓練統計」頁面',
    ])
    .setRequired(true);

  form.addMultipleChoiceItem()
    .setTitle('計次準不準？')
    .setChoiceValues([
      '都正確',
      '偶爾多算或少算',
      '經常不對',
      '完全數不到',
    ])
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('如果計次不準，是哪個動作、什麼情況？')
    .setHelpText(
      '例如：做原地高抬腿時，右腳抬完要等左腳再抬一次才一起跳 2 下。' +
      '沒遇到就寫「沒有」'
    )
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('語音或文字提示有沒有講錯、講不清楚的？')
    .setHelpText(
      '例如：一下叫你往前站、一下叫你往後站；' +
      '或提示跟你正在做的動作對不上（做舉手卻叫你蹲深一點）。沒遇到就寫「沒有」'
    )
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('有沒有閃退、畫面卡住、或文字重疊？')
    .setHelpText('有的話請說是在哪一個畫面。沒遇到就寫「沒有」')
    .setRequired(true);

  form.addParagraphTextItem()
    .setTitle('其他想說的（選填）')
    .setHelpText('字太小、按鈕太難按、動作說明看不懂 —— 什麼都可以')
    .setRequired(false);

  form.addSectionHeaderItem()
    .setTitle('最後一件事（選填，但幫助很大）')
    .setHelpText(
      '打開 App → 選擇動作的畫面 → 訓練統計 → 「匯出全部歷史紀錄」，' +
      '把產生的 CSV 檔用 LINE 或 email 傳給我。\n' +
      '裡面只有你做了哪些動作、各幾下、每一下的達成率 —— 沒有影像、沒有個資。'
    );

  Logger.log('編輯表單：%s', form.getEditUrl());
  Logger.log('發給測試人員：%s', form.getPublishedUrl());
}

/**
 * 「深蹲教練」封閉測試招募海報 — Word 版（白底、可編輯文字）。
 *
 * 用法：node design/make_poster_docx.js
 * 產出：design/深蹲教練_封閉測試招募.docx
 *
 * 為什麼文字是真的文字而不是一張圖：使用者這幾天一直在改文案，
 * 貼一張 PNG 進 Word 等於每次改字都要回來重產。
 */
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell,
  AlignmentType, WidthType, BorderStyle, VerticalAlign, HeightRule,
} = require("docx");

// QR 編碼的網址（兩個都已於 2026-10-22 實機掃碼確認）：
//   opt-in https://play.google.com/apps/testing/com.heartchen.squat
//   問卷   https://forms.gle/ojAW5pGWAFeiK2bLA
const ROOT = path.resolve(__dirname, "..");
const A = (f) => fs.readFileSync(path.join(ROOT, "design/assets", f));

// 白底上的字色：深色文字、深青做標示、深琥珀做警示。
// 深色版那組亮青/亮琥珀在白紙上對比不足，直接搬過來會看不清楚。
const INK = "1A1A1A";
const MUTE = "5A5A5A";
const TEAL = "0F766E";
const WARN = "B45309";

// Office 在 Windows 與 Mac 都會帶「微軟正黑體」；缺字時 Word 會自動替代。
const FONT = "Microsoft JhengHei";

const t = (text, o = {}) => new TextRun({
  text, font: FONT, color: o.color || INK, bold: !!o.bold,
  size: (o.pt || 11) * 2, ...(o.spacing ? { characterSpacing: o.spacing } : {}),
});

const line = (runs, o = {}) => new Paragraph({
  children: Array.isArray(runs) ? runs : [runs],
  alignment: o.align || AlignmentType.CENTER,
  spacing: { before: (o.before || 0) * 20, after: (o.after || 0) * 20, line: 300 },
  ...(o.rule ? {
    border: { bottom: { style: BorderStyle.SINGLE, size: 4, space: 10, color: "D4D4D4" } },
  } : {}),
});

const gap = (pts) => new Paragraph({ children: [], spacing: { after: pts * 20 } });

/** 一個步驟：左邊編號、右邊內文與說明。用表格才對得齊，不靠空白填。 */
function step(n, main, sub, warn) {
  const col = warn ? WARN : INK;
  const body = [new Paragraph({
    children: [t(main, { pt: 13, bold: true, color: col })],
    spacing: { after: sub ? 40 : 0, line: 280 },
  })];
  if (sub) {
    body.push(new Paragraph({
      children: [t(sub, { pt: 9.5, color: warn ? WARN : MUTE })],
      spacing: { line: 260 },
    }));
  }
  return new TableRow({
    children: [
      new TableCell({
        width: { size: 760, type: WidthType.DXA },
        margins: { top: 60, bottom: 60, left: 0, right: 0 },
        borders: noBorder(),
        verticalAlign: VerticalAlign.TOP,
        children: [new Paragraph({
          children: [t(String(n), { pt: 15, bold: true, color: warn ? WARN : TEAL })],
          spacing: { line: 280 },
        })],
      }),
      new TableCell({
        width: { size: 8878, type: WidthType.DXA },
        margins: { top: 60, bottom: 60, left: 0, right: 0 },
        borders: noBorder(),
        verticalAlign: VerticalAlign.TOP,
        children: body,
      }),
    ],
  });
}

const noBorder = () => ({
  top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
  bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
  left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
  right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
});

function groupHeading(tag, name) {
  return new TableRow({
    children: [
      new TableCell({
        width: { size: 9638, type: WidthType.DXA },
        columnSpan: 2,
        margins: { top: 200, bottom: 40, left: 0, right: 0 },
        borders: noBorder(),
        children: [new Paragraph({
          children: [t(tag + "　" + name, { pt: 10.5, bold: true, color: TEAL, spacing: 20 })],
          spacing: { line: 260 },
        })],
      }),
    ],
  });
}

const steps = new Table({
  columnWidths: [760, 8878],
  width: { size: 9638, type: WidthType.DXA },
  rows: [
    groupHeading("A", "加入"),
    step(1, "加入測試群組", null, false),
    step(2, "點連結按「成為測試人員」", "第 2 步沒點就不算數，最多人卡在這裡", false),
    step(3, "回 Play 商店安裝", null, false),
    groupHeading("B", "測試期間"),
    step(4, "這 14 天請不要解除安裝", "中途退出，整個測試要重新計算", true),
    step(5, "至少打開來用 3~4 次，六個動作都試一遍",
      "Google 會看使用狀況，只安裝不使用一樣過不了", true),
    step(6, "最後掃右下角的 QR 填一份兩分鐘的問卷", null, false),
  ],
});

/** 兩個 QR 並排。各自有標題、圖、說明，三層都置中。 */
function qrCell(file, title, caption, note) {
  return new TableCell({
    width: { size: 4819, type: WidthType.DXA },
    margins: { top: 100, bottom: 100, left: 120, right: 120 },
    borders: noBorder(),
    children: [
      new Paragraph({
        children: [t(title, { pt: 12, bold: true, color: TEAL })],
        alignment: AlignmentType.CENTER, spacing: { after: 120, line: 260 },
      }),
      new Paragraph({
        children: [new ImageRun({
          type: "png", data: A(file),
          transformation: { width: 168, height: 168 },
        })],
        alignment: AlignmentType.CENTER, spacing: { after: 120 },
      }),
      new Paragraph({
        children: [t(caption, { pt: 11.5, bold: true })],
        alignment: AlignmentType.CENTER, spacing: { after: 60, line: 260 },
      }),
      new Paragraph({
        children: [t(note, { pt: 9.5, color: MUTE })],
        alignment: AlignmentType.CENTER, spacing: { line: 260 },
      }),
    ],
  });
}

const qrs = new Table({
  columnWidths: [4819, 4819],
  width: { size: 9638, type: WidthType.DXA },
  rows: [new TableRow({
    children: [
      qrCell("qr_optin.png", "① 先掃這個", "成為測試人員", "掃完請用你平常在用的 Google 帳號登入"),
      qrCell("qr_form.png", "② 用過再掃這個", "填測試回饋問卷", "約兩分鐘，六題"),
    ],
  })],
});

const doc = new Document({
  creator: "深蹲教練",
  title: "深蹲教練 封閉測試招募",
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },          // A4
        margin: { top: 1021, bottom: 851, left: 1134, right: 1134 },
      },
    },
    children: [
      new Paragraph({
        children: [new ImageRun({
          type: "png", data: A("figure_squat_on_white.png"),
          transformation: { width: 156, height: 156 },
        })],
        // ⚠️ after 不能太小。行內圖的行盒不會替下一段讓位，
        // 第一版給 60 twips（3pt），渲染出來人偶的投影直接壓在「深蹲教練」上面。
        alignment: AlignmentType.CENTER, spacing: { after: 240 },
      }),
      line(t("深蹲教練", { pt: 34, bold: true, spacing: 60 }), { after: 4 }),
      line(t("徵 15 位 Android 測試員", { pt: 17, bold: true, color: TEAL, spacing: 20 }),
        { after: 10, rule: true }),

      line(t("手機立在前方兩公尺，自動計次", { pt: 14, bold: true }), { before: 12, after: 3 }),
      line(t("判斷深度・偵測膝內夾・語音提示", { pt: 12, color: MUTE }), { after: 6 }),
      line(t("六個動作：深蹲／坐站練習／原地高抬腿／踮腳尖／雙臂高舉／擴胸推掌",
        { pt: 11, color: INK }), { after: 6 }),
      line(t("App 沒有網路權限 — 不錄影、不上傳，做不到也不會做",
        { pt: 11, bold: true, color: TEAL }), { after: 10, rule: true }),

      steps,
      new Paragraph({
        children: [],
        border: { bottom: { style: BorderStyle.SINGLE, size: 4, space: 10, color: "D4D4D4" } },
        spacing: { before: 200, after: 200 },
      }),
      qrs,
    ],
  }],
});

const out = path.join(ROOT, "design/深蹲教練_封閉測試招募.docx");
Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(out, buf);
  console.log("輸出：", out, buf.length, "bytes");
});

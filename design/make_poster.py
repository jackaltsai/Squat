# -*- coding: utf-8 -*-
"""
「深蹲教練」封閉測試招募海報 — Calibrated Field。

設計理念見 design/PHILOSOPHY_calibrated_field.md。
輸出 A4 300dpi PNG（2480 x 3508）。

用法：python3 design/make_poster.py
"""
import os
import segno
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = ("/root/.claude/skills/synced/a3cf108f-87c0-48d9-aa41-01c5de9e2b1d"
         "_ce8e40f1-782d-43f8-9ddd-b8b5b7e09458/canvas-design/canvas-fonts")
WQY = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"

W, H = 2480, 3508
MARGIN = 170

# 四色票，不多不少（見理念「色與材質」）
FIELD_TOP = (6, 34, 39)
FIELD_BOT = (12, 52, 59)
PAPER = (242, 239, 233)
SIGNAL = (95, 210, 180)
WARN = (232, 163, 61)

# ✅ 2026-10-22 使用者實機掃過，確認導到正確的 opt-in 頁面。
# （這是 Play 封閉測試的標準網址格式 —— 由套件名推得，不是從 Console 複製的，
#  所以當初是「推測正確」而非「已知正確」。現在是已驗證的。）
OPT_IN_URL = "https://play.google.com/apps/testing/com.heartchen.squat"
FORM_URL = "forms.gle/ojAW5pGWAFeiK2bLA"


def cjk(size):
    return ImageFont.truetype(WQY, size)


def latin(size, bold=False):
    """西文與數字用人文主義無襯線體 —— 文泉驛的西文字母與標題並置時明顯鬆散。"""
    name = "InstrumentSans-Bold.ttf" if bold else "InstrumentSans-Regular.ttf"
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def mono(size, bold=False):
    """等寬只留給系統性的參照標記：編號、區段代號、單位。"""
    name = "GeistMono-Bold.ttf" if bold else "GeistMono-Regular.ttf"
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def _is_cjk(ch):
    """
    判斷要用哪一套字型畫這個字。

    ⚠️ 片假名區（0x30A0~0x30FF）**必須**算進來 —— 間隔號「・」是 U+30FB，
    落在那一區。少了它會被分到西文字型，而 InstrumentSans 沒有那個字符，
    於是畫出一個豆腐方塊。（第一版就是這樣，preview 上看得一清二楚。）
    破折號與箭頭刻意留給西文字型，那邊的字形比較俐落。
    """
    o = ord(ch)
    return (0x3000 <= o <= 0x303F        # CJK 標點
            or 0x3040 <= o <= 0x30FF     # 平假名、片假名（含 ・）
            or 0x3400 <= o <= 0x4DBF     # 擴充 A
            or 0x4E00 <= o <= 0x9FFF     # 基本區
            or 0xFF00 <= o <= 0xFFEF)    # 全形


def _runs(text):
    """把字串切成同書寫系統的連續段，西文段前後各留一點視覺空隙。"""
    out = []
    for ch in text:
        kind = "cjk" if _is_cjk(ch) else "lat"
        if out and out[-1][0] == kind:
            out[-1][1] += ch
        else:
            out.append([kind, ch])
    return [(k, t) for k, t in out]


def mixed(d, x, baseline, text, size, fill, bold=False, track=0, measure_only=False):
    """
    沿同一條基線混排中西文。回傳總寬度。

    CJK 與西文的 em 方盒不同，用 anchor="ls"（左—基線）讓兩者真正對齊，
    而不是各自用 bounding box 置頂 —— 後者會讓西文整段浮起來。
    """
    fc, fl = cjk(size), latin(size, bold)
    gap = size * 0.06
    total = 0.0
    parts = []
    for kind, t in _runs(text):
        f = fc if kind == "cjk" else fl
        if kind == "cjk" and track:
            w = sum(d.textlength(c, font=f) for c in t) + track * len(t)
        else:
            w = d.textlength(t, font=f)
        parts.append((kind, t, f, w))
        total += w
    total += gap * sum(1 for i, p in enumerate(parts)
                       if p[0] == "lat" and 0 < i < len(parts) - 1) * 2
    if measure_only:
        return total
    cx = x
    for i, (kind, t, f, w) in enumerate(parts):
        if kind == "lat" and 0 < i:
            cx += gap
        if kind == "cjk" and track:
            for c in t:
                d.text((cx, baseline), c, font=f, fill=fill, anchor="ls")
                cx += d.textlength(c, font=f) + track
        else:
            d.text((cx, baseline), t, font=f, fill=fill, anchor="ls")
            cx += w
        if kind == "lat" and i < len(parts) - 1:
            cx += gap
    return total


def dim(c, a):
    """把顏色按 alpha 混進場色（近似，避免半透明圖層堆疊）。"""
    return tuple(int(c[i] * a + FIELD_BOT[i] * (1 - a)) for i in range(3))


def field():
    """垂直漸層的場。未被量測的連續體。"""
    img = Image.new("RGB", (W, H), FIELD_TOP)
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        d.line(
            [(0, y), (W, y)],
            fill=tuple(int(FIELD_TOP[i] + (FIELD_BOT[i] - FIELD_TOP[i]) * t) for i in range(3)),
        )
    return img


def plate(d):
    """觀測圖板的邊框與刻度。每一個刻度都落在真正的格點上。"""
    inset = 96
    hair = dim(PAPER, 0.13)
    d.rectangle([inset, inset, W - inset, H - inset], outline=hair, width=2)
    # 左右邊緣的刻度：每 80px 一短刻、每 400px 一長刻
    step = 80
    y = inset + step
    while y < H - inset:
        long_tick = (y - inset) % (step * 5) == 0
        ln = 34 if long_tick else 16
        col = dim(PAPER, 0.20 if long_tick else 0.11)
        d.line([(inset, y), (inset + ln, y)], fill=col, width=2)
        d.line([(W - inset - ln, y), (W - inset, y)], fill=col, width=2)
        y += step


def rule(d, y, weight=0.22):
    d.line([(MARGIN, y), (W - MARGIN, y)], fill=dim(PAPER, weight), width=2)


def centered(d, baseline, text, size, fill, bold=False, track=0):
    """置中一行混排文字。y 是**基線**，不是頂端。"""
    total = mixed(d, 0, baseline, text, size, fill, bold, track, measure_only=True)
    mixed(d, (W - total) / 2, baseline, text, size, fill, bold, track)


def specimens(img, d, top):
    """
    六個動作並排。讓排列說話，不要讓句子說話 ——
    一行「本 App 有六個動作」遠不如六個等距的形狀有力。
    """
    names = [
        ("ic_exercise_squat.png", "深蹲"),
        ("ic_exercise_chair_squat.png", "坐站練習"),
        ("ic_exercise_high_knees.png", "高抬腿"),
        ("ic_exercise_heel_raise.png", "踮腳尖"),
        ("ic_exercise_arm_raise.png", "雙臂高舉"),
        ("ic_exercise_chest_expansion.png", "擴胸推掌"),
    ]
    cell = (W - 2 * MARGIN) / 6
    size = 300
    f_idx = mono(28)
    baseline_y = top + size + 6
    # 標本列的量測基線：六個形狀站在同一條線上，這條線本身就是刻度
    d.line([(MARGIN, baseline_y), (W - MARGIN, baseline_y)], fill=dim(PAPER, 0.16), width=2)
    for i, (fn, label) in enumerate(names):
        cx = MARGIN + cell * (i + 0.5)
        src = Image.open(os.path.join(ROOT, "app/src/main/res/drawable-nodpi", fn))
        fig = src.convert("RGBA").resize((size, size), Image.LANCZOS)
        img.paste(fig, (int(cx - size / 2), top), fig)
        d.line([(cx, baseline_y), (cx, baseline_y + 14)], fill=dim(SIGNAL, 0.45), width=2)
        d.text((cx, baseline_y + 30), f"{i + 1:02d}", font=f_idx,
               fill=dim(SIGNAL, 0.80), anchor="ma")
        w = mixed(d, 0, 0, label, 42, PAPER, measure_only=True)
        mixed(d, cx - w / 2, baseline_y + 118, label, 42, dim(PAPER, 0.86))
    return baseline_y + 118 + 36


def qr_panel(img, d, cy):
    """QR 落在白色量測面板上，模組對齊到整數像素。"""
    q = segno.make(OPT_IN_URL, error="m")
    m = [list(row) for row in q.matrix]
    n = len(m)
    quiet = 4
    scale = 15                      # error="m" 模組少，單格可以放大 → 好掃
    side = (n + quiet * 2) * scale
    panel = Image.new("RGB", (side, side), (255, 255, 255))
    pd = ImageDraw.Draw(panel)
    for r in range(n):
        for c in range(n):
            if m[r][c]:
                x0 = (c + quiet) * scale
                y0 = (r + quiet) * scale
                pd.rectangle([x0, y0, x0 + scale - 1, y0 + scale - 1], fill=(8, 36, 41))
    x = int(W / 2 - side / 2)
    img.paste(panel, (x, cy))
    # 四角的對位角標 —— 儀器的語言
    k, off, col = 46, 26, dim(SIGNAL, 0.55)
    for sx, sy in ((x - off, cy - off), (x + side + off, cy - off),
                   (x - off, cy + side + off), (x + side + off, cy + side + off)):
        dx = k if sx < W / 2 else -k
        dy = k if sy < cy + side / 2 else -k
        d.line([(sx, sy), (sx + dx, sy)], fill=col, width=3)
        d.line([(sx, sy), (sx, sy + dy)], fill=col, width=3)
    return cy + side


def steps(d, y):
    """
    ①②③ 與 ④⑤⑥ 在同一條編號序列上 —— 放進同一串才會被讀成「要做的事」，
    而不是小字免責聲明。測試期間那兩條用警戒色：它們決定這次測試算不算數。
    """
    groups = [
        ("A", "加入", [
            ("加入測試群組", None, False),
            ("點連結按「成為測試人員」", "第 2 步沒點就不算數，最多人卡在這裡", False),
            ("回 Play 商店安裝", None, False),
        ]),
        ("B", "測試期間", [
            ("這 14 天請不要解除安裝", "中途退出，整個測試要重新計算", True),
            ("至少打開來用 3~4 次，六個動作都試一遍",
             "Google 會看使用狀況，只安裝不使用一樣過不了", True),
            ("最後填一份兩分鐘的問卷", FORM_URL, False),
        ]),
    ]
    f_grp = mono(30, bold=True)
    f_num = mono(52, bold=True)
    x_num = MARGIN + 16
    x_txt = MARGIN + 126
    i = 0
    for tag, gname, items in groups:
        d.text((x_num, y), tag, font=f_grp, fill=dim(SIGNAL, 0.85), anchor="ls")
        mixed(d, x_num + 46, y, gname, 32, dim(PAPER, 0.46))
        d.line([(x_num + 46 + mixed(d, 0, 0, gname, 32, PAPER, measure_only=True) + 26,
                 y - 11), (W - MARGIN, y - 11)], fill=dim(PAPER, 0.13), width=2)
        y += 78
        for main_t, sub, warn in items:
            i += 1
            accent = WARN if warn else SIGNAL
            d.text((x_num, y), f"{i}", font=f_num, fill=dim(accent, 0.95), anchor="ls")
            mixed(d, x_txt, y, main_t, 52, WARN if warn else PAPER)
            if sub:
                mixed(d, x_txt, y + 60, sub, 33,
                      dim(WARN, 0.80) if warn else dim(PAPER, 0.52))
                y += 60 + 62
            else:
                y += 80
        y += 34
    return y


def main():
    img = field()
    d = ImageDraw.Draw(img)
    plate(d)

    y = MARGIN + 118
    centered(d, y, "CLOSED  TESTING", 34, dim(SIGNAL, 0.70), track=0)
    y += 224
    centered(d, y, "深蹲教練", 208, PAPER, track=22)
    y += 126
    centered(d, y, "徵 15 位 Android 測試員", 76, SIGNAL, track=3)
    y += 56

    rule(d, y)
    y += 48
    y = specimens(img, d, int(y))

    y += 44
    centered(d, y, "手機立在前方兩公尺，自動計次", 60, PAPER)
    y += 78
    centered(d, y, "判斷深度・偵測膝內夾・語音提示", 52, dim(PAPER, 0.78))
    y += 86
    centered(d, y, "App 沒有網路權限 — 不錄影、不上傳，做不到也不會做",
             42, dim(SIGNAL, 0.92))
    y += 56

    rule(d, y)
    y += 82
    y = steps(d, int(y))

    y = qr_panel(img, d, int(y) + 16)
    y += 80
    centered(d, y, "掃我 → 成為測試人員", 52, PAPER)
    y += 70
    centered(d, y, "請用你平常在用的 Google 帳號", 44, WARN)

    print("最後一行基線 y =", y, "/ 下邊距起點", H - MARGIN)
    assert y + 30 < H - MARGIN, "超出下邊距"

    out = os.path.join(ROOT, "design/poster_closed_test.png")
    img.save(out, "PNG")
    # LINE / 訊息軟體用的縮圖版：A4 300dpi 的檔案太大，傳出去會被二次壓縮
    share = os.path.join(ROOT, "design/poster_closed_test_share.png")
    img.resize((1240, 1754), Image.LANCZOS).save(share, "PNG", optimize=True)
    print("輸出：", out, img.size)
    print("分享用：", share, "(1240x1754)")
    print("QR 編碼的網址：", OPT_IN_URL)


if __name__ == "__main__":
    main()

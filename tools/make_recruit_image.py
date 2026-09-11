from PIL import Image, ImageDraw, ImageFont
import qrcode

OPT_IN = "https://play.google.com/apps/testing/com.heartchen.squat"
FONT = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
def f(size, idx=0): return ImageFont.truetype(FONT, size, index=idx)

# 單獨 QR
q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=20, border=3)
q.add_data(OPT_IN); q.make(fit=True)
q.make_image(fill_color="black", back_color="white").convert("RGB").save("docs/qr-opt-in.png")

W, H = 1080, 1350
img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)
top, bot = (11, 27, 43), (13, 74, 82)
for y in range(H):
    t = y / H
    d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bot[i]-top[i]) * t) for i in range(3)))

# 雷達環畫在 RGBA 圖層再合成，直接畫在 RGB 上 alpha 會被忽略、環會變成實白線
ov = Image.new("RGBA", (W, H), (0,0,0,0))
od = ImageDraw.Draw(ov)
for r in range(200, 1000, 130):
    od.ellipse([W-140-r, -200-r, W-140+r, -200+r], outline=(255,255,255,20), width=2)
img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
d = ImageDraw.Draw(img)

def center(text, y, font, fill="white"):
    d.text(((W - d.textlength(text, font=font))/2, y), text, font=font, fill=fill)

icon = Image.open("app/src/main/ic_launcher-playstore.png").convert("RGBA").resize((146,146))
mask = Image.new("L", (146,146), 0)
ImageDraw.Draw(mask).rounded_rectangle([0,0,146,146], radius=33, fill=255)
img.paste(icon, ((W-146)//2, 56), mask)

center("深蹲教練", 228, f(76, 2))
center("徵 12 位 Android 測試員", 330, f(38), "#7FE3D4")
d.line([(150, 404), (W-150, 404)], fill=(120,150,165), width=2)

center("手機立在前方兩公尺", 438, f(33))
center("自動計次・判斷深度・偵測膝內夾", 486, f(33))
center("全程本機運算，不連網、不錄影、不上傳", 550, f(27), "#9FB8C8")

y = 628
for num, label in [("1","加入測試群組"), ("2","點連結按「成為測試人員」"), ("3","回 Play 商店安裝")]:
    d.ellipse([132, y, 178, y+46], fill="#1FC8A9")
    d.text((155 - d.textlength(num, font=f(29))/2, y+7), num, font=f(29), fill="#0B1B2B")
    d.text((202, y+5), label, font=f(31), fill="white")
    y += 70
d.text((202, y+2), "※ 第 2 步沒點就不算數，最多人卡在這裡", font=f(22), fill="#FFB74D")

# QR 卡片：910~1190，下方留給說明與警告，不再互相壓字
CS, CX, CY = 280, (W-280)//2, 906
d.rounded_rectangle([CX-18, CY-18, CX+CS+18, CY+CS+18], radius=24, fill="white")
qp = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=10, border=1)
qp.add_data(OPT_IN); qp.make(fit=True)
img.paste(qp.make_image(fill_color="#0B1B2B", back_color="white").convert("RGB").resize((CS,CS)), (CX, CY))

center("掃我 → 成為測試人員", CY+CS+44, f(27), "#CFE0EA")
# 用畫的三角形代替 ⚠ emoji：WQY 沒有該字符，會變成豆腐字
wy = CY+CS+96
wt, wf = "請用你平常在用的 Google 帳號", f(28)
tw = d.textlength(wt, font=wf)
tri_x = (W - (tw+44))/2
d.polygon([(tri_x+14, wy+4), (tri_x+28, wy+30), (tri_x, wy+30)], fill="#FFB74D")
d.text((tri_x+44, wy), wt, font=wf, fill="#FFB74D")

img.save("docs/threads-recruit.png")
print("ok", img.size)

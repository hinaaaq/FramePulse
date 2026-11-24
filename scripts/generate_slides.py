import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1080, 1920
OUT_DIR = os.environ.get("OUT_DIR", "slides_output")
VERSION = os.environ.get("APP_VERSION", "v1.1.0")
os.makedirs(OUT_DIR, exist_ok=True)

def load_font(candidates, size):
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()

BOLD_PATHS = [
    "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
]
REG_PATHS = [
    "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
]
MONO_PATHS = [
    "/usr/share/fonts/truetype/liberation/LiberationMono-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
]

BG = (10, 13, 20)
CARD = (18, 24, 38)
CARD_DARK = (13, 18, 30)
BORDER = (36, 48, 72)
WHITE = (245, 248, 255)
MUTED = (156, 169, 194)
GREEN = (0, 229, 160)
RED = (255, 77, 109)
CYAN = (56, 189, 248)
AMBER = (251, 191, 36)

def draw_bg(accent):
    img = Image.new("RGB", (W, H), BG)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((W - 380, -140, W + 220, 460), fill=(*accent, 75))
    gd.ellipse((-200, H - 520, 360, H + 40), fill=(*accent, 55))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=120))
    img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")
    draw = ImageDraw.Draw(img, "RGBA")
    for x in range(0, W, 60):
        draw.line([(x, 0), (x, H)], fill=(255, 255, 255, 9), width=1)
    for y in range(0, H, 60):
        draw.line([(0, y), (W, y)], fill=(255, 255, 255, 9), width=1)
    return img, draw

def wrap_text(draw, text, font, max_w):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        test = (cur + " " + w).strip()
        bbox = draw.textbbox((0, 0), test, font=font)
        if bbox[2] - bbox[0] <= max_w:
            cur = test
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines

def draw_multiline(draw, x, y, text, font, fill, max_w, line_gap=14):
    lines = wrap_text(draw, text, font, max_w)
    cur_y = y
    for line in lines:
        draw.text((x, cur_y), line, font=font, fill=fill)
        bbox = draw.textbbox((x, cur_y), line, font=font)
        cur_y += (bbox[3] - bbox[1]) + line_gap
    return cur_y

def draw_pill(draw, x, y, text, font, accent, bg_alpha=36):
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad_x, pad_y = 26, 14
    draw.rounded_rectangle(
        (x, y, x + tw + pad_x * 2, y + th + pad_y * 2),
        radius=28,
        fill=(*accent, bg_alpha),
        outline=(*accent, 180),
        width=2,
    )
    draw.text((x + pad_x, y + pad_y - 2), text, font=font, fill=accent)
    return y + th + pad_y * 2

def draw_header_footer(draw, slide_num, total, accent, footer_text="SWIPE  >>>"):
    f_mono = load_font(MONO_PATHS, 28)
    f_foot = load_font(MONO_PATHS, 26)
    draw.rounded_rectangle((76, 150, 455, 204), radius=14, fill=(18, 24, 38, 230), outline=(*BORDER, 255), width=2)
    draw.ellipse((98, 169, 114, 185), fill=accent)
    draw.text((128, 162), f"FRAMEPULSE {VERSION}", font=f_mono, fill=WHITE)

    num_txt = f"0{slide_num} / 0{total}"
    draw.rounded_rectangle((830, 150, 1004, 204), radius=14, fill=(18, 24, 38, 230), outline=(*BORDER, 255), width=2)
    draw.text((856, 162), num_txt, font=f_mono, fill=MUTED)

    bar_w = 928
    seg_w = (bar_w - 4 * 14) // 5
    for i in range(5):
        bx = 76 + i * (seg_w + 14)
        col = accent if i < slide_num else (40, 50, 70)
        draw.rounded_rectangle((bx, 1540, bx + seg_w, 1550), radius=5, fill=col)

    draw.text((76, 1572), footer_text, font=f_foot, fill=accent)
    draw.text((645, 1572), "github.com/hinaaaq", font=f_foot, fill=MUTED)

f_tag = load_font(MONO_PATHS, 28)
f_h1 = load_font(BOLD_PATHS, 70)
f_h1_sm = load_font(BOLD_PATHS, 62)
f_sub = load_font(REG_PATHS, 38)
f_card_t = load_font(BOLD_PATHS, 34)
f_card_b = load_font(REG_PATHS, 30)
f_mono_lg = load_font(MONO_PATHS, 46)
f_mono_md = load_font(MONO_PATHS, 30)
f_mono_sm = load_font(MONO_PATHS, 25)

# SLIDE 1
img, d = draw_bg(RED)
draw_header_footer(d, 1, 5, RED)
y = draw_pill(d, 76, 265, "REALITY CHECK", f_tag, RED)
y = draw_multiline(d, 76, y + 36, 'Stop percaya app "Game Booster" yang janjiin FPS naik instan.', f_h1, WHITE, 880, 18)
y = draw_multiline(d, 76, y + 26, "Kebanyakan cuma bersihin RAM bentar, habis itu HP tetep panas dan frame drop.", f_sub, MUTED, 880, 14)
cy = y + 60
d.rounded_rectangle((76, cy, 980, cy + 430), radius=30, fill=CARD, outline=BORDER, width=2)
d.rounded_rectangle((112, cy + 38, 944, cy + 190), radius=20, fill=(38, 18, 26), outline=(*RED, 140), width=2)
d.text((146, cy + 62), "X  GAME BOOSTER GIMIK", font=f_mono_md, fill=RED)
d.text((146, cy + 114), "+99% TURBO FPS BOOST  ->  Cuma animasi palsu", font=f_card_b, fill=(220, 170, 180))
d.rounded_rectangle((112, cy + 225, 944, cy + 390), radius=20, fill=(14, 36, 32), outline=(*GREEN, 160), width=2)
d.text((146, cy + 250), "OK  TELEMETRI JUJUR", font=f_mono_md, fill=GREEN)
d.text((146, cy + 302), "Pantau True FPS, 1% Low & Suhu secara real-time", font=f_card_b, fill=WHITE)
img.save(f"{OUT_DIR}/slide_1.png")

# SLIDE 2
img, d = draw_bg(AMBER)
draw_header_footer(d, 2, 5, AMBER)
y = draw_pill(d, 76, 265, "KENAPA TETEP LAG?", f_tag, AMBER)
y = draw_multiline(d, 76, y + 36, "Avg FPS 60, tapi game masih kerasa patah-patah?", f_h1, WHITE, 880, 18)
y = draw_multiline(d, 76, y + 24, "Biang keroknya adalah 1% Low FPS yang anjlok pas HP kena Thermal Throttle.", f_sub, MUTED, 880, 14)
cy = y + 55
d.rounded_rectangle((76, cy, 980, cy + 500), radius=30, fill=CARD, outline=BORDER, width=2)
d.text((116, cy + 34), "LIVE TELEMETRY TRACE (10 MIN)", font=f_mono_sm, fill=MUTED)
d.rounded_rectangle((660, cy + 26, 942, cy + 72), radius=12, fill=(48, 18, 26), outline=RED, width=2)
d.text((680, cy + 36), "THROTTLED 44.2 C", font=f_mono_sm, fill=RED)
gx1, gy1, gx2, gy2 = 116, cy + 105, 940, cy + 365
d.rounded_rectangle((gx1, gy1, gx2, gy2), radius=16, fill=CARD_DARK, outline=BORDER, width=1)
for gy in range(gy1 + 52, gy2, 52):
    d.line([(gx1, gy), (gx2, gy)], fill=(30, 40, 58), width=1)
avg_pts = [(136, gy1+62), (250, gy1+58), (370, gy1+66), (490, gy1+60), (610, gy1+72), (730, gy1+78), (850, gy1+74), (920, gy1+70)]
d.line(avg_pts, fill=GREEN, width=6)
low_pts = [(136, gy1+105), (250, gy1+112), (370, gy1+128), (490, gy1+195), (560, gy1+135), (640, gy1+230), (740, gy1+175), (830, gy1+235), (920, gy1+205)]
d.line(low_pts, fill=RED, width=6)
for px, py in [(490, gy1+195), (640, gy1+230), (830, gy1+235)]:
    d.ellipse((px-9, py-9, px+9, py+9), fill=RED, outline=WHITE, width=2)
d.text((116, cy + 392), "AVG FPS", font=f_mono_sm, fill=MUTED)
d.text((116, cy + 425), "58.4", font=f_mono_lg, fill=GREEN)
d.text((380, cy + 392), "1% LOW FPS", font=f_mono_sm, fill=MUTED)
d.text((380, cy + 425), "21.3", font=f_mono_lg, fill=RED)
d.text((665, cy + 392), "FEELING", font=f_mono_sm, fill=MUTED)
d.text((665, cy + 425), "STUTTER!", font=f_mono_lg, fill=AMBER)
img.save(f"{OUT_DIR}/slide_2.png")

# SLIDE 3
img, d = draw_bg(GREEN)
draw_header_footer(d, 3, 5, GREEN)
y = draw_pill(d, 76, 265, "NON-ROOT + SHIZUKU", f_tag, GREEN)
y = draw_multiline(d, 76, y + 32, f"Makanya gw bikin FramePulse {VERSION}", f_h1_sm, WHITE, 880, 14)
cy = y + 32
d.rounded_rectangle((76, cy, 980, cy + 250), radius=28, fill=CARD, outline=(*GREEN, 160), width=2)
d.text((114, cy + 28), "HUD OVERLAY  //  MODE: DETAILED", font=f_mono_sm, fill=GREEN)
d.rounded_rectangle((780, cy + 22, 942, cy + 68), radius=12, fill=(52, 18, 26), outline=RED, width=2)
d.ellipse((798, cy + 37, 814, cy + 53), fill=RED)
d.text((826, cy + 32), "REC", font=f_mono_sm, fill=WHITE)
metrics = [("TRUE FPS", "59.6", GREEN), ("1% LOW", "51.2", CYAN), ("TEMP", "39.4 C", AMBER)]
for idx, (lbl, val, col) in enumerate(metrics):
    mx = 114 + idx * 285
    d.rounded_rectangle((mx, cy + 90, mx + 258, cy + 218), radius=18, fill=CARD_DARK, outline=BORDER, width=1)
    d.text((mx + 22, cy + 108), lbl, font=f_mono_sm, fill=MUTED)
    d.text((mx + 22, cy + 148), val, font=f_mono_lg, fill=col)
fy = cy + 285
features = [
    ("Mode HUD & Preset per Game", "Switch Compact/Detailed, atur opasitas & preset per game."),
    ("Riwayat 20 Benchmark Lokal", "Hitung otomatis selisih Delta Avg FPS, 1% Low & Peak Temp."),
    ("Thermal Throttle & Flex Card", "Rangkum hasil tes jadi laporan bersih siap share."),
    ("Leaderboard Global & Updater", "Skor terverifikasi Cloudflare Workers + cek update otomatis."),
]
for title, desc in features:
    d.rounded_rectangle((76, fy, 980, fy + 132), radius=22, fill=CARD, outline=BORDER, width=2)
    d.ellipse((108, fy + 34, 128, fy + 54), fill=GREEN)
    d.text((148, fy + 24), title, font=f_card_t, fill=WHITE)
    draw_multiline(d, 148, fy + 72, desc, load_font(REG_PATHS, 26), MUTED, 795, 6)
    fy += 150
img.save(f"{OUT_DIR}/slide_3.png")

# SLIDE 4
img, d = draw_bg(CYAN)
draw_header_footer(d, 4, 5, CYAN)
y = draw_pill(d, 76, 265, "FUN FACT DEV", f_tag, CYAN)
y = draw_multiline(d, 76, y + 36, "100% dibikin tanpa PC & tanpa root.", f_h1, WHITE, 880, 18)
y = draw_multiline(d, 76, y + 24, f"Ngoding, build APK, sampai rilis {VERSION} full cuma modal HP Samsung Galaxy A24.", f_sub, MUTED, 880, 14)
cy = y + 48
d.rounded_rectangle((76, cy, 980, cy + 595), radius=28, fill=(11, 15, 24), outline=(*CYAN, 150), width=2)
d.rounded_rectangle((76, cy, 980, cy + 68), radius=28, fill=(20, 28, 44))
d.rectangle((76, cy + 40, 980, cy + 68), fill=(20, 28, 44))
for i, c in enumerate([RED, AMBER, GREEN]):
    d.ellipse((112 + i * 32, cy + 24, 132 + i * 32, cy + 44), fill=c)
d.text((230, cy + 20), "termux@SM-A245F:~/FramePulse", font=f_mono_sm, fill=MUTED)
term_lines = [
    ("$ device", "Samsung Galaxy A24 (Non-Root)", WHITE),
    ("$ stack", "Kotlin + Jetpack Compose + Shizuku", CYAN),
    ("$ backend", "Cloudflare Workers + KV", WHITE),
    (f"$ sh release.sh {VERSION}", "", AMBER),
    ("[OK] GitHub Actions", "Signed APK built!", GREEN),
    ("[OK] GitHub Releases", f"{VERSION} live!", GREEN),
]
ty = cy + 98
for left, right, col in term_lines:
    d.text((112, ty), left, font=f_mono_md, fill=col)
    if right:
        d.text((112, ty + 36), f"  -> {right}", font=f_mono_sm, fill=MUTED)
        ty += 82
    else:
        ty += 52
img.save(f"{OUT_DIR}/slide_4.png")

# SLIDE 5
img, d = draw_bg(GREEN)
draw_header_footer(d, 5, 5, GREEN, footer_text="CEK GITHUB RELEASES")
y = draw_pill(d, 76, 265, "GRATIS & NO GIMIK", f_tag, GREEN)
y = draw_multiline(d, 76, y + 36, "Ringan. Jujur. Siap pakai di Android Non-Root.", f_h1_sm, WHITE, 880, 16)
cy = y + 44
d.rounded_rectangle((76, cy, 980, cy + 365), radius=28, fill=CARD, outline=(*GREEN, 180), width=2)
d.text((114, cy + 32), "FRAMEPULSE FLEX CARD  //  VERIFIED", font=f_mono_sm, fill=GREEN)
d.line([(114, cy + 78), (942, cy + 78)], fill=BORDER, width=2)
stats = [("AVG FPS", "58.6", GREEN), ("1% LOW", "49.1", CYAN), ("PEAK TEMP", "40.2 C", AMBER)]
for idx, (lbl, val, col) in enumerate(stats):
    sx = 114 + idx * 282
    d.text((sx, cy + 106), lbl, font=f_mono_sm, fill=MUTED)
    d.text((sx, cy + 146), val, font=f_mono_lg, fill=col)
d.rounded_rectangle((114, cy + 232, 942, cy + 324), radius=18, fill=(14, 34, 30), outline=(*GREEN, 120), width=1)
d.text((144, cy + 258), "DELTA vs SESI LALU:  +3.2 Avg FPS  |  -1.4 C Peak", font=f_mono_sm, fill=WHITE)
cta_y = cy + 405
d.rounded_rectangle((76, cta_y, 980, cta_y + 235), radius=28, fill=(15, 28, 38), outline=(*CYAN, 160), width=2)
d.text((114, cta_y + 34), "Download APK di GitHub Releases:", font=f_sub, fill=MUTED)
d.text((114, cta_y + 92), "hinaaaq/FramePulse", font=load_font(MONO_PATHS, 54), fill=GREEN)
d.text((114, cta_y + 170), "Drop tipe HP lu di komen, yuk adu 1% Low!", font=f_card_b, fill=WHITE)
img.save(f"{OUT_DIR}/slide_5.png")

print("Done! Slides saved to", OUT_DIR)

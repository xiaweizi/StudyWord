#!/usr/bin/env python3
"""
build_app_icons.py —— 生成 10 个 App 图标候选,供挑选后再落地为自适应图标。

设计约束(不是随意画)
--------------------
1. **自适应图标安全区**:Android 自适应图标是 108dp 画布,只有中间 **72dp(66.7%)**
   保证可见,外围会被启动器裁成圆形/方形/水滴形。所以**焦点元素必须落在中央 66% 内**;
   背景渐变/纹理可以出血(它们本来就是背景层)。
2. **小尺寸可读**:图标在桌面可能只有 48px,所以每张只有一个视觉焦点,字号尽量大。
3. **字形与应用一致**:用项目里的楷体子集(StudyWordKai)渲染「字」。
4. **配色沿用应用设计 token**(deep_blue / tangerine / cream / mint …)。

实现要点
--------
每个设计分两层:
- `bg`:背景(渐变/底色/纹理)——允许出血
- `focal`:焦点元素(字/星光/下划线)——**画在独立透明层上**

这样脚本可以用 `focal.getbbox()` **精确**量出焦点范围并校验安全区,
而不是"拿角落颜色当基准去猜" —— 后者会把渐变背景本身误判成越界元素。

输出
----
- icons/candidates/icon_01.png … icon_10.png  (512×512)
- icons/candidates/contact_sheet.png          (10 个拼一张,含 48px 小尺寸预览)

用法
----
    python scripts/build_app_icons.py
"""

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
FONT_PATH = ROOT / "app" / "src" / "main" / "res" / "font" / "studyword_kai.ttf"
OUT_DIR = ROOT / "icons" / "candidates"

SIZE = 512
SAFE = int(SIZE * 0.667)
SAFE_LO = (SIZE - SAFE) // 2
SAFE_HI = SAFE_LO + SAFE

DEEP_BLUE = (78, 110, 228)
TANGERINE = (255, 155, 98)
CREAM = (255, 247, 230)
MINT = (184, 242, 230)
SUNSHINE = (255, 225, 123)
PINK = (255, 179, 217)
SKY = (167, 216, 255)
NAVY = (46, 58, 138)
WHITE = (255, 255, 255)


# ---------------------------------------------------------------- helpers

def vgrad(top, bottom):
    img = Image.new("RGB", (SIZE, SIZE))
    d = ImageDraw.Draw(img)
    for y in range(SIZE):
        t = y / (SIZE - 1)
        d.line([(0, y), (SIZE, y)],
               fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img


def highlight(base, cx_r=0.34, cy_r=0.30, rad_r=0.42, strength=64):
    """柔和径向高光,给纯色/渐变底一点体积感"""
    layer = Image.new("L", (SIZE, SIZE), 0)
    d = ImageDraw.Draw(layer)
    cx, cy, R = SIZE * cx_r, SIZE * cy_r, SIZE * rad_r
    for i in range(44, 0, -1):
        r = R * i / 44
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=int(strength * (1 - i / 44) ** 1.5))
    layer = layer.filter(ImageFilter.GaussianBlur(SIZE * 0.035))
    return Image.composite(Image.new("RGB", (SIZE, SIZE), WHITE), base, layer)


def font(px):
    return ImageFont.truetype(str(FONT_PATH), px)


def text_at(d, text, px, color, center=(SIZE / 2, SIZE / 2), dx=0, dy=0):
    f = font(px)
    box = d.textbbox((0, 0), text, font=f)
    w, h = box[2] - box[0], box[3] - box[1]
    d.text((center[0] - w / 2 - box[0] + dx, center[1] - h / 2 - box[1] + dy),
           text, font=f, fill=color)


def sparkle(d, cx, cy, r, color):
    pts = []
    for i in range(8):
        ang = math.pi / 4 * i - math.pi / 2
        rr = r if i % 2 == 0 else r * 0.38
        pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    d.polygon(pts, fill=color)


def rounded_mask(radius_ratio=0.225):
    m = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [0, 0, SIZE - 1, SIZE - 1], radius=int(SIZE * radius_ratio), fill=255)
    return m


# ---------------------------------------------------------------- 10 个设计
# 每个返回 (bg_image, focal_drawer)，focal_drawer 只在透明层上画焦点元素

def d01():
    return (highlight(vgrad((108, 132, 240), DEEP_BLUE)),
            lambda d: text_at(d, "字", 300, WHITE, dy=-4))


def d02():
    bg = Image.new("RGB", (SIZE, SIZE), (250, 240, 216))
    inset = (SIZE - SAFE) // 2 + 8
    ImageDraw.Draw(bg).rounded_rectangle(
        [inset, inset, SIZE - inset - 1, SIZE - inset - 1], radius=52, fill=CREAM)
    return bg, lambda d: text_at(d, "字", 282, DEEP_BLUE)


def d03():
    def focal(d):
        text_at(d, "字", 280, WHITE, dy=10)
        sparkle(d, SIZE - 152, 156, 40, WHITE)
    return highlight(vgrad((255, 192, 122), TANGERINE)), focal


def d04():
    bg = Image.new("RGB", (SIZE, SIZE), MINT)
    d = ImageDraw.Draw(bg)
    step = SIZE // 6
    for i in range(1, 6):                      # 格线属于背景纹理,允许出血
        d.line([(i * step, 0), (i * step, SIZE)], fill=(150, 220, 205), width=3)
        d.line([(0, i * step), (SIZE, i * step)], fill=(150, 220, 205), width=3)
    return bg, lambda d2: text_at(d2, "字", 286, DEEP_BLUE)


def d05():
    bg = vgrad((205, 232, 255), SKY)
    r = SAFE // 2 - 4
    ImageDraw.Draw(bg).ellipse(
        [SIZE // 2 - r, SIZE // 2 - r, SIZE // 2 + r, SIZE // 2 + r], fill=WHITE)
    return bg, lambda d: text_at(d, "字", 268, DEEP_BLUE)


def d06():
    return (highlight(vgrad((62, 78, 172), NAVY), strength=48),
            lambda d: text_at(d, "字", 300, SUNSHINE))


def d07():
    def focal(d):
        text_at(d, "字", 236, WHITE, dy=-46)
        text_at(d, "A", 104, SUNSHINE, dx=98, dy=76)
    return highlight(vgrad((96, 122, 236), DEEP_BLUE)), focal


def d08():
    def focal(d):
        text_at(d, "字", 252, DEEP_BLUE, dy=-40)
        d.rounded_rectangle([146, 330, SIZE - 146, 358], radius=14, fill=TANGERINE)
    return Image.new("RGB", (SIZE, SIZE), CREAM), focal


def d09():
    def focal(d):
        text_at(d, "字", 268, WHITE, dy=6)
        sparkle(d, 158, 160, 34, WHITE)
        sparkle(d, SIZE - 160, SIZE - 168, 28, WHITE)
    return highlight(vgrad((255, 208, 232), PINK)), focal


def d10():
    def focal(d):
        f = font(92)
        for i, ch in enumerate("一二三"):
            box = d.textbbox((0, 0), ch, font=f)
            w = box[2] - box[0]
            d.text(((SIZE - w) / 2 - box[0], 106 + i * 96), ch, font=f, fill=DEEP_BLUE)
        d.rounded_rectangle([SIZE // 2 - 76, 400, SIZE // 2 + 76, 424], radius=12, fill=TANGERINE)
    return Image.new("RGB", (SIZE, SIZE), CREAM), focal


ICONS = [
    ("01 蓝紫渐变·白字", d01),
    ("02 米白纸卡·蓝字", d02),
    ("03 暖橙渐变·星", d03),
    ("04 薄荷格线·蓝字", d04),
    ("05 天蓝圆徽章", d05),
    ("06 深蓝·黄字", d06),
    ("07 中英双语 字+A", d07),
    ("08 米白·笔锋下划线", d08),
    ("09 粉底·双星", d09),
    ("10 一二三 进阶", d10),
]


def main() -> int:
    if not FONT_PATH.exists():
        print(f"[ERROR] 找不到字体: {FONT_PATH}", file=sys.stderr)
        return 1
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    mask = rounded_mask()

    rendered = []
    print("生成候选:")
    for name, fn in ICONS:
        bg, draw_focal = fn()
        focal = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        draw_focal(ImageDraw.Draw(focal))

        img = Image.alpha_composite(bg.convert("RGBA"), focal)
        img.putalpha(mask)
        idx = name.split()[0]
        img.save(OUT_DIR / f"icon_{idx}.png")
        rendered.append((name, img, focal.getbbox()))
        print(f"  icon_{idx}.png  {name}")

    # ---- 安全区合规检查(量焦点层,精确) ----
    print(f"\n安全区合规检查(焦点需落在 {SAFE_LO}~{SAFE_HI}):")
    bad = []
    for name, _, bbox in rendered:
        if bbox is None:
            print(f"  ?? {name} 焦点层为空")
            continue
        x0, y0, x1, y1 = bbox
        ok = x0 >= SAFE_LO and x1 <= SAFE_HI and y0 >= SAFE_LO and y1 <= SAFE_HI
        if not ok:
            bad.append(name.split()[0])
        print(f"  {'OK ' if ok else '!! '} {name:<22} x {x0:>3}~{x1 - 1:<3} y {y0:>3}~{y1 - 1:<3}  "
              f"{'在安全区内' if ok else '**越界**'}")
    print("\n全部设计均在安全区内 ✓" if not bad
          else f"\n[WARN] 需调整: {', '.join(bad)}")

    # ---- 拼图(4 列 × 3 行,末格放 48px 小尺寸) ----
    cols, rows, cell, gap, pad = 4, 3, 200, 22, 26
    sheet = Image.new("RGB", (pad * 2 + cols * cell + (cols - 1) * gap,
                              pad * 2 + rows * cell + (rows - 1) * gap), (245, 246, 250))
    d = ImageDraw.Draw(sheet)
    for i, (name, img, _) in enumerate(rendered):
        r, c = divmod(i, cols)
        x, y = pad + c * (cell + gap), pad + r * (cell + gap)
        thumb = img.resize((cell, cell), Image.LANCZOS)
        sheet.paste(thumb, (x, y), thumb)
        d.text((x + 4, y + cell + 4), name.split()[0], fill=(90, 92, 108))

    r, c = divmod(len(rendered), cols)
    x, y = pad + c * (cell + gap), pad + r * (cell + gap)
    d.text((x, y), "48px:", fill=(90, 92, 108))
    for i, (_, img, _) in enumerate(rendered[:6]):
        small = img.resize((48, 48), Image.LANCZOS)
        sheet.paste(small, (x + i * 52, y + 22), small)

    sheet_path = OUT_DIR / "contact_sheet.png"
    sheet.save(sheet_path)
    print(f"\n拼图: {sheet_path.relative_to(ROOT)}  ({sheet.size[0]}x{sheet.size[1]})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

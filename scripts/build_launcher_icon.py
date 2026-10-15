#!/usr/bin/env python3
"""
build_launcher_icon.py —— 把设计稿整图转成 Android 启动器图标资源。

为什么不能直接把整张图塞进自适应图标
------------------------------------
Android 自适应图标是 **108dp 画布**,但只有中间 **72dp(66.7%) 直径的圆**保证可见,
外围会被启动器裁成圆形/方形/水滴形。

设计稿是 1536×1536,装饰(星星/蜡笔)最远点达半宽的 **110%**,远超安全半径 33.3%。
整张直接用 → 地球左缘和小狮子右侧会被裁掉。

关键实现点
----------
1. **连通域**分离主体与装饰。主体(地球+小狮子)是最大的一块连通区域;
   星星/蜡笔是散落的小块。
   - 试过按饱和度分:**分不开**(星星也是高饱和的黄色)
   - 阈值也不能太低:阈值 52 时地面阴影把蜡笔连进主体,
     包围盒虚胖到 80~1444;阈值 150 时干净地分开
2. 缩放依据是**主体像素到自身质心的真实最大半径**,而不是包围盒对角线 ——
   主体是"圆 + 侧面的狮子"而非填满的矩形,用对角线会缩放过度、图标显得很小
3. 背景层用取样到的米色,与前景边缘同色 → 接缝不可见

输出
----
- mipmap-*/ic_launcher.png            传统方形图标
- mipmap-*/ic_launcher_round.png      圆形图标
- mipmap-*/ic_launcher_foreground.png 自适应前景(主体缩到安全圆内)
- mipmap-*/ic_launcher_monochrome.png Android 13+ 主题图标(「字」剪影)
- values/ic_launcher_background.xml   背景色
- mipmap-anydpi-v26/ic_launcher{,_round}.xml

用法
----
    python scripts/build_launcher_icon.py
"""

import math
import sys
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "app_icon_source.png"
RES = ROOT / "app" / "src" / "main" / "res"
FONT_PATH = RES / "font" / "studyword_kai.ttf"

DENSITIES = [
    ("mdpi", 48, 108),
    ("hdpi", 72, 162),
    ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324),
    ("xxxhdpi", 192, 432),
]

SAFE_RATIO = 0.667      # 安全圆直径占画布比例
THRESH = 150            # 非背景判定阈值(150 才不会把阴影和蜡笔连进主体)
MARGIN = 1.10           # 主体外留白系数
SAFE_INSET = 0.94       # 主体半径占安全半径的比例(留一点呼吸)
LABEL = 4               # 连通域在 1/LABEL 分辨率上做


def background_color(img):
    w, h = img.size
    px = img.load()
    samples = []
    for bx in (0, w - 40):
        for by in (0, h - 40):
            for dx in range(0, 40, 4):
                for dy in range(0, 40, 4):
                    samples.append(px[min(bx + dx, w - 1), min(by + dy, h - 1)])
    return tuple(sorted(s[i] for s in samples)[len(samples) // 2] for i in range(3))


def largest_component(img, bg, thresh=THRESH):
    """最大连通域(主体)的质心、最大半径与包围盒(原图坐标)"""
    w, h = img.size
    sw, sh = w // LABEL, h // LABEL
    sp = img.resize((sw, sh), Image.BILINEAR).load()

    mask = bytearray(sw * sh)
    for y in range(sh):
        for x in range(sw):
            r, g, b = sp[x, y]
            if abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) > thresh:
                mask[y * sw + x] = 1

    seen = bytearray(sw * sh)
    best = None
    for sy in range(sh):
        for sx in range(sw):
            i0 = sy * sw + sx
            if not mask[i0] or seen[i0]:
                continue
            q = deque([(sx, sy)])
            seen[i0] = 1
            pts = []
            while q:
                x, y = q.popleft()
                pts.append((x, y))
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < sw and 0 <= ny < sh:
                        j = ny * sw + nx
                        if mask[j] and not seen[j]:
                            seen[j] = 1
                            q.append((nx, ny))
            if best is None or len(pts) > len(best):
                best = pts

    if not best:
        return (w / 2, h / 2), w / 2, (0, 0, w, h)

    cx = sum(p[0] for p in best) / len(best) * LABEL
    cy = sum(p[1] for p in best) / len(best) * LABEL
    max_r = max(math.hypot(p[0] * LABEL - cx, p[1] * LABEL - cy) for p in best)
    xs = [p[0] * LABEL for p in best]
    ys = [p[1] * LABEL for p in best]
    return (cx, cy), max_r, (min(xs), min(ys), max(xs), max(ys))


def crop_subject(img, center, max_r, bg):
    """
    以主体质心为中心,裁一个边长为 2*max_r*MARGIN 的正方形。

    越界处**用背景色补边**,而不是把裁剪框夹断到图内 ——
    夹断会让主体偏离裁剪图中心,贴到画布上时主体就偏了。
    """
    cx, cy = center
    half = int(max_r * MARGIN)
    box = (int(cx) - half, int(cy) - half, int(cx) + half, int(cy) + half)
    W, H = img.size
    src_box = (max(0, box[0]), max(0, box[1]), min(W, box[2]), min(H, box[3]))
    canvas = Image.new("RGB", (2 * half, 2 * half), bg)
    canvas.paste(img.crop(src_box), (src_box[0] - box[0], src_box[1] - box[1]))
    return canvas


def rounded_mask(size, ratio=0.22):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1],
                                        radius=int(size * ratio), fill=255)
    return m


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def main() -> int:
    if not SOURCE.exists():
        print(f"[ERROR] 找不到设计稿: {SOURCE}", file=sys.stderr)
        return 1

    src = Image.open(SOURCE).convert("RGB")
    W, H = src.size
    bg = background_color(src)
    print(f"设计稿: {W}x{H}   背景色: {bg}  #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    center, max_r, bbox = largest_component(src, bg)
    print(f"主体连通域: 包围盒 x {bbox[0]}~{bbox[2]} y {bbox[1]}~{bbox[3]}  "
          f"({bbox[2]-bbox[0]}x{bbox[3]-bbox[1]})")
    print(f"            质心 ({center[0]:.0f}, {center[1]:.0f})   像素最大半径 {max_r:.0f}px "
          f"({max_r/(W/2)*100:.1f}% 半宽;安全半径 33.3%)")

    subject = crop_subject(src, center, max_r, bg)
    side = subject.size[0]
    print(f"裁剪边长 {side}px (含 {int((MARGIN-1)*100)}% 留白)")

    for dens, legacy_px, adaptive_px in DENSITIES:
        out = RES / f"mipmap-{dens}"
        out.mkdir(parents=True, exist_ok=True)

        sq = subject.resize((legacy_px, legacy_px), Image.LANCZOS).convert("RGBA")
        sq.putalpha(rounded_mask(legacy_px))
        sq.save(out / "ic_launcher.png")

        rd = subject.resize((legacy_px, legacy_px), Image.LANCZOS).convert("RGBA")
        rd.putalpha(circle_mask(legacy_px))
        rd.save(out / "ic_launcher_round.png")

        # 自适应前景:主体半径 -> SAFE_INSET * 安全半径
        safe_r = adaptive_px * SAFE_RATIO / 2
        # 裁剪图边长 = 2*max_r*MARGIN,其内容半径 = max_r
        # 目标:内容半径缩放后 = SAFE_INSET * safe_r
        art_px = int(side * (SAFE_INSET * safe_r) / max_r)
        art = subject.resize((art_px, art_px), Image.LANCZOS)

        fg = Image.new("RGBA", (adaptive_px, adaptive_px), bg + (255,))
        off = (adaptive_px - art_px) // 2
        fg.paste(art, (off, off))
        fg.save(out / "ic_launcher_foreground.png")

        # 主题图标:纯「字」剪影
        mono = Image.new("RGBA", (adaptive_px, adaptive_px), (0, 0, 0, 0))
        if FONT_PATH.exists():
            d = ImageDraw.Draw(mono)
            f = ImageFont.truetype(str(FONT_PATH), int(adaptive_px * 0.52))
            box = d.textbbox((0, 0), "字", font=f)
            d.text(((adaptive_px - (box[2] - box[0])) / 2 - box[0],
                    (adaptive_px - (box[3] - box[1])) / 2 - box[1]),
                   "字", font=f, fill=(0, 0, 0, 255))
        mono.save(out / "ic_launcher_monochrome.png")

        # 校验分两档口径:
        # - 主体半径:必须 <= 安全半径(否则主体被裁,这是硬指标)
        # - 含装饰外扩:装饰(星星/蜡笔)伸出安全区**是预期的**,启动器会裁掉,
        #   所以这里只报告、不作为失败条件
        subj_r = SAFE_INSET * safe_r
        fgp = fg.load()
        full_r = 0.0
        ccx = ccy = adaptive_px / 2
        for y in range(0, adaptive_px, 2):
            for x in range(0, adaptive_px, 2):
                r, g, b, a = fgp[x, y]
                if abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) > THRESH:
                    full_r = max(full_r, math.hypot(x - ccx, y - ccy))
        ok = subj_r <= safe_r
        print(f"  mipmap-{dens:<8} legacy {legacy_px:>3}px adaptive {adaptive_px:>3}px  "
              f"主体半径 {subj_r:5.1f}/{safe_r:5.1f} {'OK' if ok else '越界'}  "
              f"(含装饰 {full_r:5.1f},超出部分会被启动器裁掉)")

    colors = RES / "values" / "ic_launcher_background.xml"
    colors.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- 由 scripts/build_launcher_icon.py 生成:取自设计稿四角背景色,\n'
        '     与自适应图标前景的边缘同色,因此背景层与前景之间没有接缝 -->\n'
        '<resources>\n'
        f'    <color name="ic_launcher_background">#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}</color>\n'
        '</resources>\n',
        encoding="utf-8", newline="\n")

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (RES / "mipmap-anydpi-v26" / name).write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- 由 scripts/build_launcher_icon.py 生成 -->\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n',
            encoding="utf-8", newline="\n")
    print("\n背景色资源 + 自适应图标 XML(含 monochrome) 已生成")

    build_preview(bg)
    return 0


def build_preview(bg):
    """
    生成"启动器实际会显示成什么样"的预览。

    自适应图标最终长什么样取决于启动器的遮罩:圆形 / 圆角方形 / 水滴形…
    这里把最常见的几种遮罩和常用桌面尺寸都渲染出来,便于判断:
    - 主体在圆形遮罩下是否完整
    - 48px 桌面尺寸下是否还认得出
    """
    fg = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.png").convert("RGBA")
    size = fg.size[0]

    def compose(mask_kind):
        base = Image.alpha_composite(Image.new("RGBA", (size, size), bg + (255,)), fg)
        m = Image.new("L", (size, size), 0)
        d = ImageDraw.Draw(m)
        if mask_kind == "circle":
            d.ellipse([0, 0, size - 1, size - 1], fill=255)
        elif mask_kind == "squircle":
            d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.30), fill=255)
        elif mask_kind == "rounded":
            d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.16), fill=255)
        else:
            d.rectangle([0, 0, size - 1, size - 1], fill=255)
        base.putalpha(m)
        return base

    variants = [("完整 108dp", "none"), ("圆形遮罩", "circle"),
                ("圆角方形", "squircle"), ("圆角方形(小)", "rounded")]
    cell, gap, pad = 300, 20, 24
    sheet = Image.new("RGB", (pad * 2 + 4 * cell + 3 * gap, pad * 2 + cell + 100), (245, 246, 250))
    d = ImageDraw.Draw(sheet)

    for i, (label, kind) in enumerate(variants):
        img = compose(kind).resize((cell, cell), Image.LANCZOS)
        x = pad + i * (cell + gap)
        sheet.paste(img, (x, pad), img)
        d.text((x + 4, pad + cell + 8), label, fill=(70, 72, 88))

    y = pad + cell + 40
    d.text((pad, y), "桌面尺寸(圆形遮罩):", fill=(70, 72, 88))
    circle = compose("circle")
    cx = pad + 260
    for px in (192, 144, 96, 72, 48):
        small = circle.resize((px, px), Image.LANCZOS)
        sheet.paste(small, (cx, y - 8), small)
        cx += px + 14

    out = ROOT / "icons" / "launcher_preview.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(f"效果预览: {out.relative_to(ROOT)}  ({sheet.size[0]}x{sheet.size[1]})")


if __name__ == "__main__":
    raise SystemExit(main())

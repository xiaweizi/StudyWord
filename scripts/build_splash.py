#!/usr/bin/env python3
"""
build_splash.py —— 把设计给的 3:4 开机插画适配成手机屏幕比例。

为什么需要这个脚本
------------------
设计给的成图是 **1024×1360(3:4)**，而手机屏幕是 **1260×2720(约 9:19.5)**。

- 直接 `centerCrop`:图片要放大 2 倍才能填满高度,宽度变成 2048px 而屏幕只有 1260px
  → **左右各裁掉 19%(合计 38% 宽度)**,狮子和「字」被切掉,看起来"显示很不完整"。
- 直接 `fitCenter`:原画完整了,但上下各留 523px 空条,很难看。

本脚本的解法:**按图像自身的边缘像素向外延展**(edge replication + 缓慢淡出到整体底色),
把画布补到比屏幕更高的比例,再交给 `centerCrop`。这样:
- 原画 **100% 完整显示**(不会被裁)
- 屏幕被铺满,没有空条
- 延展区域的颜色取自图像边缘,**接缝不可见**(第一行延展像素 = 原图边缘行)

输出同时也做成 WebP:开机图是整屏大图,PNG 会有 2~3 MB,
而插图没有透明通道,WebP 质量 88 体积只有约十分之一。

用法
----
    python scripts/build_splash.py
"""

import sys
from pathlib import Path

from PIL import Image

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
# 原画保留在 design/ 下(不参与打包),避免以后想改尺寸时找不到源图
SOURCE = ROOT / "design" / "splash_intro_source.png"
OUT_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
OUT_WEBP = OUT_DIR / "splash_intro.webp"

# 目标宽高比(宽/高)。取 0.44 比本机屏幕 0.463 更"瘦"一点,
# 这样 centerCrop 只会裁到延展出来的底色,不会碰到原画。
TARGET_ASPECT = 0.44

# 延展区域向整体底色淡出的比例(0=完全复刻边缘像素,1=完全变成纯底色)
FADE = 0.75


def edge_extend(img: Image.Image, extra_top: int, extra_bottom: int) -> Image.Image:
    """
    在上下各延展若干像素:
    - 复刻最外一行/列的像素(保证接缝处连续,看不出边界)
    - 随距离缓慢淡出到整张图的中位底色(避免远处出现"竖条纹")
    """
    w, h = img.size
    px = img.load()

    # 以整幅图的中位色作为"淡出终点",比用某个角落更稳
    median = tuple(sorted(c[i] for c in img.getdata() for i in ()) ) if False else None
    # 直接算每通道中位数(样本抽样,避免大图慢)
    step = max(1, (w * h) // 20000)
    samples = [img.getpixel((x, y)) for y in range(0, h, max(1, h // 100)) for x in range(0, w, step)]
    base = tuple(int(sum(s[i] for s in samples) / len(samples)) for i in range(3))

    out = Image.new("RGB", (w, h + extra_top + extra_bottom))
    out.paste(img, (0, extra_top))

    op = out.load()
    for y in range(extra_top):
        t = (extra_top - y) / max(1, extra_top) * FADE          # 0 在接缝,→FADE 在最外端
        src_y = 0
        for x in range(w):
            c = px[x, src_y]
            op[x, y] = tuple(int(c[i] * (1 - t) + base[i] * t) for i in range(3))

    for y in range(extra_bottom):
        t = (y + 1) / max(1, extra_bottom) * FADE
        src_y = h - 1
        for x in range(w):
            c = px[x, src_y]
            op[x, extra_top + h + y] = tuple(int(c[i] * (1 - t) + base[i] * t) for i in range(3))

    return out


def main() -> int:
    if not SOURCE.exists():
        print(f"[ERROR] 找不到原画: {SOURCE}", file=sys.stderr)
        return 1

    img = Image.open(SOURCE).convert("RGB")
    w, h = img.size
    print(f"原画: {w}x{h}  宽高比 {w/h:.3f}")

    target_h = int(round(w / TARGET_ASPECT))
    if target_h <= h:
        print(f"[ERROR] 目标高度 {target_h} 不大于原画高度 {h},比例设置有问题", file=sys.stderr)
        return 1
    total_pad = target_h - h
    extra_top = total_pad // 2
    extra_bottom = total_pad - extra_top
    print(f"目标画布: {w}x{target_h} (比例 {TARGET_ASPECT})  上延展 {extra_top}px, 下延展 {extra_bottom}px")

    out = edge_extend(img, extra_top, extra_bottom)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out.save(OUT_WEBP, format="WEBP", quality=88, method=6)
    size_kb = OUT_WEBP.stat().st_size / 1024
    print(f"输出: {OUT_WEBP.relative_to(ROOT)}  {size_kb:.0f} KB  ({out.size[0]}x{out.size[1]})")

    # 顺带报告:本机屏幕(1260x2720)下 centerCrop 会裁多少、是否只裁到延展区
    sw, sh = 1260, 2720
    scale = max(sw / out.size[0], sh / out.size[1])
    disp_w, disp_h = out.size[0] * scale, out.size[1] * scale
    crop_v = (disp_h - sh) / 2
    print(f"\n在 1260x2720 屏幕上 centerCrop: 放大 {scale:.3f} 倍 → {disp_w:.0f}x{disp_h:.0f}")
    print(f"  上下各裁 {crop_v:.0f}px;延展区共 {total_pad}px(上下各约 {extra_top}px)"
          f" → {'只裁到延展区,原画完整 ✓' if crop_v <= extra_top else '**会裁到原画!需加大延展**'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

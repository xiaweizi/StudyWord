#!/usr/bin/env python3
"""
build_kai_font.py —— 从 LXGW 文楷的 webfont 分片里,生成只含本应用所需汉字的楷体子集。

为什么要有这个脚本
------------------
1. **必须子集化**。完整 LXGW 文楷 Regular 是 24 MB,而整个 release APK 才 2.5 MB。
   本应用只需要字库里出现过的字,子集后只有几百 KB。
2. **必须改名**。LXGW 文楷采用 SIL OFL 1.1 许可,其中包含"保留字体名"(Reserved Font Name)
   条款:修改过的衍生作品**不得继续使用原字体名**。子集化即修改,所以输出字体
   改名为 StudyWordKai,并在 licenses/ 下随附 OFL 原文。
3. **必须可复现**。字体是二进制资产,不能"手工弄一个丢进去";本脚本记录了完整的
   来源、版本、字符集来源和改名规则,任何人可重跑。

输入 / 输出
-----------
输入:`%TEMP%/kai_font/package/**/lxgwwenkailite-regular-subset-*.woff2`
      (来自 npm 包 lxgw-wenkai-lite-webfont@1.7.0,国内可通过 registry.npmmirror.com 获取)
输出:`app/src/main/res/font/studyword_kai.ttf`

用法
----
    python scripts/build_kai_font.py
"""

import glob
import json
import os
import re
import sys
import tempfile
from pathlib import Path

from fontTools import subset
from fontTools.merge import Merger
from fontTools.ttLib import TTFont

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
PACKAGE_GLOB = str(Path(tempfile.gettempdir()) / "kai_font" / "package" / "**" / "lxgwwenkailite-regular-subset-*.woff2")
OUT = ROOT / "app" / "src" / "main" / "res" / "font" / "studyword_kai.ttf"

NEW_FAMILY = "StudyWordKai"
NEW_FULL = "StudyWordKai Regular"
NEW_PS = "StudyWordKai-Regular"

# 额外保留的字符:ASCII(英文卡/数字)、常用中英文标点、以及应用界面可能用到的符号
EXTRA = (
    "".join(chr(c) for c in range(0x20, 0x7F))          # 可打印 ASCII
    + "　、。〈〉《》「」『』【】〔〕・ー—…‥“”‘’·×÷±°"
    + "←↑→↓★☆♥♪✓✗⚠"
)


def collect_chars() -> set[str]:
    """要保留的字符 = 中文字库全部汉字 + 词组/例句里出现过的字 + 额外符号。"""
    chars: set[str] = set(EXTRA)

    sets_path = ROOT / "app" / "src" / "main" / "assets" / "character_sets.json"
    data = json.loads(sets_path.read_text(encoding="utf-8"))
    for key in ("easy", "medium", "hard"):
        for item in data.get(key, []):
            if isinstance(item, str):
                chars.update(item)
                continue
            chars.update(str(item.get("char", "")))
            # 词组 / 例句里出现的字也要有字形,否则以后把楷体用在词组上会缺字
            for w in item.get("words") or []:
                chars.update(str(w.get("word", "")))
            for e in item.get("examples") or []:
                chars.update(str(e.get("sentence", "")))
    chars.discard("")
    return chars


def subset_to(font: TTFont, text: str) -> TTFont:
    opts = subset.Options()
    opts.layout_features = []      # 楷体用于显示,不需要 OpenType 排版特性
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.recalc_bounds = True
    opts.drop_tables += ["DSIG"]
    sub = subset.Subsetter(options=opts)
    sub.populate(text=text)
    sub.subset(font)
    return font


def rename(font: TTFont) -> None:
    """
    OFL 的保留字体名条款:衍生作品不得使用原字体名。
    这里把所有包含 LXGW / WenKai / 文楷 的名字记录改掉,并注明来源与许可。
    """
    name_table = font["name"]
    copyright_text = (
        "StudyWordKai: a character subset derived from LXGW WenKai Lite (霞鹜文楷 Lite) "
        "by LXGW / chawyehsu, licensed under SIL OFL 1.1. "
        "Renamed per the OFL Reserved Font Name clause. See licenses/OFL-LXGWWenKai.txt."
    )
    for record in name_table.names:
        try:
            current = record.toUnicode()
        except Exception:
            continue
        if record.nameID == 0:
            new = copyright_text
        elif record.nameID == 1:
            new = NEW_FAMILY
        elif record.nameID == 2:
            new = "Regular"
        elif record.nameID == 3:
            new = "StudyWordKai-1.0"
        elif record.nameID == 4:
            new = NEW_FULL
        elif record.nameID == 6:
            new = NEW_PS
        elif record.nameID in (16, 17):
            new = NEW_FAMILY if record.nameID == 16 else "Regular"
        else:
            continue
        record.string = new.encode(record.getEncoding()) if record.getEncoding() else new


def main() -> int:
    files = sorted(glob.glob(PACKAGE_GLOB, recursive=True))
    if not files:
        print("[ERROR] 找不到字体分片。请先下载 npm 包 lxgw-wenkai-lite-webfont@1.7.0", file=sys.stderr)
        print("        并解压到 %TEMP%/kai_font/package/", file=sys.stderr)
        return 1
    print(f"字体分片: {len(files)} 个")

    chars = collect_chars()
    text = "".join(sorted(chars))
    print(f"字符集  : {len(chars)} 个(字库 3000 字 + 词组/例句用字 + ASCII/标点)")

    tmpdir = Path(tempfile.mkdtemp(prefix="kai_subset_"))
    kept: list[str] = []
    for i, path in enumerate(files, 1):
        try:
            font = TTFont(path)
            if "cmap" not in font:
                font.close()
                continue
            subset_to(font, text)
            if len(font.getBestCmap()) == 0:
                font.close()
                continue
            out = tmpdir / f"part_{i:03d}.ttf"
            font.save(out)
            font.close()
            kept.append(str(out))
        except Exception as exc:  # 单个分片失败不该中断整体
            print(f"  [warn] {os.path.basename(path)}: {exc}", file=sys.stderr)

    print(f"命中的分片: {len(kept)} 个")
    if not kept:
        print("[ERROR] 没有任何分片包含目标字符", file=sys.stderr)
        return 1

    print("合并中…")
    # Merger 接受"文件路径"或 file-like 对象,不接受 TTFont 实例
    merged = Merger().merge(kept)
    subset_to(merged, text)
    rename(merged)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    merged.save(OUT)
    merged.close()

    size_kb = OUT.stat().st_size / 1024
    final = TTFont(OUT)
    print(f"\n输出 : {OUT.relative_to(ROOT)}  ({size_kb:.0f} KB)")
    print(f"字形数: {len(final.getBestCmap())}")
    print(f"家族名: {final['name'].getDebugName(1)}")
    final.close()

    if size_kb > 3000:
        print("[warn] 字体超过 3 MB,考虑进一步裁剪字符集", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

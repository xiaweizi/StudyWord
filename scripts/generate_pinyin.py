#!/usr/bin/env python3
"""
generate_pinyin.py

为字库生成离线拼音表 `app/src/main/assets/pinyin_table.json`。

为什么要有这张表(v1.5.0)
------------------------
旧实现用 `android.icu.text.Transliterator("Han-Latin/Names")` 在运行时推导拼音,有三个问题:

1. **在 Android 8/9(API 26~28)上会崩溃** —— `Transliterator#getInstance` /
   `#transliterate` 到 API 29 才进入公开 API 面(Android Lint [NewApi]),
   而应用的 minSdk 是 26。拼音在首屏就要为 3000 个字生成,等于启动即崩。
2. **多音字易错** —— `Han-Latin/Names` 是人名转写规则集,对「长/行/重/教」这类字常取错音。
   旧代码靠一张 12 条的人工 override 表补救,覆盖远远不够。
3. **首启卡顿** —— 3000 次 ICU 转写 + 数千次正则编译都在主线程。

改成"构建期生成、运行期查表"后:任意 API 版本都能用、读音质量更高、首启零开销。

读音规则
--------
用 pypinyin 的**默认读音**(基于词频的常用读音),保留声调符号,与卡片展示一致。
例如:长→cháng、了→le、行→xíng、教→jiào。

用法
----
    python scripts/generate_pinyin.py            # 生成/覆盖拼音表
    python scripts/generate_pinyin.py --dry-run  # 只统计,不写文件
"""

import argparse
import json
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

try:
    from pypinyin import pinyin, Style
except ImportError:
    print("[ERROR] 需要 pypinyin:python -m pip install pypinyin", file=sys.stderr)
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parent.parent
CHARACTER_SETS = ROOT / "app" / "src" / "main" / "assets" / "character_sets.json"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "pinyin_table.json"

DIFFICULTIES = ("easy", "medium", "hard")


def load_hanzi() -> list[str]:
    """按字库原始顺序取出全部汉字(顺序仅用于日志/统计,拼音表本身与顺序无关)。"""
    data = json.loads(CHARACTER_SETS.read_text(encoding="utf-8"))
    result: list[str] = []
    for key in DIFFICULTIES:
        for item in data.get(key, []):
            if isinstance(item, str):
                result.append(item.strip())
            elif isinstance(item, dict):
                ch = str(item.get("char", "")).strip()
                if ch:
                    result.append(ch)
    return result


def to_reading(hanzi: str) -> str:
    """取单字读音(带声调符号),失败时返回空串。"""
    try:
        parts = pinyin(hanzi, style=Style.TONE, strict=False, errors="ignore")
    except Exception:
        return ""
    if not parts or not parts[0]:
        return ""
    return "".join(parts[0]).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只打印统计,不写文件")
    args = parser.parse_args()

    hanzi_list = load_hanzi()
    if not hanzi_list:
        print("[ERROR] 未能从字库读取到任何汉字", file=sys.stderr)
        return 1

    unique: dict[str, str] = {}
    missing: list[str] = []
    for hanzi in hanzi_list:
        if hanzi in unique:
            continue
        reading = to_reading(hanzi)
        if reading:
            unique[hanzi] = reading
        else:
            missing.append(hanzi)

    # 与既有 pinyin_table.json 合并(保留人工补充的条目)
    merged: dict[str, str] = {}
    if OUTPUT.exists():
        try:
            merged = json.loads(OUTPUT.read_text(encoding="utf-8"))
        except Exception:
            merged = {}
    merged.update(unique)

    # 按 key 排序,保证多次运行产生完全相同的文件(便于 git diff)
    ordered = {k: merged[k] for k in sorted(merged.keys())}

    print(f"字库汉字数(含重复)   : {len(hanzi_list)}")
    print(f"去重后                 : {len(unique) + len(missing)}")
    print(f"成功生成读音           : {len(unique)}")
    print(f"无读音(将回落 ICU)     : {len(missing)}")
    if missing:
        print("  缺读音的字:", "".join(missing[:50]))
    print(f"合并既有表后总条目     : {len(ordered)}")

    samples = ["长", "了", "行", "教", "重", "乐", "地", "天"]
    print("抽样:", ", ".join(f"{c}→{ordered.get(c, '?')}" for c in samples))

    if args.dry_run:
        print("[dry-run] 未写文件")
        return 0

    OUTPUT.write_text(
        json.dumps(ordered, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"已写入 {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
audit_content_coverage.py —— 内容完整性审计:词组 / 例词 / 例句

回答"到底哪些字/词没有内容"这一个问题,输出**可逐条核对**的清单,
而不是一句"覆盖率 XX%"。

检查项
------
中文(character_sets.json)
- 每个字是否有 words(词组)与 examples(例句)
- 词组/例句是否缺拼音
- 词组的词是否**包含该字**(否则就是张冠李戴)
- 例句是否**包含该字**
- 按 easy 1~600 / easy 601~1200 / medium / hard 分段统计

英文(english_sets.json)
- 字母:letter / uppercase / lowercase / phonetic / exampleWord / exampleWordChinese 是否齐全非空
- 单词:word / phonetic / chineseMeaning / exampleSentence /
  exampleSentenceTranslation / difficulty 是否齐全非空
- 例句是否包含该单词

白名单
------
KNOWN_NO_WORDS 里的字是**人工确认过"没有合适幼儿词组"**的
(如"死/杀/血/伤"这类不适合幼儿组词的),不算缺失。

用法
----
    python scripts/audit_content_coverage.py            # 摘要
    python scripts/audit_content_coverage.py --list      # 附完整缺失清单
    python scripts/audit_content_coverage.py --list --seg easy601
"""

import json
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
CHAR_FILE = ROOT / "app" / "src" / "main" / "assets" / "character_sets.json"
EN_FILE = ROOT / "app" / "src" / "main" / "assets" / "english_sets.json"

# 人工确认"没有合适幼儿词组"的字(不适合给 4-6 岁组词的)
KNOWN_NO_WORDS = set(
    "毋弗奴囚弘死杀血伤奸邪伪妄讼刑亦岂汝迄忖讳迂兆夷旬旨廷吏玑吁讽牟"
    "伦邦仲贞妃执劣巩朽匈"
    "妓坟隶怖劫歼役扼"
)

LETTER_FIELDS = ["letter", "uppercase", "lowercase", "phonetic",
                 "exampleWord", "exampleWordChinese"]
WORD_FIELDS = ["word", "phonetic", "chineseMeaning", "exampleSentence",
               "exampleSentenceTranslation", "difficulty"]


def ch(it):
    return it["char"] if isinstance(it, dict) else it


def audit_chinese(show_list=False, only_seg=None):
    data = json.loads(CHAR_FILE.read_text(encoding="utf-8"))
    easy, med, hard = data["easy"], data["medium"], data["hard"]

    segs = [
        ("easy 1~600", easy[:600], True),          # True = 已承诺覆盖
        ("easy 601~1200", easy[600:1200], True),
        ("medium 1~1000", med, False),
        ("hard 1~800", hard, False),
    ]

    print("=" * 78)
    print("中文:词组 / 例句覆盖")
    print("=" * 78)
    print(f"{'分段':<16}{'总数':>6}{'有词组':>8}{'有例句':>8}{'白名单':>8}{'待补':>8}  状态")
    print("-" * 78)

    total_missing = 0
    details = {}
    for name, arr, promised in segs:
        have_w = have_e = white = 0
        missing = []
        for it in arr:
            c = ch(it)
            w = it.get("words") if isinstance(it, dict) else None
            e = it.get("examples") if isinstance(it, dict) else None
            if w:
                have_w += 1
            if e:
                have_e += 1
            if not w:
                if c in KNOWN_NO_WORDS:
                    white += 1
                else:
                    missing.append(c)
        total_missing += len(missing)
        status = "已承诺并完成" if promised and not missing else (
            "**未开始**" if promised else "未承诺")
        print(f"{name:<16}{len(arr):>6}{have_w:>8}{have_e:>8}{white:>8}{len(missing):>8}  {status}")
        details[name] = (missing, arr)

    print("-" * 78)
    print(f"待补内容合计: {total_missing} 个字")
    print(f"白名单(确认无合适幼儿词组): {len(KNOWN_NO_WORDS)} 个字 —— 不算缺失")

    # 字段质量问题(只查已有内容的条目)
    print()
    print("已有内容的质量检查:")
    bad = []
    for name, arr in (("easy", easy), ("medium", med), ("hard", hard)):
        for it in arr:
            if not isinstance(it, dict):
                continue
            c = it["char"]
            for w in (it.get("words") or []):
                if not str(w.get("pinyin", "")).strip():
                    bad.append(f"{c}:词组「{w.get('word')}」缺拼音")
                if c not in str(w.get("word", "")):
                    bad.append(f"{c}:词组「{w.get('word')}」不含该字")
            for e in (it.get("examples") or []):
                if not str(e.get("pinyin", "")).strip():
                    bad.append(f"{c}:例句缺拼音")
                if c not in str(e.get("sentence", "")):
                    bad.append(f"{c}:例句「{e.get('sentence')}」不含该字")
    if bad:
        for b in bad[:20]:
            print(f"  !! {b}")
        print(f"  共 {len(bad)} 条问题")
    else:
        print("  词组/例句的拼音齐全、且都包含目标字 ✓")

    if show_list:
        print()
        for name, (missing, arr) in details.items():
            if only_seg and only_seg not in name.replace(" ", ""):
                continue
            if not missing:
                continue
            print(f"[{name}] 缺内容的 {len(missing)} 个字:")
            for i in range(0, len(missing), 40):
                print("  " + " ".join(missing[i:i + 40]))
    return total_missing


def audit_english(show_list=False):
    data = json.loads(EN_FILE.read_text(encoding="utf-8"))
    letters, words = data["letters"], data["words"]

    print()
    print("=" * 78)
    print("英文:字母 / 单词字段完整性")
    print("=" * 78)

    bad_letters = []
    for l in letters:
        blanks = [f for f in LETTER_FIELDS if not str(l.get(f, "")).strip()]
        if blanks:
            bad_letters.append((l.get("letter"), blanks))
    print(f"字母 {len(letters)} 个:")
    if bad_letters:
        for name, blanks in bad_letters:
            print(f"  !! {name} 缺字段: {blanks}")
    else:
        print(f"  全部 {len(LETTER_FIELDS)} 个字段(字母/大写/小写/音标/例词/例词中文)齐全非空 ✓")

    bad_words = []
    no_contain = []
    for w in words:
        blanks = [f for f in WORD_FIELDS if not str(w.get(f, "")).strip()]
        if blanks:
            bad_words.append((w.get("word"), blanks))
        elif w["word"].lower() not in w["exampleSentence"].lower():
            no_contain.append(w["word"])
    by_diff = {}
    for w in words:
        by_diff[w.get("difficulty")] = by_diff.get(w.get("difficulty"), 0) + 1
    print(f"单词 {len(words)} 个,难度分布 {by_diff}:")
    if bad_words:
        for name, blanks in bad_words:
            print(f"  !! {name} 缺字段: {blanks}")
    else:
        print(f"  全部 {len(WORD_FIELDS)} 个字段齐全非空 ✓")
    if no_contain:
        print(f"  !! 例句不含该词的: {no_contain}")
    else:
        print("  每条例句都包含该单词 ✓")

    if show_list and (bad_letters or bad_words):
        print("\n缺失明细:")
        for n, b in bad_letters + bad_words:
            print(f"  {n}: {b}")
    return len(bad_letters) + len(bad_words) + len(no_contain)


def main() -> int:
    show_list = "--list" in sys.argv
    only_seg = None
    for a in sys.argv:
        if a.startswith("--seg"):
            only_seg = a.split("=", 1)[1] if "=" in a else None

    miss_cn = audit_chinese(show_list, only_seg)
    probs_en = audit_english(show_list)

    print()
    print("=" * 78)
    print(f"结论:中文待补 {miss_cn} 字;英文问题 {probs_en} 处")
    if miss_cn == 0 and probs_en == 0:
        print("全部内容完整 ✓")
    print("=" * 78)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

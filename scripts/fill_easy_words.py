#!/usr/bin/env python3
"""
fill_easy_words.py

为 simple 难度的**前 100 个常用字**补齐词组与例句（就地写回 character_sets.json）。

为什么需要这个脚本(v1.5.0)
----------------------------
`generate_words.py` 有两个问题，导致"前 100 个字"里只有 56 个真正带上了词组：
1. 它按"前 N 个"切片（默认 `--limit 100`），而字典命中的字分散在别处；
2. 只处理 easy 数组的前 N 项，剩下 44 个字被写成了 `{"char":"X"}` **空对象**
   —— 既没词组也没例句，却把原来的字符串格式改写了一遍（纯 churn）。

本脚本的做法
------------
- 按**字**定位（而不是按下标切片），只处理字典里确实有内容的字；
- 拼音由 pypinyin 依据**上下文**生成（比逐字查表更准，如「萝卜」→ luó bo），
  不手写拼音，避免人工出错；
- **严格保持原有顺序**：只在既有条目上补字段，绝不插入/删除/重排。
  这一点很关键 —— v1.5.0 的进度迁移依赖"字库顺序与 v1.4.4 一致"，
  顺序一变，老用户的按位置 id 就会映射到别的字上；
- 幂等：已有词组的条目默认跳过（`--force` 可覆盖）。

用法
----
    python scripts/fill_easy_words.py            # 补齐缺口
    python scripts/fill_easy_words.py --dry-run  # 只报告，不写文件
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
    print("[ERROR] 需要 pypinyin：python -m pip install pypinyin", file=sys.stderr)
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parent.parent
JSON_PATH = ROOT / "app" / "src" / "main" / "assets" / "character_sets.json"

# ============================================================================
# 人工撰写的词组 + 例句（面向 3~6 岁幼儿识字，用全角标点）
# 拼音一律由 pypinyin 依据上下文生成，不在此手写，避免出错。
# ============================================================================
CONTENT: dict[str, dict] = {
    "乙": {"words": ["甲乙", "乙方", "乙等"], "examples": ["我按甲乙丙丁的顺序排队。"]},
    "了": {"words": ["来了", "好了", "走了"], "examples": ["天亮了，我们该起床了。"]},
    "力": {"words": ["力气", "用力", "力量"], "examples": ["我用力推开大门。"]},
    "又": {"words": ["又来", "又是", "又大又圆"], "examples": ["月亮又大又圆。"]},
    "入": {"words": ["入口", "进入", "加入"], "examples": ["请从入口进教室。"]},
    "几": {"words": ["几个", "几天", "几时"], "examples": ["你有几个好朋友？"]},
    "厂": {"words": ["工厂", "厂房", "厂里"], "examples": ["爸爸在工厂上班。"]},
    "刀": {"words": ["小刀", "菜刀", "剪刀"], "examples": ["妈妈用菜刀切菜。"]},
    "乃": {"words": ["乃是", "乃至", "乃父"], "examples": ["这本书乃是老师送的。"]},
    "丁": {"words": ["园丁", "丁香", "甲乙丙丁"], "examples": ["园丁在给花浇水。"]},
    "卜": {"words": ["萝卜", "胡萝卜", "占卜"], "examples": ["兔子最爱吃萝卜。"]},
    "个": {"words": ["一个", "个人", "个子"], "examples": ["我有一个小皮球。"]},
    "也": {"words": ["也是", "也好", "也许"], "examples": ["我也想去公园玩。"]},
    "于": {"words": ["于是", "等于", "关于"], "examples": ["三加二等于五。"]},
    "之": {"words": ["之前", "之后", "之间"], "examples": ["吃饭之前要洗手。"]},
    "么": {"words": ["什么", "怎么", "这么"], "examples": ["你在做什么呢？"]},
    "与": {"words": ["你与我", "参与", "与人为善"], "examples": ["我与你一起玩。"]},
    "已": {"words": ["已经", "而已", "已知"], "examples": ["我已经吃完饭了。"]},
    "工": {"words": ["工人", "工作", "手工"], "examples": ["我在做手工。"]},
    "己": {"words": ["自己", "知己", "己方"], "examples": ["我自己会穿衣服。"]},
    "门": {"words": ["大门", "门口", "开门"], "examples": ["请把门关上。"]},
    "及": {"words": ["及时", "以及", "及格"], "examples": ["我们要及时喝水。"]},
    "义": {"words": ["意义", "义务", "正义"], "examples": ["帮助别人是很有意义的事。"]},
    "才": {"words": ["才能", "刚才", "天才"], "examples": ["他刚才还在玩。"]},
    "万": {"words": ["一万", "万一", "千万"], "examples": ["天上有千万颗星星。"]},
    "干": {"words": ["干净", "干杯", "饼干"], "examples": ["我把手洗得很干净。"]},
    "士": {"words": ["士兵", "战士", "士气"], "examples": ["士兵站得很直。"]},
    "广": {"words": ["广场", "广大", "广告"], "examples": ["我们在广场上跑步。"]},
    "土": {"words": ["土地", "泥土", "土豆"], "examples": ["泥土里有小虫子。"]},
    "千": {"words": ["一千", "千万", "千米"], "examples": ["一千米有多远？"]},
    "久": {"words": ["很久", "长久", "好久"], "examples": ["好久不见！"]},
    "卫": {"words": ["卫生", "保卫", "卫士"], "examples": ["我们要讲卫生。"]},
    "习": {"words": ["学习", "练习", "习惯"], "examples": ["我每天练习写字。"]},
    "乡": {"words": ["家乡", "乡村", "老乡"], "examples": ["我的家乡有山有水。"]},
    "亡": {"words": ["亡羊补牢", "死亡", "逃亡"], "examples": ["亡羊补牢，还不算晚。"]},
    "凡": {"words": ["凡是", "平凡", "不凡"], "examples": ["凡是自己能做的事，就自己做。"]},
    "亿": {"words": ["一亿", "亿万", "一亿年"], "examples": ["一亿是很大的数。"]},
    "川": {"words": ["四川", "山川", "川流不息"], "examples": ["四川有可爱的大熊猫。"]},
    "丈": {"words": ["丈夫", "万丈", "方丈"], "examples": ["这座山好像有万丈高。"]},
    "尸": {"words": ["僵尸", "尸体", "尸骨"], "examples": ["万圣节有人扮成僵尸。"]},
    "亏": {"words": ["吃亏", "幸亏", "亏本"], "examples": ["幸亏今天带了雨伞。"]},
    "寸": {"words": ["一寸", "尺寸", "寸步"], "examples": ["这条绳子有一寸长。"]},
    "夕": {"words": ["夕阳", "除夕", "七夕"], "examples": ["夕阳把天空染红了。"]},
    "弓": {"words": ["弓箭", "拉弓", "弹弓"], "examples": ["他拉开弓射箭。"]},
}

# 处理范围:None = 整个简单档。
# 以前写死 1200,难度改档后简单档长度变了,写死的数字会让新增的字被漏掉 —— 改为动态。
TARGET_PREFIX = None
DIFFICULTY_KEYS = ("easy", "medium", "hard")

# 外部内容批次（人工/子代理撰写，键 → {words, examples}）。
# 与脚本内 CONTENT 合并；同名以外部文件为准（便于分批迭代）。
BATCH_FILES = ("words_content_batch2.json", "words_content_batch3.json",
                "words_content_batch4.json", "words_content_batch5.json",
                "words_content_batch6.json", "words_content_batch7.json",
                "words_content_batch8.json", "words_content_batch9.json",
                "words_content_batch10.json", "words_content_batch11.json")


def load_batches() -> dict[str, dict]:
    """加载并合并所有外部内容批次。"""
    merged: dict[str, dict] = {}
    for name in BATCH_FILES:
        path = ROOT / "scripts" / name
        if not path.exists():
            print(f"[warn] 内容批次不存在，跳过: {name}")
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            print(f"[ERROR] {name} 顶层必须是对象", file=sys.stderr)
            raise SystemExit(2)
        merged.update({k.strip(): v for k, v in data.items()})
        print(f"[info] 载入 {name}: {len(data)} 条")
    return merged


def validate(char: str, spec: dict) -> list[str]:
    """内容质量校验：词组/例句必须包含目标字（幼儿识字的关键）。"""
    problems: list[str] = []
    words = spec.get("words") or []
    examples = spec.get("examples") or []
    if not words:
        problems.append(f"{char}: 缺少词组")
    if not examples:
        problems.append(f"{char}: 缺少例句")
    for w in words:
        if char not in w:
            problems.append(f"{char}: 词组「{w}」不含目标字")
        if not (2 <= len(w) <= 4):
            problems.append(f"{char}: 词组「{w}」长度应在 2~4")
    for e in examples:
        if char not in e:
            problems.append(f"{char}: 例句「{e}」不含目标字")
    return problems


def reading(text: str) -> str:
    """按上下文取带声调的拼音，空格分隔。"""
    try:
        parts = pinyin(text, style=Style.TONE, strict=False, errors="ignore")
    except Exception:
        return ""
    return " ".join("".join(p) for p in parts if p).strip()


def char_of(item) -> str:
    return item.strip() if isinstance(item, str) else str(item.get("char", "")).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="只报告，不写文件")
    parser.add_argument("--force", action="store_true", help="覆盖已有词组的条目")
    args = parser.parse_args()

    raw = JSON_PATH.read_text(encoding="utf-8")
    data = json.loads(raw)

    content = dict(CONTENT)
    content.update(load_batches())

    # 内容质量门禁：词组/例句必须含目标字，否则拒绝写入
    all_problems: list[str] = []
    for ch, spec in content.items():
        all_problems.extend(validate(ch, spec))
    if all_problems:
        print(f"[ABORT] 内容校验未通过 {len(all_problems)} 处：", file=sys.stderr)
        for pr in all_problems[:20]:
            print(f"  - {pr}", file=sys.stderr)
        return 2
    print(f"[info] 内容校验通过（{len(content)} 个字的词条均含目标字）")

    # 改动前记录顺序指纹，写完必须一致
    order_before = [
        char_of(it) for k in DIFFICULTY_KEYS for it in data.get(k, [])
    ]

    easy = data.get("easy", [])
    limit = TARGET_PREFIX if TARGET_PREFIX is not None else len(easy)
    filled, already, not_in_dict, empty_fixed = [], [], [], []

    for idx in range(min(limit, len(easy))):
        item = easy[idx]
        ch = char_of(item)
        has_words = isinstance(item, dict) and item.get("words")
        if has_words and not args.force:
            already.append(ch)
            continue
        spec = content.get(ch)
        if not spec:
            not_in_dict.append(ch)
            continue

        words = [
            {"word": w, "pinyin": reading(w)} for w in spec["words"]
        ]
        examples = [
            {"sentence": s, "pinyin": reading(s)} for s in spec["examples"]
        ]
        easy[idx] = {"char": ch, "words": words, "examples": examples}
        filled.append(ch)
        if isinstance(item, dict) and not item.get("words"):
            empty_fixed.append(ch)

    order_after = [char_of(it) for k in DIFFICULTY_KEYS for it in data.get(k, [])]
    order_ok = order_before == order_after

    total_with = sum(
        1 for it in easy if isinstance(it, dict) and it.get("words")
    )

    print(f"简单档前 {limit} 字中原本有内容 : {len(already)}")
    print(f"本次补齐                        : {len(filled)}  -> {' '.join(filled)}")
    print(f"  其中原本是空对象的              : {len(empty_fixed)}")
    print(f"字典里没有(多数是已确认无合适幼儿词组的白名单)          : {len(not_in_dict)}"
          + (f" -> {' '.join(not_in_dict)}" if not_in_dict else ""))
    print(f"easy 中现共有词组的条目           : {total_with}")
    print(f"顺序指纹一致(关键!)              : {'是' if order_ok else '否 —— 已中止'}")

    if not order_ok:
        print("[ABORT] 顺序发生变化，拒绝写入（会影响老用户进度迁移）", file=sys.stderr)
        return 1

    if args.dry_run:
        print("[dry-run] 未写文件")
        return 0

    JSON_PATH.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"已写回 {JSON_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

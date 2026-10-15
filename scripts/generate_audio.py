#!/usr/bin/env python3
"""
generate_audio.py —— 为字/词/例句生成内置语音包(离线播放,不依赖系统 TTS)

为什么要做这个
--------------
用户在 HarmonyOS 手机上完全没有发音:该设备的安卓兼容层不提供 TTS 引擎
(`TextToSpeech.getEngines()` 返回空,系统"文本转语音"设置页也卡在"正在检查")。
鸿蒙自己的语音是 ArkTS API,安卓 APK 调不到 —— 这是平台边界,代码绕不过去。

但实测那台设备上 **MediaPlayer 播放音频文件是正常的**(答题音效有声),
所以把语音预先生成成音频文件打进 APK,就能在**任何设备**上都有发音。

技术选型
--------
- 语音合成:**edge-tts**(微软免费神经网络语音),中文音色自然,有儿童向音色
- 编码:**MP3 40kbps 单声道 22050Hz**(libmp3lame)

  ⚠️ **为什么最终选 MP3 而不是更"先进"的编码** —— 都是实测量出来的,不是偏好:

  | 编码 | 4 条样例合计 | 相对 Opus | 全集预估 |
  |---|---|---|---|
  | Opus 24k | 12,871 B | 100% | 6.3 MB |
  | **MP3 32k** | 11,562 B | **89%** | **5.7 MB** |
  | MP3 40k | ~14,400 B | ~112% | ~7.1 MB |
  | Vorbis 24k | 23,278 B | 180% | 11.5 MB |

  1. **兼容性**:目标设备(HarmonyOS 的安卓兼容层)上 **Ogg/Opus 播不出声** ——
     同一台设备游戏答题音效(`res/raw/*.ogg`)是 Vorbis 且有声音。
     MP3 是 Android 从 1.0 起就硬件解码的格式,兼容面比 Vorbis 更广。
  2. **体积**:MP3 反而比 Opus 小(Opus 短音频的容器开销占比高),
     Vorbis 则是 Opus 的近 2 倍 —— 低码率下 Opus 效率优势明显。
  3. **成本**:edge-tts 原生输出就是 mp3,不需要额外一代转码,音质少一次损失。
- 文件命名:key 的 **CRC32**,形如 `k1a2b3c4.mp3`。
  - 纯 ASCII,不受资源名限制
  - 与内容一一对应且**稳定**:新增内容不会改动已有文件名
  - 构建时断言**无哈希碰撞**,碰撞就报错中止(不会静默覆盖)
- 播放:走 assets + MediaPlayer(需在 build.gradle 里对 ogg 关压缩)

键的约定(与 App 侧 AudioClips 保持一致,改动必须两边同步)
------------------------------------------------------
    c:<汉字>      中文单字
    w:<中文>      中文词组
    e:<中文>      中文例句
    z:<中文>      其它中文文本(中文意思 / 例词中文 / 例句翻译)——
                  合到一个命名空间,相同文本能自动去重
    l:<字母>      英文字母
    n:<英文>      英文单词 / 字母的例词(同一命名空间,可去重)
    s:<英文句>    英文例句

用法
----
    python scripts/generate_audio.py                 # 全量生成(断点续跑)
    python scripts/generate_audio.py --limit 12      # 只生成前 12 条(验证链路)
    python scripts/generate_audio.py --jobs 12       # 并发数(默认 8)
    python scripts/generate_audio.py --verify-only   # 只校验已生成的文件
"""

import argparse
import asyncio
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
CHAR_FILE = ROOT / "app" / "src" / "main" / "assets" / "character_sets.json"
EN_FILE = ROOT / "app" / "src" / "main" / "assets" / "english_sets.json"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "audio"
MANIFEST = ROOT / "scripts" / "audio_manifest.json"

VOICE_ZH = "zh-CN-XiaoyiNeural"     # 年轻女声,适合儿童内容
VOICE_EN = "en-US-AnaNeural"        # 儿童音色
RATE_ZH = "-8%"                     # 略慢一点,便于幼儿跟读
RATE_EN = "-4%"
MP3_BITRATE = "40k"

# 覆盖范围:None = **整个简单档**。
# 这里曾写死 600 —— 那是早期"只有前 600 字有词组"时的值,后来内容补到 1200 字、
# 难度改档后简单档又变成 1167 字,写死的数字导致**后 567 个字完全没有发音**
# (那台鸿蒙手机没有 TTS 引擎,这些字点下去是静音的)。改为动态,以后不会再漏。
CHAR_COVER = None
# 英文单词:None = 全部难度。原先只做 EASY,导致中等/困难共 100 个词没有发音。
EN_WORD_DIFFICULTY = None

LETTER_FIELDS = ["uppercase", "exampleWord", "exampleWordChinese"]


def clip_name(key: str) -> str:
    """key -> 文件名(CRC32)"""
    return f"k{zlib.crc32(key.encode('utf-8')) & 0xFFFFFFFF:08x}.mp3"


def build_items():
    """收集全部待生成条目: [(key, text, lang, kind)]"""
    items = []

    # ---- 中文:简单字 1~600 的单字 / 词组 / 例句 ----
    data = json.loads(CHAR_FILE.read_text(encoding="utf-8"))
    easy = data["easy"] if CHAR_COVER is None else data["easy"][:CHAR_COVER]
    for it in easy:
        if not isinstance(it, dict):
            continue  # 没有词组/例句的字(白名单)跳过
        hanzi = it["char"]
        items.append((f"c:{hanzi}", hanzi, "zh", "单字"))
        for w in (it.get("words") or []):
            word = (w.get("word") or "").strip()
            if word:
                items.append((f"w:{word}", word, "zh", "词组"))
        for e in (it.get("examples") or []):
            sent = (e.get("sentence") or "").strip()
            if sent:
                items.append((f"e:{sent}", sent, "zh", "例句"))

    # ---- 英文:字母(字母本身 + 例词 + 例词中文) ----
    en = json.loads(EN_FILE.read_text(encoding="utf-8"))
    for lt in en["letters"]:
        up = (lt.get("uppercase") or "").strip()
        ew = (lt.get("exampleWord") or "").strip()
        ec = (lt.get("exampleWordChinese") or "").strip()
        if up:
            items.append((f"l:{up}", up, "en", "字母"))
        if ew:
            items.append((f"n:{ew}", ew, "en", "例词"))
        if ec:
            items.append((f"z:{ec}", ec, "zh", "例词中文"))

    # ---- 英文:简单单词(单词 + 中文意思 + 例句 + 翻译) ----
    for w in en["words"]:
        if EN_WORD_DIFFICULTY is not None and w.get("difficulty") != EN_WORD_DIFFICULTY:
            continue
        word = (w.get("word") or "").strip()
        mean = (w.get("chineseMeaning") or "").strip()
        sent = (w.get("exampleSentence") or "").strip()
        tran = (w.get("exampleSentenceTranslation") or "").strip()
        if word:
            items.append((f"n:{word}", word, "en", "单词"))
        if mean:
            items.append((f"z:{mean}", mean, "zh", "中文意思"))
        if sent:
            items.append((f"s:{sent}", sent, "en", "英文例句"))
        if tran:
            items.append((f"z:{tran}", tran, "zh", "例句翻译"))

    return items


def check_collisions(items):
    """CRC32 必须无碰撞,否则会静默覆盖 —— 构建时直接失败"""
    seen = {}
    bad = []
    for key, _, _, _ in items:
        name = clip_name(key)
        if name in seen and seen[name] != key:
            bad.append((seen[name], key, name))
        seen[name] = key
    return bad


async def synth_one(sem, edge_tts, text, voice, rate, tmp_path, retries=3):
    """合成一条到 mp3(临时)。返回 True/False"""
    async with sem:
        last_error = None
        for attempt in range(retries):
            try:
                c = edge_tts.Communicate(text, voice, rate=rate)
                await c.save(str(tmp_path))
                if tmp_path.exists() and tmp_path.stat().st_size > 512:
                    return True
            except Exception as exc:
                last_error = exc
            await asyncio.sleep(1.0 + attempt)
        if last_error is not None:
            print(f"  [warn] 合成失败 text={text[:12]!r}: "
                  f"{type(last_error).__name__}: {last_error}", flush=True)
        return False


# 裁掉首尾静音:edge-tts 生成的音频两端常带 0.3~0.8s 静音,
# 幼儿点一下要等半秒才出声,体验上像是"坏了"。顺带也省体积。
SILENCE_TRIM = (
    "silenceremove="
    "start_periods=1:start_duration=0:start_threshold=-45dB:"
    "stop_periods=-1:stop_duration=0.08:stop_threshold=-45dB"
)


def transcode(ffmpeg, src, dst):
    r = subprocess.run(
        [ffmpeg, "-y", "-loglevel", "error", "-i", str(src),
         "-af", SILENCE_TRIM,
         "-c:a", "libmp3lame", "-b:a", MP3_BITRATE, "-ac", "1", "-ar", "22050", str(dst)],
        capture_output=True, text=True,
    )
    if r.returncode != 0:
        # 裁剪失败就退回不裁,不让个别条目卡住整批
        r = subprocess.run(
            [ffmpeg, "-y", "-loglevel", "error", "-i", str(src),
             "-c:a", "libmp3lame", "-b:a", MP3_BITRATE, "-ac", "1", "-ar", "22050", str(dst)],
            capture_output=True, text=True,
        )
    return r.returncode == 0 and dst.exists() and dst.stat().st_size > 256


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="只生成前 N 条(调试用)")
    ap.add_argument("--jobs", type=int, default=8, help="并发数")
    ap.add_argument("--verify-only", action="store_true")
    ap.add_argument("--clean", action="store_true", help="先清空输出目录")
    args = ap.parse_args()

    try:
        import edge_tts
    except ImportError:
        print("[ERROR] 需要 edge-tts:python -m pip install edge-tts", file=sys.stderr)
        return 1
    try:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        print("[ERROR] 需要 imageio-ffmpeg:python -m pip install imageio-ffmpeg",
              file=sys.stderr)
        return 1

    items = build_items()
    # 同一文本可能出现多次(例如同一个词组属于多个字),按 key 去重
    uniq = {}
    for key, text, lang, kind in items:
        uniq.setdefault(key, (text, lang, kind))
    print(f"待生成条目: {len(items)} 条 -> 去重后 {len(uniq)} 条")

    by_kind = {}
    for key, (text, lang, kind) in uniq.items():
        by_kind[kind] = by_kind.get(kind, 0) + 1
    print("  分布:", ", ".join(f"{k} {v}" for k, v in sorted(by_kind.items())))

    bad = check_collisions([(k, None, None, None) for k in uniq])
    if bad:
        print(f"[ERROR] CRC32 碰撞 {len(bad)} 组,必须修正命名方案后再生成:", file=sys.stderr)
        for a, b, n in bad[:10]:
            print(f"  {n}: {a}  <->  {b}", file=sys.stderr)
        return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    if args.clean:
        shutil.rmtree(OUT_DIR, ignore_errors=True)
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        print("已清空输出目录")

    tasks = list(uniq.items())
    if args.limit:
        tasks = tasks[: args.limit]

    done = 0
    skipped = 0
    failed = []
    total_bytes = 0
    lock = asyncio.Lock()
    sem = asyncio.Semaphore(args.jobs)

    if args.verify_only:
        missing = [k for k, _ in tasks if not (OUT_DIR / clip_name(k)).exists()]
        sizes = [ (OUT_DIR / clip_name(k)).stat().st_size
                  for k, _ in tasks if (OUT_DIR / clip_name(k)).exists() ]
        print(f"已生成 {len(sizes)} / {len(tasks)} 条,合计 {sum(sizes)/1024/1024:.2f} MB")
        if missing:
            print(f"缺失 {len(missing)} 条,例如: {missing[:5]}")
        return 0

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)

        async def worker(key, text, lang, kind):
            nonlocal done, skipped, total_bytes
            dst = OUT_DIR / clip_name(key)
            if dst.exists() and dst.stat().st_size > 256:
                async with lock:
                    skipped += 1
                    total_bytes += dst.stat().st_size
                return
            voice = VOICE_ZH if lang == "zh" else VOICE_EN
            rate = RATE_ZH if lang == "zh" else RATE_EN
            raw = tmp / (clip_name(key)[:-4] + "_raw.mp3")
            ok = await synth_one(sem, edge_tts, text, voice, rate, raw)
            if not ok:
                async with lock:
                    failed.append(key)
                return
            # ⚠️ transcode 内部是 subprocess.run(同步阻塞)。
            # 直接在协程里调用会**阻塞整个事件循环** —— 12 个 worker 中只要有 1 个在
            # 转码,其余 11 个连网络请求都发不出去,实测吞吐从 114 条/分钟掉到 8 条/分钟。
            # 丢进线程池后 ffmpeg 与网络才真正并行。
            loop = asyncio.get_running_loop()
            if not await loop.run_in_executor(None, transcode, ffmpeg, raw, dst):
                async with lock:
                    failed.append(key)
                return
            try:
                raw.unlink()
            except OSError:
                pass
            async with lock:
                done += 1
                total_bytes += dst.stat().st_size
                if done % 100 == 0:
                    print(f"  已生成 {done} 条,累计 {total_bytes/1024/1024:.2f} MB", flush=True)

        print(f"开始生成(并发 {args.jobs})...")
        await asyncio.gather(*(worker(k, t, l, kd) for k, (t, l, kd) in tasks))

    print()
    print(f"新生成 {done} 条,跳过已存在 {skipped} 条,失败 {len(failed)} 条")
    print(f"本轮涉及文件合计 {total_bytes/1024/1024:.2f} MB")
    files = list(OUT_DIR.glob("*.mp3"))
    total = sum(f.stat().st_size for f in files)
    print(f"音频目录现有 {len(files)} 个文件,共 {total/1024/1024:.2f} MB")
    if failed:
        print(f"失败条目(前 10): {failed[:10]}")
        return 1

    # 供调试用的清单(运行时不读,只为排查"这个 key 对应哪个文件")
    MANIFEST.write_text(
        json.dumps({k: {"file": clip_name(k), "text": v[0], "lang": v[1], "kind": v[2]}
                    for k, v in uniq.items()},
                   ensure_ascii=False, indent=1),
        encoding="utf-8",
    )
    print(f"清单: {MANIFEST.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))

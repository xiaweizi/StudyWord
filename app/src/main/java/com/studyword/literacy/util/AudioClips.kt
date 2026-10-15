package com.studyword.literacy.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 内置语音包播放器。
 *
 * 为什么需要它
 * ------------
 * 部分设备(实测 HarmonyOS 的安卓兼容层)不提供 TTS 引擎:
 * `TextToSpeech.getEngines()` 返回空,系统"文本转语音"设置页也卡在"正在检查…"。
 * 鸿蒙自己的语音是 ArkTS API,安卓 APK 调不到 —— 这是平台边界,代码绕不过去。
 *
 * 但实测那台设备 **MediaPlayer 播放音频文件是正常的**(答题音效有声),
 * 所以把语音预生成成 Opus 文件打进 assets,就能在**任何设备**上都有发音。
 *
 * 与 `scripts/generate_audio.py` 的约定(改动必须两边同步)
 * -----------------------------------------------------
 *   c:<汉字>   中文单字        w:<中文>   中文词组
 *   e:<中文>   中文例句        z:<中文>   其它中文(意思/例词中文/翻译)
 *   l:<字母>   英文字母        n:<英文>   英文单词或例词
 *   s:<英文>   英文例句
 *
 * 文件名 = `k{CRC32(key):08x}.ogg`,生成脚本会断言无哈希碰撞。
 *
 * 依赖:build.gradle 里必须对 ogg 关闭压缩,否则 `openFd` 拿不到可 seek 的 FD
 * (MediaPlayer 无法直接播放被压缩进 APK 的音频)。
 */
object AudioClips {

    private const val TAG = "AudioClips"

    /** 池大小:一次只播一条为主,留几个余量给"点得很快"的情况 */
    private const val POOL_SIZE = 3

    /** 同一 key 连续失败多少次才判定为"这条音频不可用"(偶发失败允许重试) */
    private const val MAX_PLAY_FAILURES = 2

    /**
     * 顺序播放时两句之间的默认停顿。
     * 550ms 是"能听出这是两句话、又不显得拖沓"的经验值。
     */
    private const val DEFAULT_GAP_MS = 550L

    private var appContext: Context? = null

    /** 用于句间停顿(见 [playAt]) —— 以及 stop() 时取消未执行的补播 */
    private val handler = Handler(Looper.getMainLooper())

    /** 已排期但尚未执行的下一条播放;stop() 必须取消它 */
    private var pendingPlayback: Runnable? = null

    /** 已确认**不存在**的 key,避免每次都去 assets 里探一遍(IO 不便宜) */
    private val missing = HashSet<String>()

    /** 每个 key 的播放失败次数;达到 [MAX_PLAY_FAILURES] 才永久拉黑 */
    private val failureCounts = HashMap<String, Int>()

    /**
     * 最近一次播放失败的原因(含 MediaPlayer 错误码)。
     * 供"语音诊断"显示 —— 这类失败在设备上通常**没有任何用户可见的报错**,
     * 只能靠这里回传的 what/extra 定位(例如设备不支持该音频编码)。
     */
    @Volatile
    var lastError: String? = null
        private set

    /** 已确认缺失的 key 数量 */
    val missingCount: Int get() = missing.size

    /** 供"语音诊断"展示的内置语音包状态 */
    fun report(): String = buildString {
        append("内置语音包：")
        append(if (lastError == null) "未出现播放错误" else "最近一次播放失败")
        append('\n')
        append("已判定缺失的 key：").append(missing.size).append(" 个\n")
        lastError?.let { append("错误详情：").append(it) }
    }

    /** MediaPlayer 池:取走式(见 [SimplePool] 的说明,轮转式曾导致实例被误销毁) */
    private val pool = SimplePool<MediaPlayer>(POOL_SIZE) { MediaPlayer() }

    /** 当前正在播放的实例,用于 stop() */
    private var current: MediaPlayer? = null

    private var enabled = true

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 临时关掉内置音频(调试时对比 TTS 用) */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stop()
    }

    /**
     * 规整 key:与生成脚本的 strip 算法对齐,避免"数据末尾多一个空格 → 音频永远播不出"。
     *
     * 实现放在 [AudioClipNaming] —— 那里是纯函数且有跨语言契约测试,能被单测覆盖;
     * 本类的其余部分依赖 MediaPlayer,无法在 JVM 上测试。
     */
    private fun normalize(key: String): String = AudioClipNaming.normalizeKey(key)

    /**
     * key 对应的 asset 路径。
     * 规则见 [AudioClipNaming] —— 抽出去是为了有**跨语言契约测试**锁定,
     * 避免与 Python 生成脚本悄悄分叉(症状是"生成了却播不出声"且无报错)。
     */
    private fun assetPath(key: String): String = AudioClipNaming.assetPath(normalize(key))

    /** 是否存在该条音频(结果会被缓存) */
    fun has(key: String): Boolean {
        if (!enabled) return false
        val ctx = appContext ?: return false
        val k = normalize(key)
        if (missing.contains(k)) return false
        val path = assetPath(k)
        return try {
            ctx.assets.openFd(path).use { true }
        } catch (e: Exception) {
            missing.add(k)
            false
        }
    }

    /**
     * 播放一条音频。
     * @return true = 找到并开始播放;false = 没有这条音频,调用方应回退到 TTS
     */
    fun play(key: String): Boolean {
        if (!enabled) return false
        val ctx = appContext ?: return false
        val k = normalize(key)
        if (missing.contains(k)) return false
        val path = assetPath(k)
        val fd: AssetFileDescriptor = try {
            ctx.assets.openFd(path)
        } catch (e: Exception) {
            missing.add(k)
            return false
        }
        return try {
            stopCurrent()
            val mp = obtain()
            mp.reset()
            fd.use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setOnCompletionListener { /* 播完就停着,下次复用 */ }
            attachErrorListener(mp, key)
            mp.prepare()
            mp.start()
            current = mp
            true
        } catch (e: Exception) {
            recordFailure(key, "异常 ${e.javaClass.simpleName}: ${e.message}")
            releaseCurrent()
            false
        }
    }

    /**
     * 记录一次播放失败。
     *
     * 只有**反复失败**才把 key 拉黑 —— 偶发失败(实例状态、被其他应用抢占音频焦点等)
     * 应该允许下次重试,否则一次抖动会让这个字在本次进程内**永远没声音**。
     * 失败原因同时记进 [lastError],否则这类问题在设备上完全无法定位。
     */
    private fun recordFailure(key: String, reason: String) {
        lastError = "key=$key $reason"
        Log.w(TAG, "播放失败 $lastError")
        val count = (failureCounts[key] ?: 0) + 1
        failureCounts[key] = count
        if (count >= MAX_PLAY_FAILURES) missing.add(key)
    }

    /**
     * MediaPlayer 的错误回调。
     * 编码不支持、文件损坏等都会走到这里(what=1, extra=负的错误码),
     * 而**不会有异常抛出** —— 不挂这个监听就只能看到"没声音"。
     *
     * 返回 true 表示已处理,引擎不会再走完成回调;因此这里必须自己把实例
     * 归还池子,否则该实例会一直挂在"使用中"状态,后续播放拿不到可用实例。
     */
    private fun attachErrorListener(mp: MediaPlayer, key: String) {
        mp.setOnErrorListener { player, what, extra ->
            recordFailure(key, "MediaPlayer 错误 what=$what extra=$extra")
            current = null
            release(player)
            true
        }
    }

    /**
     * 依次播放多条(如英文单词卡的"单词 → 中文意思"、中文卡的"第一句例句 → 第二句")。
     * 任一条缺失就交给调用方回退 TTS;全部存在则顺序播完。
     *
     * @param gapMs 两条之间的停顿。**中文多句例句必须留停顿** ——
     *              否则两句连成一串,幼儿听不出这是两句话。
     * @return true = 已接管播放;false = 有缺失,调用方应自行处理(例如走 TTS)
     */
    fun playSequence(keys: List<String>, gapMs: Long = DEFAULT_GAP_MS): Boolean {
        if (!enabled) return false
        val ctx = appContext ?: return false
        if (keys.isEmpty()) return false
        // 先整体检查,避免播了一半才发现缺
        // (normalize:与生成脚本的 strip 对齐,详见 normalize 的说明)
        val paths = keys.map { raw ->
            val key = normalize(raw)
            if (missing.contains(key)) return false
            val p = assetPath(key)
            try {
                ctx.assets.openFd(p).use { }
                p
            } catch (e: Exception) {
                missing.add(key)
                return false
            }
        }
        return try {
            stopCurrent()
            playAt(ctx, paths, 0, gapMs)
            true
        } catch (e: Exception) {
            Log.w(TAG, "顺序播放失败: ${e.javaClass.simpleName} ${e.message}")
            releaseCurrent()
            false
        }
    }

    /**
     * 播放第 index 条,播完停顿 [gapMs] 再播下一条。
     *
     * v1.6.0:加入停顿。此前是播完立刻播下一条,**两句例句会连成一串** ——
     * 幼儿听不出这是两句、也来不及反应。用户反馈"2 句例句中间要停顿一下"。
     */
    private fun playAt(ctx: Context, paths: List<String>, index: Int, gapMs: Long) {
        if (index >= paths.size) {
            releaseCurrent()
            return
        }
        val mp = obtain()
        mp.reset()
        ctx.assets.openFd(paths[index]).use {
            mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
        mp.setOnCompletionListener {
            current = null
            release(mp)
            val next = index + 1
            if (next < paths.size && gapMs > 0) {
                // 用 delayed runnable 制造句间停顿;必须在 stop() 时取消,
                // 否则切走页面后还会"补播"下一条 —— 听起来像幽灵声音。
                val task = Runnable { playAt(ctx, paths, next, gapMs) }
                pendingPlayback = task
                handler.postDelayed(task, gapMs)
            } else {
                playAt(ctx, paths, next, gapMs)
            }
        }
        attachErrorListener(mp, paths[index])
        mp.prepare()
        mp.start()
        current = mp
    }

    /** 停止当前播放(切卡片/离开页面时调用) */
    fun stop() {
        stopCurrent()
    }

    fun release() {
        stopCurrent()
        pool.clear { dead -> runCatching { dead.release() } }
    }

    // ============================================================
    // 内部
    // ============================================================

    private fun stopCurrent() {
        // 先取消"句间停顿后补播下一条"的排期 ——
        // 否则离开页面后停顿结束仍会补播一句,听起来像幽灵声音
        pendingPlayback?.let { handler.removeCallbacks(it) }
        pendingPlayback = null
        current?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.reset() }
            release(mp)
        }
        current = null
    }

    /**
     * 取一个可用的 MediaPlayer。
     *
     * 语义由 [SimplePool] 保证:**取走后即不再属于池**,归还后才重新空闲。
     * 曾经这里写成"轮转"(`removeFirst()` 后再 `addLast()`),
     * 实例在使用期间仍留在池里,归还时被判为"已在池中"而销毁,
     * 池里却留着已销毁的引用 —— 症状是"开头几声能响、之后全没声音"。
     */
    private fun obtain(): MediaPlayer = pool.obtain()

    /** 归还实例:池未满则留作空闲,否则销毁 */
    private fun release(mp: MediaPlayer) {
        runCatching { mp.reset() }
        pool.release(mp) { dead -> runCatching { dead.release() } }
    }

    private fun releaseCurrent() {
        current = null
    }
}

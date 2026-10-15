package com.studyword.literacy.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * 全应用共用的 TTS 朗读管理器。
 *
 * 设计要点:
 * - 单例(进程级),避免每个 Activity 单独初始化引擎
 * - 默认中文,幼儿语速(1.0x = 系统默认)
 * - 英文朗读通过 [speakEnglish] 走 en-US Locale,与中文共用同一个引擎实例
 *
 * v1.6.0 修复(起因:用户在一台鸿蒙手机上点卡片毫无反应)
 * ---------------------------------------------------------
 * 旧实现有两个真问题:
 * 1. **只尝试默认引擎 + `Locale.SIMPLIFIED_CHINESE` 一次**,失败就把 `isReady` 置 false
 *    永久沉默。但系统里**可能有多个 TTS 引擎**,默认的引擎不支持中文、另一个支持 ——
 *    旧实现不会去找。
 * 2. 失败原因被**静默吞掉**,调用方只拿到 `false`,界面无法向用户解释"为什么没声音"。
 *
 * 现在:
 * - 依次探测**默认引擎 + 系统里所有引擎**,取第一个支持中文的;
 * - 中文 Locale 按 `zh_CN` → `zh` 顺序尝试(部分引擎只认其中一种);
 * - [status] 给出可区分的原因,供界面显示**可操作**的提示;
 * - [retryIfNeeded] 让用户去系统设置装好语音、回到前台后能自愈,不必重启 App;
 * - [diagnostics] 记录每个引擎的中文支持情况,便于排查。
 *
 * 实现注意:`TextToSpeech.getEngines()` / `getDefaultEngine()` 都是**实例方法**
 * (已用 javap 核对 android-34 的 android.jar),所以必须**先建一个默认引擎实例**
 * 才能枚举系统里有哪些引擎,再逐个新建实例去试。
 */
object TtsManager {

    private const val TAG = "TtsManager"

    /** 语音不可用的具体原因 —— 让界面能说清"为什么没声音" */
    enum class Status {
        /** 还没初始化 */
        NOT_INITIALIZED,

        /** 可用 */
        READY,

        /** 系统里一个 TTS 引擎都没有 */
        NO_ENGINE,

        /** 有引擎,但没有任何一个支持中文(或缺中文语音数据) */
        NO_CHINESE,

        /** 引擎存在但初始化失败 */
        INIT_FAILED;

        val ok: Boolean get() = this == READY
    }

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var probing = false

    @Volatile
    var status: Status = Status.NOT_INITIALIZED
        private set

    @Volatile
    var isReady: Boolean = false
        private set

    /** 英文 Locale 是否可用;探测引擎时同步尝试,后续 speakEnglish 据此决定是否朗读 */
    @Volatile
    var isEnglishReady: Boolean = false
        private set

    /** 实际选中的引擎包名(诊断用) */
    @Volatile
    var enginePackage: String? = null
        private set

    /** 逐引擎的中文支持情况(诊断用,失败时写入日志) */
    @Volatile
    var diagnostics: String = ""
        private set

    /** 探测到的可用中文 Locale(不同引擎认的写法不同:zh_CN / zh) */
    @Volatile
    private var chineseLocale: Locale = Locale.SIMPLIFIED_CHINESE

    /**
     * 用户从"语音设置"页回来时置位 —— 只在**确实去过设置**后才重试,
     * 避免每次 onResume 都重新枚举并创建 TTS 实例(创建引擎实例不算轻量)。
     */
    @Volatile
    private var retryOnNextResume = false

    /** 幼儿语速(1.0f = 系统默认) */
    private const val DEFAULT_SPEECH_RATE = 1.0f

    // ========== char-by-char 队列(保留原有能力) ==========
    private val mainHandler = Handler(Looper.getMainLooper())
    private val charQueue: ArrayDeque<CharJob> = ArrayDeque()
    private var charQueueActive = false

    private data class CharJob(val char: String, val isEnglish: Boolean, val baseId: String)

    fun init(context: Context) {
        if (tts != null || probing) return
        probe(context.applicationContext)
    }

    /** 界面打开系统语音设置前调用,这样用户装好语音回来能自动生效 */
    fun markEngineSettingsOpened() {
        retryOnNextResume = true
    }

    /** 从系统设置返回时调用;仅当上次是从语音设置回来、且当前不可用时才重试 */
    fun retryIfNeeded(context: Context) {
        if (!retryOnNextResume) return
        retryOnNextResume = false
        if (status.ok || probing) return
        Log.i(TAG, "从语音设置返回,重试探测 TTS 引擎")
        probe(context.applicationContext)
    }

    /**
     * 重置并重新探测。供"语音诊断"里的「重新检测」使用 ——
     * 用户可能在系统设置里装好了语音,但 App 的探测发生在启动时,需要手动触发一次。
     */
    fun redetect(context: Context) {
        synchronized(this) {
            runCatching { tts?.shutdown() }
            tts = null
            isReady = false
            isEnglishReady = false
            status = Status.NOT_INITIALIZED
            enginePackage = null
            diagnostics = ""
            probing = false
        }
        probe(context.applicationContext)
    }

    /**
     * 供"语音诊断"界面展示的可读报告。
     *
     * 之所以要做这个:用户设备上的 TTS 问题我这边看不到,只能靠用户截图。
     * 报告里带**逐引擎的探测结果**,一次截图就能区分
     * "系统里一个引擎都没有" 与 "有引擎但都起不来"。
     */
    fun report(): String = buildString {
        append("状态：").append(
            when (status) {
                Status.READY -> "可用 ✓"
                Status.NO_ENGINE -> "没有探测到任何语音引擎"
                Status.NO_CHINESE -> "有引擎，但没有一个支持中文"
                Status.INIT_FAILED -> "引擎初始化失败"
                Status.NOT_INITIALIZED -> "尚未检测完成（点「重新检测」）"
            }
        ).append('\n')
        append("选中引擎：").append(enginePackage ?: "（无）").append('\n')
        append("中文就绪：").append(isReady)
            .append("　英文就绪：").append(isEnglishReady).append('\n')
        if (diagnostics.isNotBlank()) {
            append('\n').append(diagnostics)
        } else {
            append("\n（没有引擎探测记录）")
        }
    }

    // ============================================================
    // 引擎探测
    // ============================================================

    /**
     * 第一步:试默认引擎;不行就用它枚举系统里的其它引擎,逐个再试。
     */
    private fun probe(context: Context) {
        probing = true
        val log = StringBuilder()
        probeEngine(context, null) { defaultInst, locale, why ->
            val defName = runCatching { defaultInst?.defaultEngine }.getOrNull()
            log.append("• 默认引擎 [").append(defName ?: "?").append("]: ").append(why).append('\n')
            if (defaultInst != null && locale != null) {
                adopt(defaultInst, locale, defName, log)
                return@probeEngine
            }
            // 默认引擎不支持中文 —— 借它枚举系统里的全部引擎(getEngines 是实例方法)
            val engines = runCatching { defaultInst?.getEngines() }.getOrNull().orEmpty()
            runCatching { defaultInst?.shutdown() }
            val others = engines.filter { it.name != defName }
            log.append("  系统共 ").append(engines.size).append(" 个引擎,再试其余 ")
                .append(others.size).append(" 个\n")
            tryOthers(context, others, 0, log, engines.size)
        }
    }

    /** 第二步:逐个尝试其余引擎 */
    private fun tryOthers(
        context: Context,
        list: List<TextToSpeech.EngineInfo>,
        index: Int,
        log: StringBuilder,
        engineCount: Int
    ) {
        if (index >= list.size) {
            finishUnavailable(log, engineCount)
            return
        }
        val info = list[index]
        probeEngine(context, info.name) { inst, locale, why ->
            log.append("• ").append(info.label.ifBlank { info.name })
                .append(" [").append(info.name).append("]: ").append(why).append('\n')
            if (inst != null && locale != null) {
                adopt(inst, locale, info.name, log)
            } else {
                runCatching { inst?.shutdown() }
                tryOthers(context, list, index + 1, log, engineCount)
            }
        }
    }

    /** 找到可用引擎:接管为当前实例,并探测英文能力 */
    private fun adopt(instance: TextToSpeech, locale: Locale, name: String?, log: StringBuilder) {
        tts = instance
        chineseLocale = locale
        enginePackage = name
        status = Status.READY
        isReady = true
        diagnostics = log.toString().trim()
        runCatching {
            instance.setLanguage(chineseLocale)
            instance.setSpeechRate(DEFAULT_SPEECH_RATE)
            // 英文朗读能力(不改变当前语言,只探测)
            val en = instance.isLanguageAvailable(Locale.US)
            isEnglishReady = en == TextToSpeech.LANG_AVAILABLE ||
                en == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                en == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            instance.setOnUtteranceProgressListener(buildProgressListener())
        }
        Log.i(TAG, "TTS 就绪 引擎=$name 中文Locale=$chineseLocale 英文可用=$isEnglishReady")
        probing = false
    }

    /**
     * 所有引擎都不可用。
     * 区分系统里一个引擎都没有(引导用户去装一个)与有引擎但都不支持中文
     * (引导用户去下载中文语音)—— 两种情况的解决动作不同,提示不能混。
     */
    private fun finishUnavailable(log: StringBuilder, engineCount: Int) {
        status = if (engineCount == 0) Status.NO_ENGINE else Status.NO_CHINESE
        isReady = false
        isEnglishReady = false
        diagnostics = log.toString().trim()
        Log.w(TAG, "没有可用的中文语音:\n$diagnostics")
        probing = false
    }

    /**
     * 用指定引擎试一次中文。回调 (实例, 可用的中文 Locale 或 null, 原因文本)。
     * `engineName` 传 null 表示系统默认引擎。
     */
    private fun probeEngine(
        context: Context,
        engineName: String?,
        onResult: (TextToSpeech?, Locale?, String) -> Unit
    ) {
        var instance: TextToSpeech? = null
        var settled = false
        val listener = TextToSpeech.OnInitListener { st ->
            if (settled) return@OnInitListener
            settled = true
            val engine = instance
            if (st != TextToSpeech.SUCCESS || engine == null) {
                onResult(engine, null, "初始化失败(状态码 $st)")
                return@OnInitListener
            }
            // 中文 Locale 逐个尝试:部分引擎只认 zh_CN,部分只认 zh
            var lastCode: Int? = null
            for (locale in listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.CHINESE,
                Locale.forLanguageTag("zh-CN"),
                Locale.forLanguageTag("zh")
            )) {
                val r = runCatching { engine.setLanguage(locale) }.getOrNull()
                if (lastCode == null) lastCode = r
                val ok = r != null && r != TextToSpeech.LANG_MISSING_DATA &&
                    r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ok) {
                    onResult(engine, locale, "支持中文($locale)")
                    return@OnInitListener
                }
            }
            val why = when (lastCode) {
                TextToSpeech.LANG_MISSING_DATA -> "缺少中文语音数据"
                TextToSpeech.LANG_NOT_SUPPORTED -> "不支持中文"
                null -> "setLanguage 返回 null(引擎可能已崩溃)"
                else -> "setLanguage 失败(状态码 $lastCode)"
            }
            onResult(engine, null, why)
        }
        instance = runCatching {
            if (engineName == null) TextToSpeech(context, listener)
            else TextToSpeech(context, listener, engineName)
        }.getOrNull()
        if (instance == null && !settled) {
            settled = true
            onResult(null, null, "无法创建引擎实例")
        }
    }

    // ============================================================
    // 朗读 API(签名保持不变)
    // ============================================================

    /**
     * 朗读一段中文文字。**引擎未就绪时返回 false** —— 调用方据此给用户提示,
     * 不再静默无声。多次调用会打断前一次朗读(QUEUE_FLUSH),不会卡队列。
     *
     * 实现细节:每次调用都显式把引擎切回中文 Locale,避免 [speakEnglish] 之后
     * 残留 en-US 状态导致中文被按英文规则朗读。
     */
    fun speak(text: String, utteranceId: String? = null): Boolean {
        return speakInternal(text, isEnglish = false, utteranceId = utteranceId, flush = true)
    }

    /** 朗读一段英文文字(单词/字母/例句均可) */
    fun speakEnglish(text: String, utteranceId: String? = null): Boolean {
        return speakInternal(text, isEnglish = true, utteranceId = utteranceId, flush = true)
    }

    /**
     * 内部核心朗读方法。
     * - [flush]=true:QUEUE_FLUSH,打断当前引擎中的所有朗读(独立朗读某段时使用)
     * - [flush]=false:QUEUE_ADD,追加到当前朗读队列末尾(前一段自然结束才读这一段)
     */
    private fun speakInternal(
        text: String,
        isEnglish: Boolean,
        utteranceId: String?,
        flush: Boolean
    ): Boolean {
        val engine = tts ?: return false
        if (text.isBlank()) return false
        if (isEnglish) {
            if (!isEnglishReady) return false
            val switchResult = runCatching { engine.setLanguage(Locale.US) }.getOrNull()
            if (switchResult == null ||
                switchResult == TextToSpeech.LANG_MISSING_DATA ||
                switchResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return false
            }
        } else {
            if (!isReady) return false
            runCatching { engine.setLanguage(chineseLocale) }
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val id = utteranceId ?: "tts_${System.nanoTime()}"
        return runCatching {
            engine.speak(text, mode, null, id) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
    }

    /**
     * 逐字朗读一段中文(词组 / 例句)。v1.4.2 起退化为整段朗读。
     * 保留函数签名只是不破坏调用方编译路径。
     */
    @Suppress("UNUSED_PARAMETER")
    fun speakPhraseCharByChar(text: String, utteranceId: String) {
        speak(text, utteranceId = utteranceId)
    }

    /**
     * 按顺序串发多段文字(英文 + 中文混合),默认 500ms 间隔。
     * 首项 FLUSH + 后续 ADD,引擎自然等前一段读完再读下一段(避免中英文重叠)。
     */
    fun speakSequential(
        items: List<Pair<String, Boolean>>,
        delayMs: Long = 500,
        baseUtteranceId: String = "seq_${System.nanoTime()}"
    ) {
        if (items.isEmpty()) return
        charQueue.clear()
        engineFlush()
        items.forEachIndexed { idx, (text, isEnglish) ->
            mainHandler.postDelayed({
                speakInternal(
                    text = text,
                    isEnglish = isEnglish,
                    utteranceId = "${baseUtteranceId}_${idx}",
                    flush = (idx == 0)
                )
            }, delayMs * idx)
        }
    }

    /** 中断当前所有朗读(char 队列 + sequential 都清空) */
    fun stop() {
        charQueue.clear()
        charQueueActive = false
        engineFlush()
    }

    // ============================================================
    // 内部
    // ============================================================

    private fun engineFlush() {
        runCatching { tts?.stop() }
    }

    /** 从 char 队列中拉下一个字朗读 */
    private fun speakNextInQueue() {
        if (!charQueueActive) return
        val engine = tts ?: run { charQueueActive = false; return }
        if (!isReady) {
            charQueueActive = false
            return
        }
        val next = charQueue.removeFirstOrNull()
        if (next == null) {
            charQueueActive = false
            return
        }
        if (next.isEnglish) {
            if (!isEnglishReady) {
                speakNextInQueue()
                return
            }
            runCatching { engine.setLanguage(Locale.US) }
        } else {
            runCatching { engine.setLanguage(chineseLocale) }
        }
        val id = "${next.baseId}_${System.nanoTime()}"
        val result = runCatching {
            engine.speak(next.char, TextToSpeech.QUEUE_ADD, null, id)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) {
            mainHandler.postDelayed({ speakNextInQueue() }, 200)
        }
    }

    private fun buildProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // 引擎开始朗读某字 —— 不需要处理
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { speakNextInQueue() }
            }

            @Deprecated("Required override for older Android versions")
            override fun onError(utteranceId: String?) {
                mainHandler.post { speakNextInQueue() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { speakNextInQueue() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                charQueueActive = false
            }
        }
    }

    /** 朗读完毕后把语言切回中文,避免下一句中文也被读成英文 */
    @Suppress("unused")
    fun resetToChinese() {
        runCatching { tts?.setLanguage(chineseLocale) }
    }

    /** 主动调整语速(默认 1.0f) */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.3f, 2.0f))
    }

    /** 释放资源。全局单例通常不需要每次 Activity 销毁都调用 */
    fun shutdown() {
        synchronized(this) {
            charQueue.clear()
            charQueueActive = false
            runCatching {
                tts?.setOnUtteranceProgressListener(null)
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
            isReady = false
            isEnglishReady = false
            status = Status.NOT_INITIALIZED
            enginePackage = null
            probing = false
        }
    }
}

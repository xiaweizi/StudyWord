package com.studyword.literacy.ui

import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressMigrator
import com.studyword.literacy.data.ProfileStore
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityMainBinding
import com.studyword.literacy.game.GameActivity
import com.studyword.literacy.game.GameMode
import com.studyword.literacy.model.BackAction
import com.studyword.literacy.model.BackPolicy
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishCategory
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.ProgressKey
import com.studyword.literacy.model.ProgressRules
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import com.studyword.literacy.util.AudioClips
import com.studyword.literacy.util.TtsManager
import kotlin.random.Random

/**
 * 主页(v1.4.0):
 * - 右上角抽屉收纳语言/难度/进度/设置
 * - 字卡可点击 → speakCurrentItem()
 * - 英文 mode 增加子模式(字母/单词)+ 单词按难度过滤
 * - 4 个 emoji 按钮(😊/😢/→/🎮),点击带中文 toast 提示
 * - 通过 registerForActivityResult 接 ProgressActivity/CharacterLibraryActivity 的跳转回值
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    /**
     * v1.5.0:跨配置变更(旋转)需保留的状态统一交给 [MainViewModel]。
     * 下面用属性委托转发,既有调用点无需改动。
     */
    private val vm: MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    // ====== 语种 / 难度 / 英文子模式(ViewModel 持有) ======
    private var currentMode: StudyMode
        get() = vm.currentMode
        set(value) { vm.currentMode = value }

    private var currentDifficulty: Difficulty
        get() = vm.currentDifficulty
        set(value) { vm.currentDifficulty = value }

    private var englishSubMode: EnglishSubMode
        get() = vm.englishSubMode
        set(value) { vm.englishSubMode = value }

    // ====== 进度集合(v1.5.0:元素为汉字 / "L:A" / "W:apple";每次 onResume 从 prefs 重载) ======
    private val knownIds: MutableSet<String> = mutableSetOf()
    private val unknownIds: MutableSet<String> = mutableSetOf()
    private val englishKnownIds: MutableSet<String> = mutableSetOf()
    private val englishUnknownIds: MutableSet<String> = mutableSetOf()

    // ====== 队列与当前项(ViewModel 持有,旋转不丢) ======
    private val pendingItems: ArrayDeque<StudyItem>
        get() = vm.pendingItems

    private var currentItem: StudyItem?
        get() = vm.currentItem
        set(value) { vm.currentItem = value }

    /**
     * v1.6.0:左右滑动导航用的两个栈。
     * - [cardHistory]:看过的卡片,向右滑 = 回到上一张
     * - [cardForward]:从历史回退过之后再向前滑,按原路返回
     *   (否则"回退一步后再往前"会变成重新抽卡,回不到刚才那张)
     */
    private val cardHistory = ArrayDeque<StudyItem>()
    private val cardForward = ArrayDeque<StudyItem>()

    /** 滑动识别器(在 [dispatchTouchEvent] 里旁路使用) */
    private var cardSwipeDetector: GestureDetector? = null

    /** 滑动判定的最小横向距离(dp → px),太短的滑动不当作翻卡 */
    private val swipeMinDistancePx: Int by lazy { (28 * resources.displayMetrics.density).toInt() }

    /**
     * 切换卡片。**统一从这里走**,是为了不漏记滑动历史 ——
     * 之前 currentItem 在多处直接赋值(loadNextItem / loadItemById / restorePreJumpState),
     * 如果把记历史的逻辑散在各处,很容易漏掉某一条路径。
     */
    private fun setCurrentItem(item: StudyItem?, recordHistory: Boolean = true) {
        val old = currentItem
        if (recordHistory && old != null && item != null && old.id != item.id) {
            cardHistory.addLast(old)
            while (cardHistory.size > HISTORY_MAX) cardHistory.removeFirst()
        }
        currentItem = item
    }

    /**
     * v1.4.2:跳转来源 — null = 正常启动;/ "progress" = 从 ProgressActivity 跳回;
     * "library" = 从 CharacterLibraryActivity 跳回。用于显示"← 返回进度/字库"按钮。
     *
     * v1.5.0:改由 [MainViewModel] 持有,旋转后不再丢失(此前按钮会莫名消失)。
     */
    private var activeProfileId: String? = null

    private var jumpSource: String?
        get() = vm.jumpSource
        set(value) { vm.jumpSource = value }

    private val random = Random(System.currentTimeMillis())
    /** v1.5.0:程序化回显 chip 状态时抑制监听器,避免初始化阶段触发重建队列 */
    private var initializingToggles = false
    private var successPlayer: MediaPlayer? = null
    private var encouragePlayer: MediaPlayer? = null
    private val mascotFaces = listOf("🐻", "🦊", "🐼", "🐰", "🦄", "🐨")
    private val drawerGreetings = listOf("你好呀!", "欢迎回来!", "今天我们一起学!", "Hi,准备好啦吗?")

    /**
     * 从 ProgressActivity / CharacterLibraryActivity 接 selectedId + language + 来源页
     * 来源页用于显示"← 返回"按钮,让用户能从主页跳回去
     *
     * 主页据此把 `jumpSource` 置空 → **"← 返回"按钮在任何跳转后都不会出现**
     * (等于把这个功能自己关掉了)。现在只要带了 `EXTRA_SOURCE_PAGE` 就记录来源,
     * 保证跳转后一定能点返回。
     */
    /**
     * v1.6.0:离开主页去打开源页(进度页 / 字库页)之前，先记下当前状态。
     * 用户从源页**用自己的返回箭头**退出来时(不会 setResult)，主页要恢复成
     * "打开源页之前"的样子 —— 否则返回按钮和跳转过来的卡片会一直留在主页上，
     * 表现就是"点了返回回到进度页，再按箭头又回到刚才那张卡"。
     */
    private var preJumpItem: StudyItem? = null
    private var preJumpSource: String? = null

    private val pageResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) {
            // 源页是被它自己的返回箭头关掉的(没有 setResult)→ 回到打开源页之前的状态
            restorePreJumpState()
            return@registerForActivityResult
        }
        val data = result.data ?: return@registerForActivityResult
        val selectedId = data.getIntExtra(EXTRA_SELECTED_ID, -1)
        val lang = data.getStringExtra(EXTRA_SELECTED_LANG) ?: return@registerForActivityResult
        if (selectedId < 0) return@registerForActivityResult
        // 记录来源(进度页 / 字库页),供"← 返回"按钮使用
        jumpSource = data.getStringExtra(EXTRA_SOURCE_PAGE)
        refreshBackJumpButton()
        loadItemById(selectedId, lang)
    }

    /**
     * 打开源页之前先快照;返回时若未选卡就还原。
     *
     * **只在"尚未处于跳转态"时快照**(即 `jumpSource == null`)。
     * 用户点「← 返回进度/字库」时本身已经处于跳转态了;如果这时也用
     * "被跳转过来的那张卡"覆盖快照,那么他从源页退出时会回到那张卡 ——
     * 而他期望的是回到**最开始离开主页时**的样子。
     * 所以快照记的是"跳转这件事还没发生之前"的状态。
     */
    private fun openSourcePage(intent: Intent) {
        if (jumpSource == null) {
            preJumpItem = currentItem
            preJumpSource = null
        }
        pageResultLauncher.launch(intent)
    }

    /**
     * 恢复到"打开源页之前":返回按钮回到原来的显隐状态,卡片回到原来那一张。
     * 快照用掉即清空,避免下次误用旧状态。
     */
    private fun restorePreJumpState() {
        val item = preJumpItem
        jumpSource = preJumpSource
        preJumpItem = null
        preJumpSource = null

        if (item != null) {
            currentItem = item
            updateCurrentItemView(item)
            updateSummaryHint()
        }
        refreshBackJumpButton()
    }

    /**
     * v1.4.4:系统返回键处理
     * - 抽屉打开时 → 关闭抽屉
     * - 有跳转源(jumpSource != null) → 启动源 Activity + finish 主页(回到源页)
     * - 正常主页态 → 弹"退出识字小帮手?"确认对话框
     *
     * v1.5.0:优先级规则抽到 [BackPolicy](纯函数 + 单测锁定),
     * 这里只负责"按决定执行"。
     */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val drawerOpen = binding.drawerLayout.isDrawerOpen(GravityCompat.END)
            val action = BackPolicy.decide(drawerOpen, jumpSource != null)
            when (action) {
                BackAction.CLOSE_DRAWER -> binding.drawerLayout.closeDrawer(GravityCompat.END)

                BackAction.GO_TO_SOURCE -> if (!navigateBackToSource()) showExitConfirmDialog()

                BackAction.CONFIRM_EXIT -> showExitConfirmDialog()
            }
        }
    }

    /**
     * 回到跳转来源页(进度 / 字库)。
     *
     * ⚠️ v1.6.0 修复:这里**不能 finish 主页**。
     *
     * 原实现是 `startActivity(intent); finish()` —— 主页被销毁后,源页就成了任务栈里
     * 唯一的 Activity,于是**在源页上滑动返回/按返回键会把源页也关掉,整个 App 直接退出**,
     * 完全绕过了"确定要退出吗"的确认对话框。
     *
     * 用户实测路径:字库 → 卡片 → (返回键) → 字库 → 左滑 → 直接退出。
     *
     * 改为与「返回字库」按钮走同一条路径 [openSourcePage]:主页保留在栈底,
     * 源页关闭后回到卡片,退出仍然必须经过确认。
     *
     * @return true 表示已跳转;false 表示来源无法解析(理论上不会发生),
     *         由调用方回落到退出确认,避免"按了返回却毫无反应"
     */
    private fun navigateBackToSource(): Boolean {
        val intent = when (jumpSource) {
            SOURCE_PROGRESS -> Intent(this, ProgressActivity::class.java)
            SOURCE_LIBRARY -> Intent(this, CharacterLibraryActivity::class.java)
            else -> null
        } ?: return false
        // 注意:openSourcePage 内部会在首次跳转时记录 preJump 状态,并在源页关闭后还原,
        // 与返回键的语义一致。这里不重置 jumpSource —— 由还原逻辑负责,避免状态错乱。
        openSourcePage(intent)
        return true
    }

    /**
     * 退出确认对话框。
     *
     * v1.5.0:文案修正 —— 每次判定都立即落盘,原话"进度还没保存"与事实不符,会误导家长。
     */
    private fun showExitConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.exit_dialog_title))
            .setMessage(getString(R.string.exit_dialog_message))
            .setPositiveButton(getString(R.string.exit_dialog_confirm)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.exit_dialog_cancel), null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore.active(this)

        // v1.5.0:把旧版「位置 id」进度一次性迁移为「内容键」进度(幂等)
        ProgressMigrator.migrateIfNeeded(progressStore, repository, englishRepository)

        currentMode = StudyMode.fromName(progressStore.loadLanguage())
        currentDifficulty = progressStore.loadDifficulty()
            ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
            ?: Difficulty.EASY
        englishSubMode = progressStore.loadEnglishSubMode()
            ?.let { runCatching { EnglishSubMode.valueOf(it) }.getOrNull() }
            ?: EnglishSubMode.LETTERS
        reloadAllProgressFromStore()
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)

        TtsManager.init(this)
        AudioClips.init(this)
        setupMenuButton()
        setupCardSwipe()
        setupSystemBarInsets()
        setupDrawerEntries()
        setupBackJumpButton()
        setupLanguageToggle()
        setupDifficultyToggle()
        setupEnglishSubModeToggle()
        setupActions()
        setupCardClick()
        refreshDrawerGreeting()
        // v1.5.0:旋转重建时 ViewModel 仍持有队列与当前项,
        // 绝不能无条件 rebuildQueue —— 否则孩子正在看的字会被换成队列里的另一项。
        if (vm.currentItem == null) {
            rebuildQueue()
            loadNextItem()
        } else {
            updateCurrentItemView(vm.currentItem)
            updateSummaryHint()
        }
        // v1.5.0:jumpSource 由 ViewModel 保留,需据此恢复"← 返回"按钮可见性
        refreshBackJumpButton()

        // v1.4.4:接管系统返回键 — 弹退出确认 / 关闭抽屉 / 回源页
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onDestroy() {
        // v1.5.0:取消撒花层所有挂起动画/回调(实现收敛到 ConfettiOverlayView)
        binding.confettiOverlay.cancelAll()
        successPlayer?.release()
        successPlayer = null
        encouragePlayer?.release()
        encouragePlayer = null
        TtsManager.shutdown()
        super.onDestroy()
    }

    // ============================================================
    // TTS 朗读
    // ============================================================

    /**
     * 朗读当前学习项。点卡片触发。
     * v1.4.1:letter 模式只读字母名(用户需求);word 模式读 EN + 中文意思(带停顿)
     * v1.4.2:中文字只读一遍,不再把拼音拼到文本里给 TTS(否则会被读成"日 rì")
     *
     * - 中文字 → speak(汉字)— 引擎默认读音,常用字最准
     * - 英文 letter → speakEnglish(uppercase),只读字母名(示例词由 chip 单独触发)
     * - 英文 word → speakSequential(word, chineseMeaning) 500ms 停顿
     */
    private fun speakCurrentItem() {
        val item = currentItem ?: return
        when (item) {
            is ChineseStudyItem -> {
                // v1.4.2:只读汉字一遍,不再 append 拼音(拼音仅作为卡片下方的 visual hint)
                speakWithFallback(listOf("c:${item.character.hanzi}")) {
                    TtsManager.speak(item.character.hanzi, utteranceId = "main_char_${item.id}")
                }
            }
            is EnglishLetterItem -> {
                speakWithFallback(listOf("l:${item.letter.uppercase}")) {
                    TtsManager.speakEnglish(
                        item.letter.uppercase, utteranceId = "main_letter_${item.id}"
                    )
                }
            }
            is EnglishWordItem -> {
                val word = item.word
                speakWithFallback(
                    buildList {
                        add("n:${word.word}")
                        if (word.chineseMeaning.isNotBlank()) add("z:${word.chineseMeaning}")
                    }
                ) {
                    TtsManager.speakSequential(
                        items = buildList {
                            add(word.word to true)
                            if (word.chineseMeaning.isNotBlank()) add(word.chineseMeaning to false)
                        },
                        delayMs = 500,
                        baseUtteranceId = "main_word_${item.id}"
                    )
                    true
                }
            }
        }
    }

    /**
     * v1.6.0:发音的**统一入口** —— 优先播内置语音包,没有再回退系统 TTS。
     *
     * 为什么:部分设备(实测 HarmonyOS 的安卓兼容层)不提供 TTS 引擎,
     * `TextToSpeech.getEngines()` 返回空、系统"文本转语音"页卡在"正在检查",
     * 但 **MediaPlayer 播放音频文件是正常的**(答题音效有声)。
     * 把常用字词预生成成音频打进 APK,这些设备上就照样有发音。
     *
     * 两条路都不可用时才提示用户。
     */
    private fun speakWithFallback(clips: List<String>, tts: () -> Boolean) {
        if (AudioClips.playSequence(clips)) return
        if (tts()) return
        showTtsUnavailable()
    }

    /**
     * 依次尝试多个候选 key,返回第一个成功播放的。
     * 用于"同一个文本可能是词组、例句或其它中文"这种不确定场景。
     */
    private fun playFirstClip(vararg keys: String): Boolean {
        for (key in keys) {
            if (AudioClips.play(key)) return true
        }
        return false
    }

    /**
     * v1.6.0:语音不可用时给出**看得懂 + 能操作**的提示,而不是静默无声。
     *
     * 起因:用户在另一台鸿蒙手机上点卡片完全没反应 —— 那台设备没有可用的中文 TTS
     * 引擎(答题音效走 MediaPlayer 所以正常),而旧实现把失败静默吞掉,
     * 用户既听不到声音也不知道为什么。
     *
     * @return true = 语音可用,可以继续;false = 已提示用户,调用方应直接返回
     */
    private fun ensureTtsAvailable(): Boolean {
        if (TtsManager.isReady) return true
        showTtsUnavailable()
        return false
    }

    /** 显示"语音不可用"提示,内容随探测到的原因变化 */
    private fun showTtsUnavailable() {
        val msgRes = when (TtsManager.status) {
            TtsManager.Status.NO_ENGINE -> R.string.tts_no_engine
            TtsManager.Status.NO_CHINESE -> R.string.tts_no_chinese
            TtsManager.Status.INIT_FAILED -> R.string.tts_init_failed
            else -> R.string.tts_not_ready
        }
        Log.w(
            TAG,
            "发音不可用 status=${TtsManager.status} engine=${TtsManager.enginePackage}\n" +
                TtsManager.diagnostics
        )
        Snackbar.make(binding.root, getString(msgRes), Snackbar.LENGTH_LONG)
            .setAction(R.string.tts_open_settings) { openTtsSettings() }
            .show()
    }

    /**
     * 打开系统的"文字转语音"设置,让用户能下载中文语音或换一个引擎。
     * 逐个尝试可用入口,避免某些 ROM 上某个 Intent 不存在导致崩溃。
     */
    private fun openTtsSettings() {
        TtsManager.markEngineSettingsOpened()
        val intents = listOf(
            // Settings 类里没有这个常量(javap 核实),用系统实际使用的 action 字符串
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        Toast.makeText(this, getString(R.string.tts_no_settings), Toast.LENGTH_SHORT).show()
    }

    /**
     * v1.4.1:点击字母行(letterContainer)只读字母,行为同 letter 分支。
     */
    private fun speakLetterOnly() {
        val letter = (currentItem as? EnglishLetterItem)?.letter ?: return
        speakWithFallback(listOf("l:${letter.uppercase}")) {
            TtsManager.speakEnglish(letter.uppercase, utteranceId = "main_letter_only_${letter.id}")
        }
    }

    /**
     * 朗读一段自定义文本(供词组 chip 点击调用)。
     * v1.4.2:中文走整段 speak(),正常语速(用户反馈"词组例句读得慢")
     * v1.5.0:移除从未使用的 pinyin 参数(lint/编译告警)
     */
    private fun speakWordOrSentence(text: String, utteranceId: String, isEnglish: Boolean) {
        // 调用方传进来的文本可能是词组、例句或其它中文/英文,依次试各个命名空间
        val played = if (isEnglish) {
            playFirstClip("n:$text", "s:$text")
        } else {
            playFirstClip("w:$text", "e:$text", "z:$text")
        }
        if (played) return
        if (isEnglish) {
            if (TtsManager.speakEnglish(text, utteranceId = utteranceId)) return
        } else {
            if (TtsManager.speak(text, utteranceId = utteranceId)) return
        }
        showTtsUnavailable()
    }

    /**
     * 例句朗读。
     * - 中文:按顺序读**全部**例句
     * - 英文 word:英文例句 + 中文翻译
     * - 英文 letter:只读英文
     *
     * ⚠️ v1.6.0 修复:此前只朗读 `first.sentence`,而卡片把**所有**例句换行拼接显示。
     * 于是有 2 条例句的字(共 239 个)点下去只听到第一句,第二句"看得见听不到"。
     * 现在朗读的内容严格跟随显示的内容 —— 显示几句就读几句。
     */
    private fun speakExampleSentence(item: StudyItem?, sentences: List<String>, utteranceId: String) {
        if (sentences.isEmpty()) return
        val english = item is EnglishWordItem || item is EnglishLetterItem
        val translation = (item as? EnglishWordItem)?.word?.exampleSentenceTranslation.orEmpty()
        val clips = buildList {
            sentences.forEach { add(if (english) "s:$it" else "e:$it") }
            // 英文单词的例句读"英文 + 中文翻译";中文没有翻译这一步
            if (english && translation.isNotBlank()) add("z:$translation")
        }
        if (AudioClips.playSequence(clips)) return

        if (item == null) {
            if (TtsManager.speak(sentences.joinToString(""), utteranceId = utteranceId)) return
            showTtsUnavailable()
            return
        }
        if (item is EnglishWordItem && translation.isNotBlank()) {
            TtsManager.speakSequential(
                items = sentences.map { it to true } + listOf(translation to false),
                delayMs = 500,
                baseUtteranceId = utteranceId
            )
            return
        }
        // 中文/英文逐句发音,句间留间隔,避免连成一串听不清。
        // speakSequential 不返回状态,所以先判可用性再调用,否则失败无人提示。
        if (!TtsManager.isReady) {
            showTtsUnavailable()
            return
        }
        TtsManager.speakSequential(
            items = sentences.map { it to english },
            delayMs = 500,
            baseUtteranceId = utteranceId
        )
    }

    // ============================================================
    // 控件初始化
    // ============================================================

    /**
     * v1.6.0:系统栏避让。
     *
     * 应用的窗口实际是**全屏**的(`dumpsys window` 里 `Requested h=2720` = 物理高度),
     * 也就是说内容会一直画到导航栏底下。手势导航的机型看不出问题(导航条是细横线),
     * 但**三键导航**的机型上,底部固定按钮栏会被系统导航栏压住一截。
     *
     * 这里按 `systemBars` 的 inset 给两处补内边距:
     * - 固定按钮栏的底部 → 始终高于导航栏
     * - 滚动区的顶部 → 状态栏/刘海更高的机型上,顶部 label 不会被压住
     *
     * 在布局原有 padding 的基础上**累加**,不是覆盖,避免破坏设计间距。
     */
    private fun setupSystemBarInsets() {
        val scrollTop = binding.homeScrollView.paddingTop
        val barBottom = binding.actionBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeScrollView.updatePadding(top = scrollTop + bars.top)
            binding.actionBar.updatePadding(bottom = barBottom + bars.bottom)
            insets
        }
    }

    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    /**
     * v1.4.2:抽屉入口改为 3 张大卡(进度 / 字库 / 设置)。
     * - 进度 / 字库走 pageResultLauncher,可能返回 selectedId 跳回主页特定卡片
     * - 设置走普通 startActivity
     */
    private fun setupDrawerEntries() {
        // v1.6.0:字库规模按实际数量生成 —— 此前写死"3000+ 字、26 字母、30 单词",
        // 英文词库扩到 150 个之后这句就是错的。数据驱动的文案不会过期。
        binding.navLibraryDesc.text = getString(
            R.string.nav_library_desc_format,
            repository.count(),
            englishRepository.letterCount(),
            englishRepository.wordCount()
        )

        // v1.6.0:进入"复习错题"模式(间隔重复)—— 只出到期的错题
        binding.drawerReviewCard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            vm.reviewOnly = true
            pendingItems.clear()
            currentItem = null
            rebuildQueue()
            loadNextItem()
        }
        binding.drawerProgressCard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            openSourcePage(Intent(this, ProgressActivity::class.java))
        }
        binding.drawerLibraryCard.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            openSourcePage(Intent(this, CharacterLibraryActivity::class.java))
        }
        binding.drawerSettingsCard.setOnClickListener {
            // v1.6.0:重要设置走家长门(Apple Kids 类目强制要求 parental gate)。
            // 只有"更多设置"加门 —— 语言/难度/复习/进度/字库对孩子无害,不加门,
            // 免得家长每次都要解题。
            ParentGate.show(this) {
                binding.drawerLayout.closeDrawer(GravityCompat.END)
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        // v1.4.4 新增:抽屉左侧"←"按钮 → 关闭抽屉回主页(主页本来就在后台栈,不需要 finish)
        binding.drawerBackButton.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
        }
    }

    /**
     * v1.4.2:从进度 / 字库跳转回来后,显示"← 返回进度"或"← 返回字库"按钮。
     * v1.4.3 修复:点 → 重启源 Activity 时不再 finish() 主页,保留主页在后台。
     * 栈顺序: [主页, 源页]。源页左上角箭头 finish() 时会自然回到主页,
     * 不会再因为主页已 finish 而直接退出 app。
     */
    private fun setupBackJumpButton() {
        binding.backJumpButton.setOnClickListener {
            val intent = when (jumpSource) {
                SOURCE_PROGRESS -> Intent(this, ProgressActivity::class.java)
                SOURCE_LIBRARY -> Intent(this, CharacterLibraryActivity::class.java)
                else -> null
            }
            if (intent != null) {
                // v1.4.3:不 finish,主页继续驻留在任务栈底部
                // v1.6.0:改走 openSourcePage → 源页用自身箭头退出时能收到回调并还原状态
                openSourcePage(intent)
            }
        }
    }

    private fun refreshBackJumpButton() {
        val source = jumpSource
        if (source == null) {
            binding.backJumpButton.visibility = View.GONE
        } else {
            binding.backJumpButton.visibility = View.VISIBLE
            // v1.6.0:统一用短文案「← 返回」。
            // 原来按来源显示「← 返回进度」/「← 返回字库」,字数多 ->
            // 把 topLabel 挤到第二行、整条顶栏变高且位置偏移。
            // 返回目标其实不必写在按钮上(用户按一下就知道),优先保证顶栏一行。
            binding.backJumpButton.text = getString(R.string.back_with_arrow)
        }
        // v1.4.3:按钮可见状态变了,刷新 topLabel 的 margin 让位
        refreshTopLabel()
    }

    /**
     * v1.4.2:每次启动随机挑一句问候,放在抽屉顶部
     */
    private fun refreshDrawerGreeting() {
        binding.drawerGreeting.text = drawerGreetings[random.nextInt(drawerGreetings.size)]
        binding.drawerAvatar.text = mascotFaces[random.nextInt(mascotFaces.size)]
    }

    private fun setupLanguageToggle() {
        binding.languageChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val newMode = when (checkedId) {
                binding.chipLanguageChinese.id -> StudyMode.CHINESE
                binding.chipLanguageEnglish.id -> StudyMode.ENGLISH
                else -> StudyMode.CHINESE
            }
            if (newMode != currentMode) {
                vm.reviewOnly = false   // v1.6.0:换语种时退出复习模式
                currentMode = newMode
                progressStore.saveLanguage(newMode.name)
                pendingItems.clear()
                currentItem = null
                applyModeUi()
                rebuildQueue()
                loadNextItem()
            }
        }
        when (currentMode) {
            StudyMode.CHINESE -> binding.chipLanguageChinese.isChecked = true
            StudyMode.ENGLISH -> binding.chipLanguageEnglish.isChecked = true
        }
        applyModeUi()
    }

    /** 中文 → 显示"难度",隐藏英文子模式;英文 → 显示"英文子模式",但难度仍按英文模式显示 */
    private fun applyModeUi() {
        val isEnglish = currentMode == StudyMode.ENGLISH
        binding.difficultyLabel.isVisible = true
        binding.difficultyChipGroup.isVisible = true
        // v1.4.3:englishSubModeGroup 已搬到抽屉"学习设置"卡内
        binding.englishSubModeGroup.isVisible = isEnglish
        binding.englishSubModeLabel.isVisible = isEnglish
        refreshTopLabel()
        // v1.5.0:让 chip 与已恢复/已选择的子模式保持一致(不再无条件强制 letters)
        if (isEnglish) {
            initializingToggles = true
            when (englishSubMode) {
                EnglishSubMode.LETTERS -> binding.chipSubLetters.isChecked = true
                EnglishSubMode.WORDS -> binding.chipSubWords.isChecked = true
            }
            initializingToggles = false
        }
    }

    /**
     * v1.4.4:顶部 label 显示"当前语言 · 难度"。
     * 中英文 mode 都显示,保证字卡起点位置永远一致。
     * 当前"卡片"内不再显示难度 chip,避免重复。
     * 当"← 返回"按钮可见时,把 topLabel 推到按钮右侧,避免重叠。
     *
     * v1.4.4:按钮融入背景(透明)+ 高度从 48dp → 40dp,所以让位 margin 从 140dp → 110dp。
     */
    private fun refreshTopLabel() {
        val langLabel = when (currentMode) {
            StudyMode.CHINESE -> getString(R.string.lang_label_chinese)
            StudyMode.ENGLISH -> getString(R.string.lang_label_english)
        }
        // v1.5.0:难度文案走 difficulty_easy/medium/hard 资源
        val difficultyText = when (currentDifficulty) {
            Difficulty.EASY -> getString(R.string.difficulty_easy)
            Difficulty.MEDIUM -> getString(R.string.difficulty_medium)
            Difficulty.HARD -> getString(R.string.difficulty_hard)
        }
        binding.topLabel.text = getString(
            R.string.top_label_format,
            langLabel,
            getString(R.string.difficulty_format, difficultyText)
        )
        // v1.6.0:不再用动态 margin 给返回按钮"让位"。
        // 旧实现是 `params.marginStart = if (jumpSource == null) 0 else dp(110)` ——
        // 它会随着返回按钮的显隐推着标签左右跳,正是"跳转后顶栏没对齐"的直接原因。
        // 现在三个元素在同一个 LinearLayout 里(标签 weight=1 占满左侧,按钮在右侧),
        // 返回按钮的显隐**不会**影响标签位置。
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun setupDifficultyToggle() {
        binding.difficultyChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (initializingToggles) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentDifficulty = when (checkedId) {
                binding.chipEasy.id -> Difficulty.EASY
                binding.chipMedium.id -> Difficulty.MEDIUM
                binding.chipHard.id -> Difficulty.HARD
                else -> Difficulty.EASY
            }
            // v1.5.0:难度持久化 —— 此前旋转/重启后会被强制重置回"简单"
            progressStore.saveDifficulty(currentDifficulty.name)
            vm.reviewOnly = false   // v1.6.0:换难度时退出复习模式
            refreshTopLabel()
            pendingItems.clear()
            currentItem = null
            rebuildQueue()
            loadNextItem()
        }
        // v1.5.0:回显上次使用的难度(不再无条件 chipEasy)
        initializingToggles = true
        when (currentDifficulty) {
            Difficulty.EASY -> binding.chipEasy.isChecked = true
            Difficulty.MEDIUM -> binding.chipMedium.isChecked = true
            Difficulty.HARD -> binding.chipHard.isChecked = true
        }
        initializingToggles = false
        refreshTopLabel()
    }

    private fun setupEnglishSubModeToggle() {
        binding.englishSubModeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (initializingToggles) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            englishSubMode = when (checkedId) {
                binding.chipSubLetters.id -> EnglishSubMode.LETTERS
                binding.chipSubWords.id -> EnglishSubMode.WORDS
                else -> EnglishSubMode.LETTERS
            }
            // v1.5.0:子模式持久化
            progressStore.saveEnglishSubMode(englishSubMode.name)
            vm.reviewOnly = false   // v1.6.0:换子模式时退出复习模式
            pendingItems.clear()
            currentItem = null
            rebuildQueue()
            loadNextItem()
        }
        // v1.5.0:回显上次使用的子模式
        initializingToggles = true
        when (englishSubMode) {
            EnglishSubMode.LETTERS -> binding.chipSubLetters.isChecked = true
            EnglishSubMode.WORDS -> binding.chipSubWords.isChecked = true
        }
        initializingToggles = false
    }

    /**
     * 4 个 emoji 按钮:认识 / 不认识 / 下一个 / 游戏
     * 每个点击有中文 toast 提示
     */
    private fun setupActions() {
        binding.knowButton.setOnClickListener {
            toast(getString(R.string.toast_known))
            handleResult(ItemResult.KNOWN)
        }
        binding.unknownButton.setOnClickListener {
            toast(getString(R.string.toast_unknown))
            handleResult(ItemResult.UNKNOWN)
        }
        binding.skipButton.setOnClickListener {
            toast(getString(R.string.toast_skip))
            loadNextItem(requeueCurrent = true)
        }
        binding.playGameButton.setOnClickListener {
            toast(getString(R.string.toast_play_game))
            val defaultMode = when (currentMode) {
                StudyMode.CHINESE -> GameMode.LISTEN
                StudyMode.ENGLISH -> GameMode.LISTEN_LETTER
            }
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_MODE, defaultMode.name)
                putExtra(GameActivity.EXTRA_DIFFICULTY, currentDifficulty.name)
            }
            startActivity(intent)
        }
    }

    /**
     * v1.4.1 字卡点击逻辑:
     * - 卡片整体(内部 LinearLayout)→ speakCurrentItem()
     *   - letter 模式:只读字母名
     *   - word 模式:读 EN + 中文意思(500ms 停顿)
     *   - 中文模式:读汉字 + 拼音
     * - letterContainer(字母行 Aa)→ speakLetterOnly(),覆盖卡片整体事件
     *   行为同 letter 分支,但确保即使在 card 整体被 clickable 时点击字母行也只读字母
     *
     * 注意:card 的 MaterialCardView 本体 clickable=false,真正的点击事件源是
     * 内部的 LinearLayout(android:foreground="?attr/selectableItemBackground")。
     */
    private fun setupCardClick() {
        val cardContent = (binding.currentCharacterCard.getChildAt(0) as? LinearLayout)
            ?: binding.currentCharacterCard
        cardContent.setOnClickListener { speakCurrentItem() }
        binding.letterContainer.setOnClickListener { speakLetterOnly() }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // 跳转回主页特定卡片
    // ============================================================

    /**
     * 从 ProgressActivity / CharacterLibraryActivity 跳转回主页指定卡片。
     *
     * v1.6.0 **修复两个 bug**:
     * 1. **中文也要按全库定位并对齐难度**。此前只有英文分支会切子模式/难度,
     *    中文沿用"当前难度池",于是在字库页点了其它难度的字时 `pendingItems` 里
     *    根本找不到该 id → 走静默 return → **主页字卡纹丝不动**(用户看到的就是"跳转错误")。
     * 2. **不再静默失败**:若重建队列后仍找不到目标,直接用仓库构造卡片显示。
     */
    private fun loadItemById(id: Int, lang: String) {
        val targetMode = StudyMode.fromName(lang)
        if (targetMode != currentMode) {
            currentMode = targetMode
            progressStore.saveLanguage(targetMode.name)
            applyModeUi()
        }
        // 跳转是"我要看这一张卡"的明确动作 → 退出复习模式,避免题池互相干扰
        vm.reviewOnly = false

        var directItem: StudyItem? = null
        initializingToggles = true
        when (targetMode) {
            StudyMode.CHINESE -> {
                repository.all().firstOrNull { it.id == id }?.let { character ->
                    currentDifficulty = character.difficulty
                    directItem = ChineseStudyItem(character)
                }
                syncDifficultyChip()
            }
            StudyMode.ENGLISH -> {
                if (id >= EnglishRepository.WORD_ID_OFFSET) {
                    englishSubMode = EnglishSubMode.WORDS
                    binding.chipSubWords.isChecked = true
                    englishRepository.findByWordById(id)?.let { word ->
                        currentDifficulty = word.difficulty
                        directItem = EnglishWordItem(word)
                    }
                } else {
                    englishSubMode = EnglishSubMode.LETTERS
                    binding.chipSubLetters.isChecked = true
                    englishRepository.findByLetterById(id)?.let { directItem = EnglishLetterItem(it) }
                }
                syncDifficultyChip()
            }
        }
        initializingToggles = false
        progressStore.saveEnglishSubMode(englishSubMode.name)
        progressStore.saveDifficulty(currentDifficulty.name)
        refreshTopLabel()

        rebuildQueue()
        // 把目标推到队首(ArrayDeque 没有 removeAt,改用 toList+重建)
        val matchIdx = pendingItems.indexOfFirst { it.id == id }
        when {
            matchIdx > 0 -> {
                val all = pendingItems.toList()
                pendingItems.clear()
                pendingQueueAddFirst(all[matchIdx], all, matchIdx)
                loadNextItem()
            }
            matchIdx == 0 -> loadNextItem()
            else -> {
                // 兜底:宁可直接显示目标,也不要静默什么都不做
                val item = directItem
                if (item != null) {
                    currentItem = item
                    updateCurrentItemView(item)
                    updateSummaryHint()
                }
            }
        }
    }

    /** 把难度 chip 与 [currentDifficulty] 对齐(不触发监听器) */
    private fun syncDifficultyChip() {
        when (currentDifficulty) {
            Difficulty.EASY -> binding.chipEasy.isChecked = true
            Difficulty.MEDIUM -> binding.chipMedium.isChecked = true
            Difficulty.HARD -> binding.chipHard.isChecked = true
        }
    }

    /**
     * 把 all[matchIdx] 推到队首,其余顺序保持
     */
    private fun pendingQueueAddFirst(found: StudyItem, all: List<StudyItem>, matchIdx: Int) {
        pendingItems.addLast(found)
        all.forEachIndexed { i, it -> if (i != matchIdx) pendingItems.addLast(it) }
    }

    // ============================================================
    // 判定与队列
    // ============================================================

    private fun handleResult(result: ItemResult) {
        val item = currentItem ?: return
        // v1.5.0:统一走 ProgressRules(与字库页共用同一套互斥规则)
        val (known, unknown) = currentKnownUnknown()
        ProgressRules.apply(known, unknown, item.progressKey, result == ItemResult.KNOWN)

        // v1.6.0:错题本 + 间隔重复
        // 不认识 → 进错题本、盒子归零;认识 → 若曾进过错题本则盒子 +1(间隔拉长)
        val now = System.currentTimeMillis()
        when (result) {
            ItemResult.UNKNOWN -> progressStore.recordWrong(item.progressKey, now)
            ItemResult.KNOWN -> progressStore.recordCorrect(item.progressKey, now)
        }

        when (result) {
            ItemResult.KNOWN -> celebrate()
            ItemResult.UNKNOWN -> {
                pendingItems.addLast(item)
                showEncourageSparkle()
                playEncourageSound()
                loadNextItem()
            }
        }
        persistProgress()
        updateSummaryHint()
    }

    private fun persistProgress() {
        progressStore.save(knownIds, unknownIds)
        progressStore.saveEnglish(englishKnownIds, englishUnknownIds)
        progressStore.recordSnapshot(knownIds.size, unknownIds.size)
    }

    private fun celebrate() {
        setActionButtonsEnabled(false)
        playSuccessSound()
        showConfetti {
            loadNextItem()
            setActionButtonsEnabled(true)
        }
    }

    /**
     * 重建题池。
     * - 中文:repository.byDifficulty(currentDifficulty)
     * - 英文 + LETTERS:全部字母
     * - 英文 + WORDS:byCategoryAndDifficulty(WORDS, currentDifficulty)
     * - v1.6.0 复习模式([MainViewModel.reviewOnly]):题池**只保留到期的错题**
     */
    private fun rebuildQueue() {
        pendingItems.clear()

        // v1.6.0:复习模式不受难度/子模式限制,直接从全库取到期错题
        if (vm.reviewOnly) {
            rebuildReviewQueue()
            return
        }

        val pool = buildCurrentPool()
        if (pool.isEmpty()) {
            updateCurrentItemView(null)
            val emptyMsg = when (currentMode) {
                StudyMode.CHINESE -> getString(R.string.empty_pool_chinese)
                StudyMode.ENGLISH -> getString(R.string.empty_pool_english)
            }
            Snackbar.make(binding.root, emptyMsg, Snackbar.LENGTH_SHORT).show()
            return
        }

        val (known, unknown) = currentKnownUnknown()
        pendingItems.addAll(
            ProgressRules.partition(pool, { it.progressKey }, known, unknown).ordered()
        )
    }

    /**
     * 复习模式的题池:只放**现在到期**的错题,按"错得最多、最久没复习"排序。
     *
     * v1.6.0 修复:此前复用"当前难度/子模式"的题池,于是**错题只要不属于当前难度,
     * 就被判成"没有要复习的字"并静默退回普通模式** —— 用户看到的现象就是
     * "点了复习错题完全没反应"。现在改为从**整个仓库**按到期键取卡片,
     * 不受难度 / 子模式限制(复习本来就不该受这些筛选影响)。
     */
    private fun rebuildReviewQueue() {
        val dueKeys = progressStore.dueReviewKeys(System.currentTimeMillis())
        val due = dueKeys.mapNotNull { key -> itemByProgressKey(key) }

        if (due.isEmpty()) {
            vm.reviewOnly = false
            updateCurrentItemView(null)
            // 区分两种情况:真的没有错题 vs 错题在另一种语言里
            val wantEnglish = currentMode == StudyMode.ENGLISH
            val belongsToCurrent = dueKeys.any { ProgressKey.isEnglish(it) == wantEnglish }
            val msgRes = if (dueKeys.isNotEmpty() && !belongsToCurrent) {
                R.string.review_other_language
            } else {
                R.string.review_empty
            }
            Snackbar.make(binding.root, getString(msgRes), Snackbar.LENGTH_LONG).show()
            rebuildQueue()
            return
        }

        pendingItems.addAll(due)
        Snackbar.make(
            binding.root,
            getString(R.string.review_started_format, due.size),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * 按进度键从**整个仓库**取卡片,忽略当前难度 / 子模式。
     * (与 [buildCurrentPool] 相对:那个是"当前筛选下的题池",这个是"全库查找")
     */
    private fun itemByProgressKey(key: String): StudyItem? = when (currentMode) {
        StudyMode.CHINESE -> repository.byHanzi(key)?.let { ChineseStudyItem(it) }
        StudyMode.ENGLISH -> when (val found = englishRepository.findByProgressKey(key)) {
            is com.studyword.literacy.model.EnglishLetter -> EnglishLetterItem(found)
            is com.studyword.literacy.model.EnglishWord -> EnglishWordItem(found)
            else -> null
        }
    }

    private fun buildCurrentPool(): List<StudyItem> = when (currentMode) {
        StudyMode.CHINESE -> repository.byDifficulty(currentDifficulty).map { ChineseStudyItem(it) }
        StudyMode.ENGLISH -> {
            val rawItems: List<Any> = when (englishSubMode) {
                EnglishSubMode.LETTERS -> englishRepository.byCategory(EnglishCategory.LETTERS)
                EnglishSubMode.WORDS -> englishRepository.byCategoryAndDifficulty(EnglishCategory.WORDS, currentDifficulty)
            }
            rawItems.map { item ->
                when (item) {
                    is com.studyword.literacy.model.EnglishLetter -> EnglishLetterItem(item)
                    is com.studyword.literacy.model.EnglishWord -> EnglishWordItem(item)
                    else -> null
                }
            }.filterNotNull()
        }
    }

    /** 返回当前语种对应的 known/unknown 集合(可变引用,供 ProgressRules 就地改写) */
    private fun currentKnownUnknown(): Pair<MutableSet<String>, MutableSet<String>> = when (currentMode) {
        StudyMode.CHINESE -> knownIds to unknownIds
        StudyMode.ENGLISH -> englishKnownIds to englishUnknownIds
    }

    private fun loadNextItem(requeueCurrent: Boolean = false) {
        val previous = currentItem
        if (requeueCurrent && previous != null) {
            pendingItems.addLast(previous)
        }

        if (pendingItems.isEmpty()) {
            rebuildQueue()
        }

        // v1.6.0:经 setCurrentItem,以便统一记录滑动历史
        setCurrentItem(if (pendingItems.isEmpty()) null else pendingItems.removeFirst())

        updateCurrentItemView(currentItem)
        updateSummaryHint()
    }

    // ============================================================
    // v1.6.0:卡片左右滑动切换
    // ============================================================

    /**
     * 向左滑 = 下一张,向右滑 = 上一张。
     *
     * 只接管**横向**手势:横向位移必须超过 [swipeMinDistancePx] 且明显大于纵向位移,
     * 否则卡片内容一多就没法上下滚动了。
     * 监听器返回 false(不消费事件),纵向滚动仍然交给 NestedScrollView。
     */
    private fun setupCardSwipe() {
        cardSwipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) < swipeMinDistancePx || abs(dx) < abs(dy) * 1.5f) return false
                if (dx < 0) goToNextCard() else goToPreviousCard()
                return true
            }
        })
    }

    /**
     * v1.6.0:在 **Activity 层**接手势,而不是给某个 View 挂 OnTouchListener。
     *
     * 原因:卡片里的字卡/词组 chip 都带点击监听,手指落在它们上面时子 View 会成为
     * touch target,父容器的 OnTouchListener 就再也收不到事件了 ——
     * 实测挂在 homeScrollView 上时,滑动完全无效(卡片一直不变)。
     * 在 dispatchTouchEvent 里旁路一份,才能保证任何位置滑动手势都能识别。
     *
     * 这里**不消费事件**(不改变返回值),纵向滚动/点击照常工作。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (cardSwipeDetector != null && !isDrawerOpen()) {
            cardSwipeDetector?.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isDrawerOpen(): Boolean =
        binding.drawerLayout.isDrawerOpen(GravityCompat.END)

    /** 下一张:若刚从历史回退过,先沿原路前进 */
    private fun goToNextCard() {
        if (cardForward.isNotEmpty()) {
            val next = cardForward.removeLast()
            setCurrentItem(next)
            updateCurrentItemView(next)
            updateSummaryHint()
            return
        }
        loadNextItem()
    }

    /** 上一张:已经是第一张时给一句提示,而不是静默无反应 */
    private fun goToPreviousCard() {
        val previous = cardHistory.removeLastOrNull()
        if (previous == null) {
            Toast.makeText(this, getString(R.string.swipe_no_previous), Toast.LENGTH_SHORT).show()
            return
        }
        currentItem?.let { cardForward.addLast(it) }
        setCurrentItem(previous, recordHistory = false)
        updateCurrentItemView(previous)
        updateSummaryHint()
    }

    // ============================================================
    // 字卡渲染(StudyItem 统一)
    // ============================================================

    private fun updateCurrentItemView(item: StudyItem?) {
        // v1.6.0:切卡片时把滚动位置复位到顶部。
        // 否则上一张卡片若被滚到中间,下一张会"继承"这个滚动位置 ——
        // 表现为卡片看起来停在中间而不是最高处,顶部内容被切掉。
        binding.homeScrollView.scrollTo(0, 0)
        if (item == null) {
            binding.currentCharacter.isVisible = false
            binding.letterContainer.isVisible = false
            binding.uppercaseText.text = ""
            binding.lowercaseText.text = ""
            binding.wordMeaning.isVisible = false
            binding.wordMeaning.text = ""
            binding.currentPinyin.text = ""
            // v1.4.3:卡片内不再显示难度 chip(已在 topLabel 显示),避免重复
            binding.remainingHint.text = when (currentMode) {
                StudyMode.CHINESE -> getString(R.string.card_empty_chinese)
                StudyMode.ENGLISH -> getString(R.string.card_empty_english)
            }
            binding.cardEmoji.text = getString(R.string.card_emoji_idle)
            setActionButtonsEnabled(false)
            renderWordsAndExamples(null)
            return
        }

        val isLetter = item is EnglishLetterItem
        val isWord = item is EnglishWordItem
        binding.currentCharacter.isVisible = !isLetter
        binding.letterContainer.isVisible = isLetter
        binding.wordMeaning.isVisible = isWord
        if (isLetter) {
            val letter = (item as EnglishLetterItem).letter
            binding.uppercaseText.text = letter.uppercase
            binding.lowercaseText.text = letter.lowercase
        } else {
            binding.currentCharacter.text = item.primaryText
            // v1.6.0:按长度给主文字设字号上限(详见函数注释)
            applyCharacterTextSize(item.primaryText, isWord)
            if (isWord) {
                // 单词模式下显示中文意思,字号比 currentPinyin 略大,作为显眼释义
                binding.wordMeaning.text = (item as EnglishWordItem).word.chineseMeaning
            }
        }

        binding.currentPinyin.text = item.secondaryText.ifBlank { "--" }

        binding.cardEmoji.text = if (item is EnglishWordItem) "🔤" else mascotFaces[random.nextInt(mascotFaces.size)]

        setActionButtonsEnabled(true)
        renderWordsAndExamples(item)
    }
    /**
     * v1.6.0:按内容给卡片主文字设字号上限。
     *
     * 为什么"宽度自动缩字"还不够
     * ------------------------
     * autoSize 只保证**不超出宽度**,不管高度。例如 "banana"(6 字母)
     * 在约 340dp 的可用宽度里能排到 ~96sp,行高就有 ~125dp ——
     * 卡片被撑高、内容超出一屏,例句被挤出屏幕。
     *
     * 而主页现在要求"卡片固定不动"(FixedHomeScrollView 在内容放得下时
     * 完全不响应拖拽),那就**必须先保证内容真的放得下一屏**。
     * 单词不像单个汉字需要那么大,按长度给上限后各卡片高度就都可控了。
     */
    private fun applyCharacterTextSize(text: String, isWord: Boolean) {
        val minSp: Int
        val maxSp: Int
        if (!isWord) {
            // 中文单字:保持原有大字号,一个字的宽度远小于容器,不会溢出
            minSp = 40
            maxSp = 100
        } else {
            minSp = 24
            maxSp = when (text.length) {
                in 0..3 -> 88      // cat / dog
                in 4..5 -> 72      // apple / water
                in 6..7 -> 58      // banana / brother
                else -> 46         // elephant / umbrella
            }
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            binding.currentCharacter, minSp, maxSp, 2, TypedValue.COMPLEX_UNIT_SP
        )
    }

    /**
     * 渲染词组 + 例句。
     * v1.4.0 改进:
     * - 英文 letter 的 exampleWord 后面加上中文意思 chip(不可点,作为展示)
     * - 例句点击用 speakExampleSentence(英文 + 中文翻译)
     */
    private fun renderWordsAndExamples(item: StudyItem?) {
        val wordsGroup = binding.wordsChipGroup
        val exampleView = binding.exampleSentence
        val wordsDivider = binding.wordsDivider
        val wordsLabel = binding.wordsLabel
        val examplesLabel = binding.examplesLabel

        wordsGroup.removeAllViews()
        exampleView.setOnClickListener(null)
        exampleView.text = ""

        val words = item?.words.orEmpty()
        val examples = item?.examples.orEmpty()
        val isEnglish = item?.ttsLocale == "en"

        if (words.isEmpty() && examples.isEmpty()) {
            wordsGroup.isVisible = false
            exampleView.isVisible = false
            wordsDivider.isVisible = false
            wordsLabel.isVisible = false
            examplesLabel.isVisible = false
            return
        }

        if (words.isNotEmpty()) {
            wordsDivider.isVisible = true
            wordsLabel.isVisible = true
            wordsGroup.isVisible = true
            wordsLabel.text = getString(
                if (isEnglish) R.string.words_label_en else R.string.words_label
            )
            val inflater = LayoutInflater.from(this)
            words.forEachIndexed { index, entry ->
                val chip = inflater.inflate(R.layout.item_word_chip, wordsGroup, false) as Chip
                chip.text = entry.word
                chip.setOnClickListener {
                    // v1.4.1:letter 模式下示例词 chip 点 → 英文示例词 + 中文意思(500ms 停顿)
                    // 其他情况保持原行为(英文 speakEnglish / 中文 char-by-char)
                    if (item is EnglishLetterItem && item.letter.exampleWord.isNotBlank()) {
                        TtsManager.speakSequential(
                            items = buildList {
                                add(entry.word to true)
                                if (item.letter.exampleWordChinese.isNotBlank()) {
                                    add(item.letter.exampleWordChinese to false)
                                }
                            },
                            delayMs = 500,
                            baseUtteranceId = "main_letter_example_${item.id}"
                        )
                    } else {
                        speakWordOrSentence(
                            text = entry.word,
                            utteranceId = "main_word_${item?.id ?: 0}_$index",
                            isEnglish = isEnglish
                        )
                    }
                }
                wordsGroup.addView(chip)
            }
            // 英文 letter:在示例词 chip 后追加"中文意思"chip(只读,展示)
            if (item is EnglishLetterItem && item.letter.exampleWordChinese.isNotBlank()) {
                val hintChip = inflater.inflate(R.layout.item_word_chip, wordsGroup, false) as Chip
                hintChip.text = item.letter.exampleWordChinese
                hintChip.isClickable = false
                hintChip.isCheckable = false
                wordsGroup.addView(hintChip)
            }
        } else {
            wordsGroup.isVisible = false
            wordsLabel.isVisible = false
            wordsDivider.isVisible = examples.isNotEmpty()
        }

        if (examples.isNotEmpty()) {
            examplesLabel.isVisible = true
            exampleView.isVisible = true
            examplesLabel.text = getString(
                if (isEnglish) R.string.examples_label_en else R.string.examples_label
            )
            val first = examples.first()
            // 英文 word 只显示第一条 + 中文翻译;中文把全部例句换行拼接显示
            val englishOnlyFirst = isEnglish && item?.englishExtra?.isNotBlank() == true
            val displayText = if (englishOnlyFirst) {
                // item 是可空的:把判定抽成变量后编译器不再智能转换,这里用安全调用
                "${first.sentence}\n— ${item?.englishExtra.orEmpty()}"
            } else {
                examples.joinToString(separator = "\n") { it.sentence }
            }
            exampleView.text = displayText
            // v1.6.0:**朗读内容严格跟随显示内容**。
            // 此前固定只传 first.sentence,而中文卡片把 2 条例句都显示了 ——
            // 用户点下去只听到第一句(239 个字受影响)。
            val spoken = if (englishOnlyFirst) listOf(first.sentence)
            else examples.map { it.sentence }
            exampleView.setOnClickListener {
                speakExampleSentence(item, spoken, "main_example_${item?.id ?: 0}")
            }
        } else {
            examplesLabel.isVisible = false
            exampleView.isVisible = false
        }
    }

    private fun updateSummaryHint() {
        // v1.6.0:复习模式下明确提示"现在只出到期的错题",
        // 避免家长误以为字库只剩这几个字
        if (vm.reviewOnly) {
            binding.remainingHint.text = getString(R.string.review_mode_hint)
            return
        }
        when (currentMode) {
            StudyMode.CHINESE -> {
                val total = repository.count()
                val known = knownIds.size
                val unknown = unknownIds.size
                val untested = total - known - unknown
                binding.remainingHint.text = getString(
                    R.string.hint_chinese_format,
                    known,
                    total,
                    unknown,
                    untested.coerceAtLeast(0)
                )
            }
            StudyMode.ENGLISH -> {
                val total = englishRepository.count()
                val known = englishKnownIds.size
                val unknown = englishUnknownIds.size
                val untested = total - known - unknown
                binding.remainingHint.text = getString(
                    R.string.hint_english_format,
                    known,
                    total,
                    unknown,
                    untested.coerceAtLeast(0)
                )
            }
        }
    }

    override fun onPause() {
        // 离开页面就停掉正在播的内置语音,避免在后台继续响
        AudioClips.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // v1.6.0:家长可能刚在系统设置里装好中文语音 —— 回到前台自动重试,不必重启 App
        TtsManager.retryIfNeeded(this)
        // v1.6.0:家长在设置里切换了孩子档案 → 必须换用新的 progressStore 并重建队列,
        // 否则会继续读写上一个孩子的进度(每个档案是独立的 SharedPreferences 文件)。
        val currentProfile = ProfileStore(this).activeProfileId()
        if (currentProfile != activeProfileId) {
            activeProfileId = currentProfile
            progressStore = ProgressStore(this, currentProfile)
            currentItem = null
            pendingItems.clear()
        }
        reloadAllProgressFromStore()
        // v1.6.0:每次 resume 都校正"← 返回"按钮(进程恢复 / 跳转回来都能正确显示)
        refreshBackJumpButton()
        updateSummaryHint()
        // 仅在 currentItem 丢失时(首次启动 / 进程被回收)才重建队列 + loadNext。
        // 否则 pageResultLauncher → loadItemById 设置的 currentItem 会被 onResume 的
        // loadNextItem 覆盖,导致跳转失效(显示队列中下一项而非跳转目标)。
        if (currentItem == null) {
            rebuildQueue()
            loadNextItem()
        }
    }

    private fun reloadAllProgressFromStore() {
        knownIds.clear()
        knownIds.addAll(progressStore.loadKnown())
        unknownIds.clear()
        unknownIds.addAll(progressStore.loadUnknown())
        englishKnownIds.clear()
        englishKnownIds.addAll(progressStore.loadEnglishKnown())
        englishUnknownIds.clear()
        englishUnknownIds.addAll(progressStore.loadEnglishUnknown())
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        binding.knowButton.isEnabled = enabled
        binding.unknownButton.isEnabled = enabled
        binding.skipButton.isEnabled = enabled
        binding.playGameButton.isEnabled = enabled
    }

    /**
     * 鼓励气泡。
     *
     * v1.5.0:改由 [ConfettiOverlayView.floatMessage] 绘制 —— 此前本文件与 GameActivity
     * 各写了一份几乎相同的粒子/浮字动画。
     */
    private fun showEncourageSparkle() {
        val message = ENCOURAGE_MESSAGES[random.nextInt(ENCOURAGE_MESSAGES.size)]
        binding.confettiOverlay.floatMessage(message, binding.remainingHint)
    }

    /**
     * 撒花。
     *
     * v1.5.0:改由 [ConfettiOverlayView.burst] 绘制,锚点为字卡中心。
     */
    private fun showConfetti(onEnd: () -> Unit) {
        binding.confettiOverlay.burst(
            spec = ConfettiOverlayView.celebrateSpec(),
            anchor = binding.currentCharacterCard,
            onEnd = { if (!isFinishing && !isDestroyed) onEnd() }
        )
    }

    private fun playSuccessSound() {
        var player = successPlayer
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.success)
            player?.setOnCompletionListener { mp -> mp.seekTo(0) }
            successPlayer = player
        }
        player?.let {
            if (it.isPlaying) {
                it.seekTo(0)
            }
            it.start()
        }
    }

    private fun playEncourageSound() {
        var player = encouragePlayer
        if (player == null) {
            player = MediaPlayer.create(this, R.raw.fail)
            player?.setOnCompletionListener { mp -> mp.seekTo(0) }
            encouragePlayer = player
        }
        player?.let {
            if (it.isPlaying) {
                it.seekTo(0)
            }
            it.start()
        }
    }

    private enum class ItemResult { KNOWN, UNKNOWN }

    companion object {
        private const val TAG = "MainActivity"

        /** 滑动历史栈上限,防止长时间使用后无限增长 */
        private const val HISTORY_MAX = 50

        /** v1.5.0:鼓励文案(原先硬编码在 showEncourageSparkle 内) */
        private val ENCOURAGE_MESSAGES = listOf("继续加油！", "还差一点点", "我们一起努力", "Try again!")

        /** ProgressActivity / CharacterLibraryActivity setResult 时填入的 extras */
        const val EXTRA_SELECTED_ID = "selected_id"
        const val EXTRA_SELECTED_LANG = "selected_lang"  // "CHINESE" / "ENGLISH"
        /** v1.4.2:跳转来源 — 主页收到后可显示"← 返回"按钮跳回源 Activity */
        const val EXTRA_SOURCE_PAGE = "source_page"
        /** v1.4.4:源页内 chip 主动 setResult + finish 时,主页清掉 jumpSource,不显示"← 返回"按钮 */
        const val SOURCE_PROGRESS = "progress"
        const val SOURCE_LIBRARY = "library"
    }
}
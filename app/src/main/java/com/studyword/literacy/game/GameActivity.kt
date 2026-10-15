package com.studyword.literacy.game

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.R
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityGameBinding
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.ProgressKey
import com.studyword.literacy.model.ProgressRules
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode
import com.studyword.literacy.ui.ConfettiOverlayView
import com.studyword.literacy.ui.GameViewModel
import com.studyword.literacy.util.AudioClips
import com.studyword.literacy.util.applyStatusBarTopPadding
import com.studyword.literacy.util.TtsManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 游戏模式主页面(中英文识字闯关)
 *
 * v1.3.0 起支持英文题型:
 * - LISTEN_LETTER:听字母名,4 个 Aa 字母卡里选
 * - LISTEN_WORD:听单词,4 个英文单词里选
 *
 * 入口:主页 "🎮 玩游戏" 按钮
 * 流程:5 题一轮,单选 4 选 1,星星评级
 */
class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    /**
     * v1.5.0:整轮状态交给 [GameViewModel]。此前 round 是 Activity 字段,
     * 旋转屏幕即丢失 → 5 题一轮直接作废重开。
     */
    private val vm: GameViewModel by lazy { ViewModelProvider(this)[GameViewModel::class.java] }

    private var mode: GameMode
        get() = vm.mode
        set(value) { vm.mode = value }

    private var language: StudyMode
        get() = vm.language
        set(value) { vm.language = value }

    private var difficulty: Difficulty
        get() = vm.difficulty
        set(value) { vm.difficulty = value }

    private var round: GameRound?
        get() = vm.round
        set(value) { vm.round = value }

    private val random = Random(System.currentTimeMillis())
    private val optionButtons: List<MaterialButton> by lazy {
        listOf(binding.optionA, binding.optionB, binding.optionC, binding.optionD)
    }

    /** v1.4.0:答错时短震动反馈(API 31+ 用 VibratorManager) */
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore.active(this)
        TtsManager.init(this)
        AudioClips.init(this)
        setupSystemBarInsets()

        // v1.5.0:仅在首次创建时解析入口参数;
        // 旋转重建时 ViewModel 仍持有整轮进度,重开会让已答的题作废。
        if (savedInstanceState == null) {
            mode = GameMode.fromName(intent.getStringExtra(EXTRA_MODE))
            language = mode.language
            difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)
                ?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
                ?: Difficulty.EASY
            round = null
        }

        setupModeChips()
        setupDifficultyChips()
        setupActions()
        // 有存活的一轮就恢复现场(可能停在某题或已到结算页),否则开新一轮
        if (round == null) {
            startNewRound()
        } else {
            showCurrentQuestion()
        }
    }

    override fun onDestroy() {
        AudioClips.stop()
        // v1.5.0:清理挂起的延迟任务。
        // 此前 onDestroy 为空,导致 700ms 的朗读与 800ms 的"进入下一题"在退出后仍会执行:
        // 孩子按返回后喇叭还在念、回合还在后台推进。
        pendingRunnables.forEach { binding.root.removeCallbacks(it) }
        pendingRunnables.clear()
        TtsManager.stop()
        super.onDestroy()
    }

    /** v1.5.0:已排期但尚未执行的延迟任务,便于 onDestroy 逐个取消 */
    private val pendingRunnables = mutableListOf<Runnable>()

    /**
     * v1.5.0:延迟执行,且只在 Activity 仍存活时生效。
     * 与 [onDestroy] 中的 removeCallbacks 形成双保险。
     */
    private fun postDelayedIfAlive(delayMs: Long, action: () -> Unit) {
        val runnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            action()
        }
        pendingRunnables += runnable
        binding.root.postDelayed(runnable, delayMs)
    }

    // ============================================================
    // 顶部控件初始化
    // ============================================================

    private fun setupModeChips() {
        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            mode = when (id) {
                binding.chipListen.id -> GameMode.LISTEN
                binding.chipPinyin.id -> GameMode.PINYIN
                binding.chipListenLetter.id -> GameMode.LISTEN_LETTER
                binding.chipListenWord.id -> GameMode.LISTEN_WORD
                else -> GameMode.LISTEN
            }
            language = mode.language
            startNewRound()
        }
        // 根据入口 mode 设置初始选中,并按语种显隐 chip
        when (mode) {
            GameMode.LISTEN -> binding.chipListen.isChecked = true
            GameMode.PINYIN -> binding.chipPinyin.isChecked = true
            GameMode.LISTEN_LETTER -> binding.chipListenLetter.isChecked = true
            GameMode.LISTEN_WORD -> binding.chipListenWord.isChecked = true
        }
        applyLanguageVisibility()
    }

    private fun setupDifficultyChips() {
        // 英文模式没有难度,隐藏 chip 组
        binding.difficultyChipGroup.isVisible = language == StudyMode.CHINESE
        binding.difficultyChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            difficulty = when (id) {
                binding.chipEasy.id -> Difficulty.EASY
                binding.chipMedium.id -> Difficulty.MEDIUM
                binding.chipHard.id -> Difficulty.HARD
                else -> Difficulty.EASY
            }
            startNewRound()
        }
        when (difficulty) {
            Difficulty.EASY -> binding.chipEasy.isChecked = true
            Difficulty.MEDIUM -> binding.chipMedium.isChecked = true
            Difficulty.HARD -> binding.chipHard.isChecked = true
        }
    }

    private fun applyLanguageVisibility() {
        val isEnglish = language == StudyMode.ENGLISH
        binding.chipListen.isVisible = !isEnglish
        binding.chipPinyin.isVisible = !isEnglish
        binding.chipListenLetter.isVisible = isEnglish
        binding.chipListenWord.isVisible = isEnglish
    }

    /**
     * v1.6.0:系统栏避让。
     *
     * 起因:游戏页左上角返回箭头点了没反应 —— 主题里 statusBarColor 是透明的、
     * 内容全屏绘制,而根布局没有避让,返回箭头正好落在状态栏区域里:
     * 看得见,但点击被状态栏窗口吃掉。主页面一直有避让,游戏页漏了。
     */
    private fun setupSystemBarInsets() {
        binding.root.applyStatusBarTopPadding()
    }

    private fun setupActions() {
        binding.backButton.setOnClickListener { finish() }
        binding.skipButton.setOnClickListener { skipCurrent() }
        binding.speakPromptButton.setOnClickListener { speakCurrentPrompt() }
        binding.replayButton.setOnClickListener { startNewRound() }
        binding.exitButton.setOnClickListener { finish() }
        optionButtons.forEach { btn ->
            btn.setOnClickListener { onOptionTapped(btn) }
        }
    }

    // ============================================================
    // 出题与渲染
    // ============================================================

    private fun startNewRound() {
        val pool = buildStudiedPool()
        if (pool.size < 4) {
            showEmptyState()
            return
        }

        val questions = buildQuestions(pool, count = QUESTIONS_PER_ROUND)
        round = GameRound(mode, difficulty.takeIf { language == StudyMode.CHINESE }, questions)

        binding.resultCard.isVisible = false
        binding.questionCard.isVisible = true
        binding.optionsGrid.isVisible = true
        binding.skipButton.isVisible = true
        binding.modeChipGroup.isVisible = true

        showCurrentQuestion()
    }

    /**
     * 题池 = (当前语言 ∩ 已学过的项)。
     * 中文:byDifficulty ∩ (known + unknown)
     * 英文:letters + words ∩ (known + unknown),按 mode 进一步过滤 letter / word
     */
    private fun buildStudiedPool(): List<StudyItem> {
        val known: Set<String>
        val unknown: Set<String>
        return when (language) {
            StudyMode.CHINESE -> {
                known = progressStore.loadKnown()
                unknown = progressStore.loadUnknown()
                val studied = known + unknown
                // v1.5.0:按内容键过滤(进度集合元素是汉字本身)
                repository.byDifficulty(difficulty)
                    .map { ChineseStudyItem(it) }
                    .filter { it.progressKey in studied }
            }
            StudyMode.ENGLISH -> {
                known = progressStore.loadEnglishKnown()
                unknown = progressStore.loadEnglishUnknown()
                val studied = known + unknown
                val allItems = mutableListOf<StudyItem>()
                if (mode == GameMode.LISTEN_LETTER) {
                    allItems += englishRepository.letters()
                        .map { EnglishLetterItem(it) }
                        .filter { it.progressKey in studied }
                } else if (mode == GameMode.LISTEN_WORD) {
                    allItems += englishRepository.words()
                        .map { EnglishWordItem(it) }
                        .filter { it.progressKey in studied }
                }
                allItems
            }
        }
    }

    private fun showEmptyState() {
        round = null
        binding.resultCard.isVisible = true
        binding.questionCard.isVisible = false
        binding.optionsGrid.isVisible = false
        binding.skipButton.isVisible = false
        binding.modeChipGroup.isVisible = false
        binding.difficultyChipGroup.isVisible = false

        binding.resultEmoji.text = getString(R.string.game_empty_emoji)
        binding.resultStars.text = ""
        binding.resultTitle.text = getString(R.string.game_empty_title)
        binding.resultScore.text = getString(
            if (language == StudyMode.ENGLISH) R.string.game_empty_message_en
            else R.string.game_empty_message
        )
        binding.replayButton.isVisible = false
    }

    private fun buildQuestions(
        pool: List<StudyItem>,
        count: Int
    ): List<GameQuestion> {
        // 同题去重:确保每轮 5 题的 correct 不同
        val shuffled = pool.shuffled(random)
        val picked = shuffled.take(count)
        return picked.map { correct ->
            val distractors = pool.filter { it.id != correct.id }
                .shuffled(random)
                .take(3)
            val options = (listOf(correct) + distractors).shuffled(random)
            GameQuestion(correct, options)
        }
    }

    private fun showCurrentQuestion() {
        val r = round ?: return
        if (r.isFinished) {
            showResult()
            return
        }
        val q = r.currentQuestion ?: run { showResult(); return }

        // 进度文本
        binding.progressText.text = getString(
            R.string.game_progress_format,
            r.currentIndex + 1,
            r.totalQuestions
        )

        // 题目区(按 mode 与语种分支)
        when (mode) {
            GameMode.LISTEN -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                binding.speakPromptHint.text = getString(R.string.game_hint_listen)
                // v1.4.3:0.7s 延迟后才发音,让用户先看清楚当前界面再听
                postDelayedIfAlive(700L) { speakCurrentPrompt() }
            }
            GameMode.PINYIN -> {
                binding.speakPromptButton.isVisible = false
                binding.speakPromptHint.isVisible = false
                binding.pinyinPrompt.isVisible = true
                binding.pinyinPrompt.text = q.correct.secondaryText.ifBlank { "?" }
            }
            GameMode.LISTEN_LETTER -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                binding.speakPromptHint.text = getString(R.string.game_hint_listen_letter)
                postDelayedIfAlive(700L) { speakCurrentPrompt() }
            }
            GameMode.LISTEN_WORD -> {
                binding.speakPromptButton.isVisible = true
                binding.speakPromptHint.isVisible = true
                binding.pinyinPrompt.isVisible = false
                binding.speakPromptHint.text = getString(R.string.game_hint_listen_word)
                postDelayedIfAlive(700L) { speakCurrentPrompt() }
            }
        }

        // 选项区(中英文用同一个按钮,只是 text 不同)
        optionButtons.forEachIndexed { index, btn ->
            val opt = q.options.getOrNull(index)
            if (opt == null) {
                btn.isVisible = false
            } else {
                btn.isVisible = true
                btn.text = renderOptionLabel(opt)
                applyOptionAutoSize(btn, opt)
                btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_default)
                btn.isEnabled = true
            }
        }
    }

    /**
     * v1.4.0:根据 item 类型给选项按钮启用合适的字号策略
     * - 中文:固定 44sp(单字刚好)
     * - 英文 letter:由 renderOptionLabel 的 SpannableString 控制,关闭 auto-size
     * - 英文 word:启用 auto-size 14-44sp,长单词自动缩字
     */
    private fun applyOptionAutoSize(btn: MaterialButton, item: StudyItem) {
        when (item) {
            is EnglishWordItem -> {
                // v1.5.0:改用 TextViewCompat ——
                // AppCompatButton 上的 setAutoSizeTextType* 是 @RestrictedApi(lint [RestrictedApi]),
                // 且 TextView.AUTO_SIZE_TEXT_TYPE_NONE 会触发 [WrongConstant]。
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    btn, 14, 44, 1, TypedValue.COMPLEX_UNIT_SP
                )
                btn.maxLines = 1
                btn.ellipsize = null
            }
            else -> {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                    btn, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
                )
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                btn.maxLines = 1
            }
        }
    }

    /**
     * 渲染选项按钮文字:
     * - 中文:汉字(44sp)
     * - 英文 letter:uppercase 60sp + 间隔 + lowercase 36sp(SpannableString 左右分开)
     * - 英文 word:单词(由 auto-size 处理)
     */
    private fun renderOptionLabel(item: StudyItem): CharSequence = when (item) {
        is ChineseStudyItem -> item.character.hanzi
        is EnglishLetterItem -> buildLetterLabel(item.letter.uppercase, item.letter.lowercase)
        is EnglishWordItem -> item.word.word
    }

    /**
     * 大写 60sp 在左、小写 36sp 在右,中间用 2 个空格拉开距离。
     * gap 太大(4 空格)时,某些宽字母(如 W、M)的 "Aa" 会越出按钮右沿,
     * 所以使用紧凑的 2 空格,确保各种字母宽度都能容纳。
     */
    private fun buildLetterLabel(uppercase: String, lowercase: String): CharSequence {
        val gap = "  "
        val text = "$uppercase$gap$lowercase"
        val spannable = SpannableString(text)
        spannable.setSpan(
            AbsoluteSizeSpan(60, true),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            AbsoluteSizeSpan(36, true),
            uppercase.length + gap.length,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun speakCurrentPrompt() {
        val q = round?.currentQuestion ?: return
        speakCorrectAnswer(q, prefix = "game_q")
    }

    /**
     * v1.6.0:朗读正确答案 —— 优先内置语音包,没有再回退系统 TTS。
     *
     * 为什么:部分设备(实测 HarmonyOS 的安卓兼容层)没有 TTS 引擎,
     * 而"听音找字"模式完全依赖发音,不处理就等于该模式不可用。
     * 内置语音包走 MediaPlayer,在那类设备上正常。
     */
    private fun speakCorrectAnswer(q: GameQuestion, prefix: String) {
        val item = q.correct
        val clips = when (item) {
            is ChineseStudyItem -> listOf("c:${item.character.hanzi}")
            is EnglishLetterItem -> listOf("l:${item.letter.uppercase}")
            is EnglishWordItem -> listOf("n:${item.word.word}")
            else -> emptyList()
        }
        if (clips.isNotEmpty() && AudioClips.playSequence(clips)) return
        when (item) {
            is ChineseStudyItem ->
                TtsManager.speak(item.character.hanzi, utteranceId = "${prefix}_${item.id}")
            is EnglishLetterItem ->
                TtsManager.speakEnglish(item.letter.uppercase, utteranceId = "${prefix}_en_${item.id}")
            is EnglishWordItem ->
                TtsManager.speakEnglish(item.word.word, utteranceId = "${prefix}_en_${item.id}")
        }
    }

    // ============================================================
    // 判分
    // ============================================================

    /**
     * v1.6.0:把游戏里的答错计入错题本。
     *
     * 做两件事(与主页面点「再学一次」完全一致):
     * 1. 写错题本(间隔重复状态):盒子归零,累计错误次数 —— 之后会出现在「复习错题」里
     * 2. 标为「待巩固」:从"已认识"移除,避免同一个字既算会了又算错题
     *
     * 只记录错误、不记录正确:游戏是练习场景,答对一次不足以证明已掌握,
     * 由主页面的「认识了」来沉淀掌握状态。
     */
    private fun recordGameMistake(item: StudyItem) {
        val key = item.progressKey
        progressStore.recordWrong(key, System.currentTimeMillis())

        val english = ProgressKey.isEnglish(key)
        val known = (if (english) progressStore.loadEnglishKnown() else progressStore.loadKnown()).toMutableSet()
        val unknown = (if (english) progressStore.loadEnglishUnknown() else progressStore.loadUnknown()).toMutableSet()
        ProgressRules.apply(known, unknown, key, false)
        if (english) {
            progressStore.saveEnglish(known, unknown)
        } else {
            progressStore.save(known, unknown)
        }
    }

    private fun onOptionTapped(btn: MaterialButton) {
        val r = round ?: return
        val q = r.currentQuestion ?: return
        val idx = optionButtons.indexOf(btn)
        val tapped = q.options.getOrNull(idx) ?: return

        // 锁定所有按钮(防多点)
        optionButtons.forEach { it.isEnabled = false }

        val isCorrect = tapped.id == q.correct.id
        r.recordAnswer(isCorrect)

        // v1.6.0:游戏答错要计入错题本。
        // 此前游戏只"读"进度不"写",导致孩子在游戏里反复错的字永远进不了复习队列。
        if (!isCorrect) {
            recordGameMistake(q.correct)
        }

        // v1.5.0:无障碍 —— 答对/答错要能被读屏播报。
        // 此前只换背景色 + 图标,视障用户既看不到颜色变化,也拿不到任何反馈
        // (且答错后按钮被 isEnabled=false 锁死,会移出无障碍树)。
        binding.root.announceForAccessibility(
            getString(if (isCorrect) R.string.announce_correct else R.string.announce_wrong)
        )

        if (isCorrect) {
            btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_correct)
            showMiniConfetti()
        } else {
            btn.background = ContextCompat.getDrawable(this, R.drawable.bg_option_wrong)
            shakeButton(btn)
            vibrateWrong()
            // 高亮正确答案 1.5 秒
            val correctBtn = optionButtons.firstOrNull {
                q.options.getOrNull(optionButtons.indexOf(it))?.id == q.correct.id
            }
            correctBtn?.background = ContextCompat.getDrawable(this, R.drawable.bg_option_correct)
            // 答错时主动念一遍正确答案
            speakCorrectAnswer(q, prefix = "game_wrong")
        }

        // 800ms 后进入下一题
        postDelayedIfAlive(800L) {
            r.advance()
            if (r.isFinished) {
                showResult()
            } else {
                showCurrentQuestion()
            }
        }
    }

    private fun skipCurrent() {
        val r = round ?: return
        r.recordAnswer(false)
        r.advance()
        if (r.isFinished) showResult() else showCurrentQuestion()
    }

    // ============================================================
    // 收尾
    // ============================================================

    private fun showResult() {
        val r = round ?: return
        binding.resultCard.isVisible = true
        binding.questionCard.isVisible = false
        binding.optionsGrid.isVisible = false
        binding.skipButton.isVisible = false
        binding.replayButton.isVisible = true

        val stars = r.stars()
        binding.resultStars.text = "⭐".repeat(stars).ifEmpty { "💧" }
        binding.resultTitle.text = getString(
            when (stars) {
                3 -> R.string.game_stars_3
                2 -> R.string.game_stars_2
                1 -> R.string.game_stars_1
                else -> R.string.game_stars_0
            }
        )
        binding.resultEmoji.text = getString(
            when (stars) {
                3 -> R.string.game_result_emoji_3
                2 -> R.string.game_result_emoji_2
                1 -> R.string.game_result_emoji_1
                else -> R.string.game_result_emoji_0
            }
        )
        binding.resultScore.text = getString(
            R.string.game_score_format,
            r.correctCount,
            r.totalQuestions
        )

        if (stars >= 2) {
            showBigConfetti()
        }
    }

    // ============================================================
    // 撒花动画(简化版,独立实现避免与 MainActivity 强耦合)
    // ============================================================

    /**
     * v1.4.0:答错时按钮左右抖动
     */
    private fun shakeButton(btn: View) {
        ObjectAnimator.ofFloat(
            btn, "translationX",
            0f, -24f, 24f, -18f, 18f, -10f, 10f, 0f
        ).apply {
            duration = 380L
            start()
        }
    }

    /**
     * v1.4.0:答错时短震动(80ms,Android 8+ 用 VibrationEffect,旧版本 fallback long)
     */
    private fun vibrateWrong() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(80L)
        }
    }

    /**
     * 答对的小撒花。
     *
     * v1.5.0:改由共享的 [ConfettiOverlayView] 绘制 ——
     * 此处原先与 MainActivity 各写了一份几乎逐行相同的粒子系统。
     */
    private fun showMiniConfetti() {
        binding.confettiOverlay.burst(ConfettiOverlayView.miniSpec())
    }

    /** 结算的大撒花 */
    private fun showBigConfetti() {
        binding.confettiOverlay.burst(ConfettiOverlayView.bigSpec())
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
        private const val QUESTIONS_PER_ROUND = 5
    }
}

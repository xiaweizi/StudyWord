package com.studyword.literacy.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studyword.literacy.R
import com.studyword.literacy.util.applyStatusBarTopPadding
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivityLibraryBinding
import com.studyword.literacy.model.ChineseStudyItem
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishLetterItem
import com.studyword.literacy.model.EnglishWordItem
import com.studyword.literacy.model.LearnStatus
import com.studyword.literacy.model.LibrarySearch
import com.studyword.literacy.model.ProgressRules
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode

/**
 * 字库浏览(v1.4.0):
 * - 短按 item → setResult(selectedId, lang) + finish,跳回主页对应卡片
 * - 长按 item → 弹状态对话框(原行为,标记 known/unknown/unseen)
 * - 通过 pageResultLauncher(在 MainActivity 中)接住跳转回值
 */
class CharacterLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository
    private lateinit var progressStore: ProgressStore

    private var currentLanguage: StudyMode = StudyMode.CHINESE

    /** v1.6.0:搜索关键词(汉字 / 拼音 / 单词 / 中文意思);空 = 不过滤 */
    private var searchQuery: String = ""

    private var statusFilter: StatusFilter = StatusFilter.ALL
    private var difficultyFilter: DifficultyFilter = DifficultyFilter.ALL

    // ====== 中文维度(v1.5.0:元素是汉字本身) ======
    private var chineseKnownIds: MutableSet<String> = mutableSetOf()
    private var chineseUnknownIds: MutableSet<String> = mutableSetOf()
    private lateinit var chineseAdapter: AllCharactersAdapter

    // ====== 英文维度(v1.5.0:元素是 "L:A" / "W:apple") ======
    private var englishKnownIds: MutableSet<String> = mutableSetOf()
    private var englishUnknownIds: MutableSet<String> = mutableSetOf()
    private lateinit var englishAdapter: AllEnglishItemsAdapter

    private val allChineseItems: List<StudyItem> by lazy {
        repository.all().map { ChineseStudyItem(it) }
    }
    private val allEnglishItems: List<StudyItem> by lazy {
        englishRepository.letters().map { EnglishLetterItem(it) } +
            englishRepository.words().map { EnglishWordItem(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // v1.6.0:顶栏避让状态栏,否则返回箭头被状态栏遮住收不到点击
        binding.root.applyStatusBarTopPadding()

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore.active(this)

        currentLanguage = StudyMode.fromName(progressStore.loadLanguage())

        chineseKnownIds = progressStore.loadKnown()
        chineseUnknownIds = progressStore.loadUnknown()
        englishKnownIds = progressStore.loadEnglishKnown()
        englishUnknownIds = progressStore.loadEnglishUnknown()

        setupToolbar()
        setupRecycler()
        setupLanguageToggle()
        setupFilters()
        renderList()
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { finish() }
    }

    /**
     * v1.4.1:从屏幕左边缘向右滑动超过 120dp 且横向速度 > 纵向速度 2 倍 → finish()
     * 不影响 RecyclerView 上下滚动、chip 点击等其他手势。
     * v1.5.0:实现抽到 [SwipeBackDelegate](与 ProgressActivity 共用)。
     */
    private val swipeBack by lazy { SwipeBackDelegate(this) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeBack.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupLanguageToggle() {
        binding.languageChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentLanguage = when (id) {
                binding.chipLanguageChinese.id -> StudyMode.CHINESE
                binding.chipLanguageEnglish.id -> StudyMode.ENGLISH
                else -> StudyMode.CHINESE
            }
            swapAdapterForLanguage()
            // v1.4.3:难度 filter 始终显示 — 英文 mode 下按 word 难度过滤(letter 始终展示)
            binding.difficultyFilterGroup.isVisible = true
            statusFilter = StatusFilter.ALL
            binding.chipFilterAll.isChecked = true
            renderList()
        }
        when (currentLanguage) {
            StudyMode.CHINESE -> binding.chipLanguageChinese.isChecked = true
            StudyMode.ENGLISH -> binding.chipLanguageEnglish.isChecked = true
        }
        binding.difficultyFilterGroup.isVisible = true
    }

    private fun swapAdapterForLanguage() {
        when (currentLanguage) {
            StudyMode.CHINESE -> {
                binding.characterRecycler.adapter = chineseAdapter
                binding.characterRecycler.layoutManager = GridLayoutManager(this, 4)
            }
            StudyMode.ENGLISH -> {
                binding.characterRecycler.adapter = englishAdapter
                binding.characterRecycler.layoutManager = GridLayoutManager(this, 4)
            }
        }
    }

    private fun setupFilters() {
        // v1.6.0:搜索框 —— 输入即过滤(字库最多 3000 条,实时过滤无压力)
        binding.searchInput.doAfterTextChanged { text: android.text.Editable? ->
            searchQuery = text?.toString().orEmpty()
            renderList()
        }

        binding.statusFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.chipFilterKnown.id -> statusFilter = StatusFilter.KNOWN
                binding.chipFilterUnknown.id -> statusFilter = StatusFilter.UNKNOWN
                binding.chipFilterUnseen.id -> statusFilter = StatusFilter.UNSEEN
                else -> statusFilter = StatusFilter.ALL
            }
            renderList()
        }
        binding.chipFilterAll.isChecked = true

        binding.difficultyFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.chipDifficultyEasy.id -> difficultyFilter = DifficultyFilter.EASY
                binding.chipDifficultyMedium.id -> difficultyFilter = DifficultyFilter.MEDIUM
                binding.chipDifficultyHard.id -> difficultyFilter = DifficultyFilter.HARD
                else -> difficultyFilter = DifficultyFilter.ALL
            }
            renderList()
        }
        binding.chipDifficultyAll.isChecked = true
    }

    private fun setupRecycler() {
        chineseAdapter = AllCharactersAdapter(
            statusProvider = { character -> chineseStatusOf(character.hanzi) },
            onItemClicked = { character -> jumpBackToHome(ChineseStudyItem(character)) },
            onItemLongClicked = { character -> showLongPressDialog(ChineseStudyItem(character)) }
        )
        englishAdapter = AllEnglishItemsAdapter(
            statusProvider = { item -> englishStatusOf(item.progressKey) },
            onItemClicked = { item -> jumpBackToHome(item) },
            onItemLongClicked = { item -> showLongPressDialog(item) }
        )
        swapAdapterForLanguage()
    }

    /**
     * v1.4.0:短按 item → setResult + finish,跳回主页对应卡片
     * v1.4.2:同时把 source=library 发回去,主页可显示"← 返回字库"按钮
     * v1.4.4:同时把 clear_source=true 发回去 — 这是用户主动从源页短按 chip 跳回主页,
     * 主页接收后清掉 jumpSource,不显示"← 返回字库"按钮
     */
    private fun jumpBackToHome(item: StudyItem) {
        val lang = when (item) {
            is ChineseStudyItem -> StudyMode.CHINESE.name
            is EnglishLetterItem, is EnglishWordItem -> StudyMode.ENGLISH.name
        }
        val data = Intent().apply {
            putExtra(MainActivity.EXTRA_SELECTED_ID, item.id)
            putExtra(MainActivity.EXTRA_SELECTED_LANG, lang)
            // v1.6.0:不再发 CLEAR_SOURCE —— 那会让主页把 jumpSource 置空,
            // 导致"← 返回字库"按钮永远不显示
            putExtra(MainActivity.EXTRA_SOURCE_PAGE, MainActivity.SOURCE_LIBRARY)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    /**
     * 长按 item → 弹状态对话框
     */
    private fun showLongPressDialog(item: StudyItem) {
        when (item) {
            is ChineseStudyItem -> showChineseStatusDialog(item)
            else -> showEnglishStatusDialog(item)
        }
    }

    // ============================================================
    // 渲染
    // ============================================================

    private fun renderList() {
        // v1.6.0:把过滤后的**条数**显式带出来给结果提示用。
        // 不能读 adapter.itemCount —— ListAdapter.submitList() 是异步的,
        // 紧接着读到的还是上一轮的旧值(会出现"列表已空却显示找到 3000 条")。
        val shown = when (currentLanguage) {
            StudyMode.CHINESE -> {
                val filtered = allChineseItems
                    .filter { item ->
                        matchesDifficulty(item) && matchesStatus(item) && matchesQueryChinese(item)
                    }
                    .map { (it as ChineseStudyItem).character }
                chineseAdapter.submitList(filtered)
                filtered.size
            }
            StudyMode.ENGLISH -> {
                // v1.4.3:难度 filter 也应用到英文 — letter 不过滤(始终),word 按 difficulty 过滤
                val filtered = allEnglishItems.filter { item ->
                    matchesDifficultyEnglish(item) && matchesStatusEnglish(item) && matchesQueryEnglish(item)
                }
                englishAdapter.submitList(filtered)
                filtered.size
            }
        }
        updateSearchSummary(shown)
    }

    /**
     * v1.6.0:搜索框的关键词过滤。
     *
     * 中文支持**汉字或拼音**;拼音匹配会忽略声调,所以家长输入 `bi` 也能搜到「bǐ」。
     */
    private fun matchesQueryChinese(item: StudyItem): Boolean {
        if (item !is ChineseStudyItem) return true
        return LibrarySearch.matchesChinese(
            hanzi = item.character.hanzi,
            pinyin = item.character.pinyin,
            query = searchQuery
        )
    }

    /** 英文:字母大写/小写/单词/中文意思 任一命中即可 */
    private fun matchesQueryEnglish(item: StudyItem): Boolean {
        val fields = when (item) {
            is EnglishLetterItem -> listOf(
                item.letter.uppercase,
                item.letter.lowercase,
                item.letter.exampleWord,
                item.letter.exampleWordChinese
            )
            is EnglishWordItem -> listOf(
                item.word.word,
                item.word.chineseMeaning,
                item.word.phonetic
            )
            else -> return true
        }
        return LibrarySearch.matchesEnglish(fields, searchQuery)
    }

    /**
     * 搜索时显示"找到 N 条";没有搜索词时不显示。
     *
     * [shown] 由 [renderList] 传入过滤后的实际条数 —— 不能读 adapter.itemCount,
     * 因为 `submitList()` 是异步的,读到的会是上一轮的旧值。
     */
    private fun updateSearchSummary(shown: Int) {
        if (LibrarySearch.isBlankQuery(searchQuery)) {
            binding.searchSummary.visibility = View.GONE
            return
        }
        val count = shown
        binding.searchSummary.visibility = View.VISIBLE
        binding.searchSummary.text = if (count == 0) {
            getString(R.string.library_search_empty)
        } else {
            getString(R.string.library_search_count, count)
        }
    }

    private fun matchesDifficulty(item: StudyItem): Boolean {
        if (item !is ChineseStudyItem) return true
        return when (difficultyFilter) {
            DifficultyFilter.ALL -> true
            DifficultyFilter.EASY -> item.character.difficulty == Difficulty.EASY
            DifficultyFilter.MEDIUM -> item.character.difficulty == Difficulty.MEDIUM
            DifficultyFilter.HARD -> item.character.difficulty == Difficulty.HARD
        }
    }

    /**
     * v1.4.3:难度 filter 也用于英文 mode。
     *
     * v1.6.0 修正:字母只在「全部难度」和「简单」下显示。
     * 数据里字母**没有 difficulty 字段**(只有单词有 EASY/MEDIUM/HARD),
     * 旧实现让字母"始终通过",于是切到「中等」「困难」时列表里仍混着一堆字母 ——
     * 而中等/困难本该只有单词。字母是入门内容,归到「简单」最符合直觉。
     */
    private fun matchesDifficultyEnglish(item: StudyItem): Boolean {
        return when (item) {
            is EnglishLetterItem -> difficultyFilter == DifficultyFilter.ALL ||
                difficultyFilter == DifficultyFilter.EASY
            is EnglishWordItem -> when (difficultyFilter) {
                DifficultyFilter.ALL -> true
                DifficultyFilter.EASY -> item.word.difficulty == Difficulty.EASY
                DifficultyFilter.MEDIUM -> item.word.difficulty == Difficulty.MEDIUM
                DifficultyFilter.HARD -> item.word.difficulty == Difficulty.HARD
            }
            else -> true
        }
    }

    private fun matchesStatus(item: StudyItem): Boolean =
        matchesStatusOf(ProgressRules.statusOf(chineseKnownIds, chineseUnknownIds, item.progressKey))

    private fun matchesStatusEnglish(item: StudyItem): Boolean =
        matchesStatusOf(ProgressRules.statusOf(englishKnownIds, englishUnknownIds, item.progressKey))

    private fun matchesStatusOf(status: LearnStatus): Boolean = when (statusFilter) {
        StatusFilter.ALL -> true
        StatusFilter.KNOWN -> status == LearnStatus.KNOWN
        StatusFilter.UNKNOWN -> status == LearnStatus.UNKNOWN
        StatusFilter.UNSEEN -> status == LearnStatus.UNSEEN
    }

    private fun chineseStatusOf(hanzi: String): CharacterStatus =
        ProgressRules.statusOf(chineseKnownIds, chineseUnknownIds, hanzi).toCharacterStatus()

    private fun englishStatusOf(key: String): CharacterStatus =
        ProgressRules.statusOf(englishKnownIds, englishUnknownIds, key).toCharacterStatus()

    /** v1.5.0:model 层状态 → UI 层状态 */
    private fun LearnStatus.toCharacterStatus(): CharacterStatus = when (this) {
        LearnStatus.KNOWN -> CharacterStatus.KNOWN
        LearnStatus.UNKNOWN -> CharacterStatus.UNKNOWN
        LearnStatus.UNSEEN -> CharacterStatus.UNSEEN
    }

    // ============================================================
    // 状态变更 Dialog(长按)
    // ============================================================

    /**
     * 状态选择对话框(已知 / 待巩固 / 未学习)。
     *
     * v1.5.0:中英文两个版本此前各写一份结构完全相同的 builder,收敛到这里,
     * 差异只剩标题文案与回调。
     */
    private fun showStatusDialog(title: String, onChosen: (CharacterStatus) -> Unit) {
        val options = arrayOf(
            getString(R.string.status_known),
            getString(R.string.status_unknown),
            getString(R.string.status_unseen)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options) { dialog, which ->
                onChosen(
                    when (which) {
                        0 -> CharacterStatus.KNOWN
                        1 -> CharacterStatus.UNKNOWN
                        else -> CharacterStatus.UNSEEN
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChineseStatusDialog(item: ChineseStudyItem) {
        showStatusDialog(getString(R.string.dialog_title_chinese, item.character.hanzi)) { status ->
            updateChineseStatus(item, status)
        }
    }

    private fun showEnglishStatusDialog(item: StudyItem) {
        val label = when (item) {
            is EnglishLetterItem -> "${item.letter.uppercase}${item.letter.lowercase}"
            is EnglishWordItem -> item.word.word
            else -> item.primaryText
        }
        showStatusDialog(getString(R.string.dialog_title_english, label)) { status ->
            updateEnglishStatus(item, status)
        }
    }

    private fun updateChineseStatus(item: ChineseStudyItem, status: CharacterStatus) {
        // v1.5.0:统一走 ProgressRules(已知/待巩固/未学习 三态的互斥处理只写一份)
        ProgressRules.setStatus(
            chineseKnownIds,
            chineseUnknownIds,
            item.progressKey,
            status.toKnownFlag()
        )
        progressStore.save(chineseKnownIds, chineseUnknownIds)
        progressStore.recordSnapshot(chineseKnownIds.size, chineseUnknownIds.size)
        Toast.makeText(
            this,
            getString(R.string.toast_status_updated_chinese, item.character.hanzi),
            Toast.LENGTH_SHORT
        ).show()
        renderList()
    }

    private fun updateEnglishStatus(item: StudyItem, status: CharacterStatus) {
        ProgressRules.setStatus(
            englishKnownIds,
            englishUnknownIds,
            item.progressKey,
            status.toKnownFlag()
        )
        progressStore.saveEnglish(englishKnownIds, englishUnknownIds)
        Toast.makeText(this, getString(R.string.english_status_updated), Toast.LENGTH_SHORT).show()
        renderList()
    }

    /** KNOWN → true;UNKNOWN → false;UNSEEN → null(清除记录) */
    private fun CharacterStatus.toKnownFlag(): Boolean? = when (this) {
        CharacterStatus.KNOWN -> true
        CharacterStatus.UNKNOWN -> false
        CharacterStatus.UNSEEN -> null
    }

    private enum class StatusFilter {
        ALL, KNOWN, UNKNOWN, UNSEEN
    }

    private enum class DifficultyFilter {
        ALL, EASY, MEDIUM, HARD
    }

    companion object {
        /** 边滑返回阈值已收敛到 [SwipeBackDelegate](v1.5.0) */
    }
}
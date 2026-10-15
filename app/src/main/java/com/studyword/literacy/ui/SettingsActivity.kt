package com.studyword.literacy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.studyword.literacy.R
import com.studyword.literacy.util.applyStatusBarTopPadding
import com.studyword.literacy.util.TtsManager
import com.studyword.literacy.data.CharacterRepository
import com.studyword.literacy.data.EnglishRepository
import com.studyword.literacy.data.ProfileStore
import com.studyword.literacy.data.ProgressStore
import com.studyword.literacy.databinding.ActivitySettingsBinding
import com.studyword.literacy.model.ProfileRules
import com.studyword.literacy.model.CsvImportResult
import com.studyword.literacy.model.ProgressCsvImporter
import com.studyword.literacy.model.ProgressKey
import com.studyword.literacy.util.CsvFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var progressStore: ProgressStore
    private lateinit var repository: CharacterRepository
    private lateinit var englishRepository: EnglishRepository

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportProgress(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importProgress(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // v1.6.0:顶栏避让状态栏,否则返回箭头被状态栏遮住收不到点击
        binding.root.applyStatusBarTopPadding()

        repository = CharacterRepository(this)
        englishRepository = EnglishRepository(this)
        progressStore = ProgressStore.active(this)

        binding.topBar.setNavigationOnClickListener { finish() }

        // v1.6.0:语音诊断 —— 远程排查 TTS 问题时让用户截图
        binding.ttsDiagnoseButton.setOnClickListener { showTtsDiagnostics() }

        binding.libraryButton.setOnClickListener {
            startActivity(Intent(this, CharacterLibraryActivity::class.java))
        }

        binding.exportButton.setOnClickListener {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("StudyWord_$date.csv")
        }

        // v1.6.0:CSV 导入(P2-3)。整个设置页已在家长门后面,这里不再重复加门。
        binding.importButton.setOnClickListener {
            // 放宽 mime 类型:不同文件管理器对 .csv 的标注不一致
            // (text/csv、text/comma-separated-values、application/vnd.ms-excel 都见过)
            importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
        }

        // 重置中文进度
        binding.resetChineseButton.setOnClickListener {
            progressStore.resetChinese()
            progressStore.recordSnapshot(0, 0)
            Snackbar.make(binding.root, getString(R.string.reset_chinese_done), Snackbar.LENGTH_LONG).show()
            refreshOverview()
        }

        // 重置英文进度(保留中文)
        binding.resetEnglishButton.setOnClickListener {
            progressStore.resetEnglish()
            Snackbar.make(binding.root, getString(R.string.reset_english_done), Snackbar.LENGTH_LONG).show()
            refreshOverview()
        }

        setupProfiles()
        refreshOverview()
    }

    /**
     * v1.6.0:CSV 导入(P2-3)。
     *
     * 流程:读取 → 解析(纯逻辑,[ProgressCsvImporter]) → **预览确认** → 合并。
     *
     * 为什么必须先预览:导入会改动学习进度,家长需要在动手前看到
     * "到底会应用多少条",而不是点一下就把状态改了。
     *
     * 合并语义(刻意不做覆盖):
     * - 文件里标记为「认识」→ 加入已认识,并从待巩固移除
     * - 文件里标记为「不认识」→ 加入待巩固,并从已认识移除
     * - 文件里「未测试 / new」→ **不改动本地记录**
     * 这样"导入"永远不会让用户现有的进度变少。
     */
    private fun importProgress(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }.orEmpty()
                }
                val result = ProgressCsvImporter.parse(text)
                if (result.isEmpty) {
                    Snackbar.make(binding.root, getString(R.string.import_empty), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                // 过滤掉字库里不存在的键(换过字库、或手改过文件时会遇到),
                // 避免把无效内容键写进进度
                val ck = result.chineseKnown.filter { repository.byHanzi(it) != null }.toSet()
                val cu = result.chineseUnknown.filter { repository.byHanzi(it) != null }.toSet()
                val ek = result.englishKnown.filter { englishRepository.findByProgressKey(it) != null }.toSet()
                val eu = result.englishUnknown.filter { englishRepository.findByProgressKey(it) != null }.toSet()
                val applicable = ck.size + cu.size + ek.size + eu.size

                if (applicable == 0) {
                    Snackbar.make(binding.root, getString(R.string.import_empty), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.import_preview_title)
                    .setMessage(
                        getString(
                            R.string.import_preview_message,
                            profileStore.activeProfile().name,
                            ck.size, cu.size, ek.size, eu.size
                        )
                    )
                    .setNegativeButton(R.string.import_cancel, null)
                    .setPositiveButton(R.string.import_confirm) { _, _ ->
                        applyImport(ck, cu, ek, eu, result)
                    }
                    .show()
            } catch (error: Exception) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.import_fail_format, error.localizedMessage ?: ""),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /** 把已确认的条目合并进当前档案;只增不减,保证"导入不会让进度变少" */
    private fun applyImport(
        chineseKnown: Set<String>,
        chineseUnknown: Set<String>,
        englishKnown: Set<String>,
        englishUnknown: Set<String>,
        parsed: CsvImportResult
    ) {
        val known = progressStore.loadKnown()
        val unknown = progressStore.loadUnknown()
        chineseKnown.forEach { key -> known.add(key); unknown.remove(key) }
        chineseUnknown.forEach { key -> unknown.add(key); known.remove(key) }
        progressStore.save(known, unknown)

        val enKnown = progressStore.loadEnglishKnown()
        val enUnknown = progressStore.loadEnglishUnknown()
        englishKnown.forEach { key -> enKnown.add(key); enUnknown.remove(key) }
        englishUnknown.forEach { key -> enUnknown.add(key); enKnown.remove(key) }
        progressStore.saveEnglish(enKnown, enUnknown)

        // 刻意不调用 recordSnapshot:导入不是一次"学习",让它假装成当天的学习量
        // 会污染掌握率趋势图。
        val applied = chineseKnown.size + chineseUnknown.size + englishKnown.size + englishUnknown.size
        refreshOverview()

        val msg = buildString {
            append(getString(R.string.import_done_format, applied))
            if (parsed.skippedRows > 0) {
                append(" · ")
                append(getString(R.string.import_skipped_format, parsed.skippedRows))
            }
        }
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    private lateinit var profileStore: ProfileStore

    // ============================================================
    // 孩子档案(P2-2 多用户档案)
    // ============================================================

    /**
     * 档案卡片:切换 / 新增 / 重命名 / 删除。
     *
     * 整个设置页已经在[家长门][ParentGate]后面,所以这里不再重复加门。
     * 切换档案后必须**重建 progressStore** —— 每个档案是独立的
     * SharedPreferences 文件,沿用旧的会继续读写上一个孩子的数据。
     */
    private fun setupProfiles() {
        profileStore = ProfileStore(this)

        binding.addProfileButton.setOnClickListener { showNameDialog(creating = true) }
        binding.renameProfileButton.setOnClickListener {
            val active = profileStore.activeProfile()
            showNameDialog(creating = false, initial = active.name)
        }
        binding.deleteProfileButton.setOnClickListener { confirmDeleteActive() }

        refreshProfiles()
    }

    /** 重建档案 chip 列表,并把当前档案设为选中 */
    private fun refreshProfiles() {
        val profiles = profileStore.profiles()
        val activeId = profileStore.activeProfileId()

        binding.profileChipGroup.removeAllViews()
        profiles.forEach { profile ->
            val chip = Chip(this).apply {
                text = profile.name
                id = View.generateViewId()
                isCheckable = true
                isChecked = profile.id == activeId
                tag = profile.id
                setOnClickListener { switchProfile(profile.id) }
            }
            binding.profileChipGroup.addView(chip)
        }

        // 只剩一个档案时不允许删除
        binding.deleteProfileButton.isEnabled = ProfileRules.canDelete(profiles)
        binding.deleteProfileButton.alpha = if (ProfileRules.canDelete(profiles)) 1f else 0.5f
    }

    /** 切换档案:换 progressStore + 刷新总览 */
    private fun switchProfile(id: String) {
        if (id == profileStore.activeProfileId()) return
        profileStore.setActiveProfile(id)
        progressStore = ProgressStore(this, id)
        refreshProfiles()
        refreshOverview()
        Snackbar.make(
            binding.root,
            getString(R.string.profile_switched_format, profileStore.activeProfile().name),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * 新增 / 重命名共用一个输入框对话框。
     * @param creating true = 新增,false = 重命名当前档案
     */
    /**
     * 显示语音引擎探测报告。
     *
     * 起因:用户在鸿蒙手机上 TTS 无声音,而那台设备的系统"文本转语音"页
     * 自己也卡在"默认语言状态:正在检查…"。这个对话框把 App 侧探测到的
     * **逐引擎结果**原样显示出来,便于远程定位。
     */
    private fun showTtsDiagnostics() {
        TtsManager.init(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tts_diagnose_title)
            .setMessage(TtsManager.report())
            .setPositiveButton(R.string.tts_diagnose_redetect) { _, _ ->
                TtsManager.redetect(this)
                Toast.makeText(
                    this, getString(R.string.tts_diagnose_redetecting), Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNameDialog(creating: Boolean, initial: String = "") {
        val input = EditText(this).apply {
            hint = getString(R.string.profile_name_hint)
            setText(initial)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setPadding(56, 32, 56, 32)
        }
        val container = FrameLayout(this).apply {
            val pad = (resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (creating) R.string.profile_add_title else R.string.profile_rename_title)
            .setView(container)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.parent_gate_confirm) { _, _ ->
                val name = ProfileRules.normalizeName(input.text?.toString())
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, getString(R.string.profile_need_name), Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (creating) {
                    val created = profileStore.create(name)
                    // 新建后直接切过去,家长刚建完就是要用这个
                    profileStore.setActiveProfile(created.id)
                    progressStore = ProgressStore(this, created.id)
                    refreshProfiles()
                    refreshOverview()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.profile_created_format, created.name),
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    val active = profileStore.activeProfile()
                    profileStore.rename(active.id, name)
                    refreshProfiles()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.profile_renamed_format, name),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    /** 删除当前档案(含其全部进度),删除前二次确认 */
    private fun confirmDeleteActive() {
        val profiles = profileStore.profiles()
        if (!ProfileRules.canDelete(profiles)) {
            Snackbar.make(binding.root, getString(R.string.profile_delete_last), Snackbar.LENGTH_LONG).show()
            return
        }
        val active = profileStore.activeProfile()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.profile_delete_title, active.name))
            .setMessage(R.string.profile_delete_message)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_delete_confirm) { _, _ ->
                if (profileStore.delete(active.id)) {
                    // 删除后 ProfileStore 会自动切到剩下的第一个档案
                    progressStore = ProgressStore(this, profileStore.activeProfileId())
                    refreshProfiles()
                    refreshOverview()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.profile_deleted_format, active.name),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    /**
     * 刷新字库总览卡片(中英分别)
     */
    private fun refreshOverview() {
        val chineseTotal = repository.count()
        val chineseKnown = progressStore.loadKnown().size
        val chineseUnknown = progressStore.loadUnknown().size
        binding.overviewChinese.text = getString(R.string.overview_chinese_format, chineseTotal)
        binding.overviewChineseProgress.text = getString(
            R.string.overview_chinese_progress_format,
            chineseKnown,
            chineseUnknown
        )

        val letterCount = englishRepository.letterCount()
        val wordCount = englishRepository.wordCount()
        val englishKnown = progressStore.loadEnglishKnown().size
        val englishUnknown = progressStore.loadEnglishUnknown().size
        binding.overviewEnglish.text = getString(R.string.overview_english_format, letterCount, wordCount)
        binding.overviewEnglishProgress.text = getString(
            R.string.overview_english_progress_format,
            englishKnown,
            englishUnknown
        )
    }

    /**
     * 导出 CSV,中英两段拼成同一文件:
     * 段 1:中文 known/unknown 表(兼容 v1.2.1 列)
     * 段 2:English letters + words
     *
     * v1.5.0:单元格统一走 [CsvFormat.cell] 转义(值含逗号/引号/换行时自动加引号)
     */
    private fun exportProgress(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val chineseKnown = progressStore.loadKnown()
                    val chineseUnknown = progressStore.loadUnknown()
                    val englishKnown = progressStore.loadEnglishKnown()
                    val englishUnknown = progressStore.loadEnglishUnknown()
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                            // ====== 段 1:中文 ======
                            writer.appendLine("# Chinese characters")
                            writer.appendLine("汉字,拼音,难度,掌握情况")
                            repository.all().forEach { character ->
                                // v1.5.0:进度集合元素是汉字本身
                                val status = when {
                                    chineseKnown.contains(character.hanzi) -> "认识"
                                    chineseUnknown.contains(character.hanzi) -> "不认识"
                                    else -> "未测试"
                                }
                                writer.appendLine(
                                    listOf(
                                        character.hanzi,
                                        character.pinyin,
                                        character.difficulty.label,
                                        status
                                    ).joinToString(",") { CsvFormat.cell(it) }
                                )
                            }
                            writer.appendLine()

                            // ====== 段 2:English letters ======
                            writer.appendLine("# English letters")
                            writer.appendLine("Letter,Uppercase,Lowercase,Phonetic,Example,Status")
                            englishRepository.letters().forEach { letter ->
                                // v1.5.0:内容键 "L:A"
                                val key = ProgressKey.letter(letter.uppercase)
                                val status = when {
                                    englishKnown.contains(key) -> "known"
                                    englishUnknown.contains(key) -> "review"
                                    else -> "new"
                                }
                                writer.appendLine(
                                    listOf(
                                        letter.letter,
                                        letter.uppercase,
                                        letter.lowercase,
                                        letter.phonetic,
                                        letter.exampleWord,
                                        status
                                    ).joinToString(",") { CsvFormat.cell(it) }
                                )
                            }
                            writer.appendLine()

                            // ====== 段 3:English words ======
                            writer.appendLine("# English words")
                            writer.appendLine("Word,Phonetic,ChineseMeaning,ExampleSentence,Status")
                            englishRepository.words().forEach { word ->
                                // v1.5.0:内容键 "W:apple"
                                val key = ProgressKey.word(word.word)
                                val status = when {
                                    englishKnown.contains(key) -> "known"
                                    englishUnknown.contains(key) -> "review"
                                    else -> "new"
                                }
                                writer.appendLine(
                                    listOf(
                                        word.word,
                                        word.phonetic,
                                        word.chineseMeaning,
                                        word.exampleSentence,
                                        status
                                    ).joinToString(",") { CsvFormat.cell(it) }
                                )
                            }
                        }
                    }
                }
                Snackbar.make(binding.root, getString(R.string.snackbar_export_ok), Snackbar.LENGTH_LONG).show()
            } catch (error: Exception) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.snackbar_export_fail, error.localizedMessage ?: ""),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}

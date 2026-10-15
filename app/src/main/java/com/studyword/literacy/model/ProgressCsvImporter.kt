package com.studyword.literacy.model

import com.studyword.literacy.util.CsvFormat

/**
 * CSV 学习进度导入(v1.6.0 / P2-3)。
 *
 * 设计原则
 * --------
 * 1. **纯逻辑**,不碰 Android API —— 导入最容易出错的就是解析,必须能单测。
 * 2. **按表头取列,不按位置**。导出的列顺序将来可能变(比如再插一列"笔画数"),
 *    按位置读会静默错位,按表头读则要么正确、要么明确报"找不到列"。
 * 3. **认不出来的行跳过并计数**,而不是抛异常让整次导入失败 ——
 *    用户手上可能是从别处改过的文件,不该因为一行坏数据全丢。
 *
 * 兼容 [com.studyword.literacy.ui.SettingsActivity] 导出的三段式格式:
 * ```
 * # Chinese characters
 * 汉字,拼音,难度,掌握情况
 * 一,yī,简单,认识
 * # English letters
 * Letter,Uppercase,Lowercase,Phonetic,Example,Status
 * A,A,a,/eɪ/,Apple,known
 * # English words
 * Word,Phonetic,ChineseMeaning,ExampleSentence,Status
 * cat,/kæt/,猫,I love my cat.,known
 * ```
 */
data class CsvImportResult(
    val chineseKnown: Set<String> = emptySet(),
    val chineseUnknown: Set<String> = emptySet(),
    val englishKnown: Set<String> = emptySet(),
    val englishUnknown: Set<String> = emptySet(),
    /** 认不出来的行数(字段不足 / 状态值无法识别) */
    val skippedRows: Int = 0,
    /** 没有 `#` 段落标记且无法推断段落的行数 */
    val orphanRows: Int = 0
) {
    val isEmpty: Boolean
        get() = chineseKnown.isEmpty() && chineseUnknown.isEmpty() &&
            englishKnown.isEmpty() && englishUnknown.isEmpty()

    val total: Int
        get() = chineseKnown.size + chineseUnknown.size + englishKnown.size + englishUnknown.size
}

object ProgressCsvImporter {

    private enum class Section { NONE, CHINESE, ENGLISH_LETTERS, ENGLISH_WORDS }

    // 状态词的容错:导出用中文/英文各一套,但用户可能把两套混着用
    private val KNOWN_WORDS = setOf("认识", "已认识", "known", "k", "1", "true")
    private val UNKNOWN_WORDS = setOf("不认识", "待巩固", "review", "r", "0", "false")
    private val IGNORED_WORDS = setOf("未测试", "未学习", "new", "n", "")

    // 表头里出现的列名 → 用途
    private val HANZI_HEADERS = setOf("汉字", "字", "hanzi", "character")
    private val LETTER_HEADERS = setOf("uppercase", "大写", "letter", "字母")
    private val WORD_HEADERS = setOf("word", "单词")
    private val STATUS_HEADERS = setOf("掌握情况", "状态", "status")

    fun parse(text: String): CsvImportResult {
        val rows = CsvFormat.parseAll(text)

        val chineseKnown = linkedSetOf<String>()
        val chineseUnknown = linkedSetOf<String>()
        val englishKnown = linkedSetOf<String>()
        val englishUnknown = linkedSetOf<String>()
        var skipped = 0
        var orphan = 0

        var section = Section.NONE
        // 当前段落的列下标;null 表示还没读到表头
        var keyIndex: Int? = null
        var statusIndex: Int? = null
        var seenHeader = false

        for (raw in rows) {
            val cells = raw.map { it.trim() }

            // ---- 段落标记 ----
            val first = cells.firstOrNull().orEmpty()
            if (first.startsWith("#")) {
                val title = cells.joinToString(" ").lowercase()
                section = when {
                    title.contains("chinese") -> Section.CHINESE
                    title.contains("letter") -> Section.ENGLISH_LETTERS
                    title.contains("word") -> Section.ENGLISH_WORDS
                    else -> Section.NONE
                }
                keyIndex = null
                statusIndex = null
                seenHeader = false
                continue
            }

            // ---- 表头行:据此定位列 ----
            if (!seenHeader) {
                val lower = cells.map { it.lowercase() }
                val keyCol = when (section) {
                    Section.CHINESE -> lower.indexOfFirst { it in HANZI_HEADERS }
                    Section.ENGLISH_LETTERS -> lower.indexOfFirst { it in LETTER_HEADERS }
                    Section.ENGLISH_WORDS -> lower.indexOfFirst { it in WORD_HEADERS }
                    Section.NONE -> -1
                }
                val statusCol = lower.indexOfFirst { it in STATUS_HEADERS }
                if (keyCol >= 0 && statusCol >= 0) {
                    keyIndex = keyCol
                    statusIndex = statusCol
                    seenHeader = true
                    continue
                }
                // 不是表头就当数据行继续往下走(有些文件省略表头)
            }

            // ---- 数据行 ----
            if (section == Section.NONE) {
                orphan++
                continue
            }
            val k = keyIndex
            val s = statusIndex
            if (k == null || s == null || cells.size <= maxOf(k, s)) {
                skipped++
                continue
            }
            val rawKey = cells[k]
            if (rawKey.isEmpty()) {
                skipped++
                continue
            }
            val status = cells[s].lowercase()

            when (section) {
                Section.CHINESE -> when {
                    status in KNOWN_WORDS -> { chineseKnown += rawKey; chineseUnknown -= rawKey }
                    status in UNKNOWN_WORDS -> { chineseUnknown += rawKey; chineseKnown -= rawKey }
                    status in IGNORED_WORDS -> Unit          // "未测试" 不改动本地记录
                    else -> skipped++
                }
                Section.ENGLISH_LETTERS -> {
                    val key = ProgressKey.letter(rawKey)
                    when {
                        status in KNOWN_WORDS -> { englishKnown += key; englishUnknown -= key }
                        status in UNKNOWN_WORDS -> { englishUnknown += key; englishKnown -= key }
                        status in IGNORED_WORDS -> Unit
                        else -> skipped++
                    }
                }
                Section.ENGLISH_WORDS -> {
                    val key = ProgressKey.word(rawKey)
                    when {
                        status in KNOWN_WORDS -> { englishKnown += key; englishUnknown -= key }
                        status in UNKNOWN_WORDS -> { englishUnknown += key; englishKnown -= key }
                        status in IGNORED_WORDS -> Unit
                        else -> skipped++
                    }
                }
                Section.NONE -> orphan++
            }
        }

        return CsvImportResult(
            chineseKnown = chineseKnown,
            chineseUnknown = chineseUnknown,
            englishKnown = englishKnown,
            englishUnknown = englishUnknown,
            skippedRows = skipped,
            orphanRows = orphan
        )
    }
}

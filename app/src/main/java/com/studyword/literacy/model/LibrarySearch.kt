package com.studyword.literacy.model

/**
 * 字库搜索匹配(v1.6.0 / P2-3 字库搜索)。
 *
 * 做成纯函数的原因:搜索的"容错"细节(要不要忽略声调、要不要忽略大小写、
 * 拼音里的空格与隔音符号怎么处理)很容易写错且肉眼难验,必须能单测。
 *
 * 匹配规则
 * --------
 * - **中文**:汉字本身包含关键词,或**拼音**包含关键词
 * - **拼音搜索忽略声调**:家长往往打不出「bǐ」,输入 `bi` 就应该能搜到
 * - **英文**:字母大写/小写/单词/中文意思任一包含关键词,忽略大小写
 */
object LibrarySearch {

    /**
     * 带声调的元音 → 基础字母。覆盖普通话全部声调符号,外加 `ü` → `v`
     * (键盘上没有 ü 的常见替代打法)。
     */
    private val TONE_TO_BASE = mapOf(
        'ā' to 'a', 'á' to 'a', 'ǎ' to 'a', 'à' to 'a',
        'ē' to 'e', 'é' to 'e', 'ě' to 'e', 'è' to 'e',
        'ī' to 'i', 'í' to 'i', 'ǐ' to 'i', 'ì' to 'i',
        'ō' to 'o', 'ó' to 'o', 'ǒ' to 'o', 'ò' to 'o',
        'ū' to 'u', 'ú' to 'u', 'ǔ' to 'u', 'ù' to 'u',
        'ǖ' to 'v', 'ǘ' to 'v', 'ǚ' to 'v', 'ǜ' to 'v',
        'ü' to 'v'
    )

    /**
     * 规范化拼音/关键词:去掉声调符号、去掉空格与隔音符号 `'`、转小写。
     * 例如 `"xiǎo péng yǒu"` → `"xiaopengyou"`。
     */
    fun normalize(s: String): String = buildString {
        for (ch in s) {
            val base = TONE_TO_BASE[ch] ?: ch
            when {
                base == ' ' || base == '\'' || base == '　' -> Unit   // 去掉空白与隔音符号
                else -> append(base.lowercaseChar())
            }
        }
    }

    /** 关键词是否为空(空关键词 = 不过滤) */
    fun isBlankQuery(query: String): Boolean = query.isBlank()

    /**
     * 中文条目是否命中。空关键词一律命中(不过滤)。
     */
    fun matchesChinese(hanzi: String, pinyin: String, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        if (hanzi.contains(q)) return true
        val nq = normalize(q)
        if (nq.isEmpty()) return false
        return normalize(pinyin).contains(nq)
    }

    /**
     * 英文条目是否命中:任一字段包含关键词即可(忽略大小写)。
     */
    fun matchesEnglish(fields: List<String>, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return fields.any { it.lowercase().contains(q) }
    }
}

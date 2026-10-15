package com.studyword.literacy.model

/**
 * 进度主键(v1.5.0)。
 *
 * 进度**不再**按字库里的位置 id 存储,而是按内容键存储:
 * - 中文:汉字本身(`"天"`)
 * - 英文字母:`"L:A"`
 * - 英文单词:`"W:apple"`(小写,大小写不敏感)
 *
 * 这样字库顺序可以任意增删调整,而用户的历史进度天然保持正确。
 * 定义放在 model 层,让 data / ui / game 各层共用同一套键规则,避免各自拼字符串。
 */
object ProgressKey {

    const val LETTER_PREFIX = "L:"
    const val WORD_PREFIX = "W:"

    /** 汉字的进度键:汉字本身 */
    fun chinese(hanzi: String): String = hanzi.trim()

    /** 英文字母的进度键,如 "L:A" */
    fun letter(uppercase: String): String = LETTER_PREFIX + uppercase.trim().uppercase()

    /** 英文单词的进度键,如 "W:apple" */
    fun word(word: String): String = WORD_PREFIX + word.trim().lowercase()

    /** 判断某个键是否为英文键(字母或单词) */
    fun isEnglish(key: String): Boolean =
        key.startsWith(LETTER_PREFIX) || key.startsWith(WORD_PREFIX)
}

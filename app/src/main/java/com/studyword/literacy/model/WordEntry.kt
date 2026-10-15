package com.studyword.literacy.model

/**
 * 由某个汉字组成的词组(如 "天" -> "天空")。
 *
 * 字段:
 * - [word] 词组的汉字字符串(2~4 字居多)
 * - [pinyin] 词组的整体拼音,用空格分隔每个字,方便 TTS 逐字朗读
 */
data class WordEntry(
    val word: String,
    val pinyin: String
)

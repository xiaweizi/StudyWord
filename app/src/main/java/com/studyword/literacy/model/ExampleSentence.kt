package com.studyword.literacy.model

/**
 * 含目标汉字的例句(如 "天" -> "今天天气真好。")。
 *
 * 字段:
 * - [sentence] 例句正文
 * - [pinyin] 例句的整体拼音,用空格分隔每个字,方便 TTS 逐字朗读
 */
data class ExampleSentence(
    val sentence: String,
    val pinyin: String
)

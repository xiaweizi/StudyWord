package com.studyword.literacy.game

import com.studyword.literacy.model.StudyMode

/**
 * 游戏模式(v1.3.0 起新增英文题型)。
 *
 * 中文:
 * - LISTEN  听音找字 —— TTS 朗读汉字,孩子从 4 个汉字里选出听到的字
 * - PINYIN  看拼音选字 —— 屏幕显示拼音,孩子从 4 个汉字里选出对应的字
 *
 * 英文:
 * - LISTEN_LETTER 听字母 —— TTS 朗读字母名,孩子从 4 个 Aa 字母卡里选出听到的字母
 * - LISTEN_WORD   听单词 —— TTS 朗读单词,孩子从 4 个英文单词里选出听到的单词
 *
 * 每个 mode 关联一个 [StudyMode],Activity 据此初始化语言状态与题池。
 */
enum class GameMode(
    val label: String,
    val description: String,
    val language: StudyMode
) {
    LISTEN("听音找字", "听声音,选出对应的汉字", StudyMode.CHINESE),
    PINYIN("看拼音选字", "看拼音,选出对应的汉字", StudyMode.CHINESE),
    LISTEN_LETTER("听字母", "听字母名,选出大小写", StudyMode.ENGLISH),
    LISTEN_WORD("听单词", "听单词,选出对应单词", StudyMode.ENGLISH);

    companion object {
        fun fromName(name: String?): GameMode =
            values().firstOrNull { it.name == name } ?: LISTEN
    }
}

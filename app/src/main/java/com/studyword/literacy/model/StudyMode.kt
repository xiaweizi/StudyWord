package com.studyword.literacy.model

/**
 * Phase 2:主页 / 游戏 / 字库当前所学语种。
 *
 * - [CHINESE] 中文汉字模式(沿用 v1.2.1 行为)
 * - [ENGLISH] 英文字母 + 高频词模式
 *
 * 持久化:通过 [com.studyword.literacy.data.ProgressStore.loadLanguage] / [saveLanguage]
 * 保存到 SharedPreferences,下次启动自动恢复。
 */
enum class StudyMode(val displayName: String) {
    CHINESE("中文"),
    ENGLISH("ENGLISH");

    companion object {
        fun fromName(name: String?): StudyMode =
            values().firstOrNull { it.name == name } ?: CHINESE
    }
}

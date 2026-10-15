package com.studyword.literacy.game

import com.studyword.literacy.model.StudyItem

/**
 * 单个题目(4 选 1)。
 *
 * Phase 2 起改为 [StudyItem] 抽象,统一支持中文 / 英文题型。
 */
data class GameQuestion(
    val correct: StudyItem,
    val options: List<StudyItem>  // 长度 = 4,包含 correct
) {
    /** 选项是否包含某个 id */
    fun contains(id: Int): Boolean = options.any { it.id == id }
}

package com.studyword.literacy.model

enum class Difficulty(val label: String) {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难");
}

data class LearningCharacter(
    val id: Int,
    val hanzi: String,
    val pinyin: String,
    val difficulty: Difficulty,
    /** 由该字组成的常见词组;旧数据缺失时为空列表,保证向前兼容。 */
    val words: List<WordEntry> = emptyList(),
    /** 含该字的简短例句;旧数据缺失时为空列表,保证向前兼容。 */
    val examples: List<ExampleSentence> = emptyList()
)

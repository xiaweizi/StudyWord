package com.studyword.literacy.model

/**
 * 英文学习内容的类型标签。
 *
 * 区别于中文的 [Difficulty](EASY/MEDIUM/HARD)三级,英文维度只有两类:
 * - [LETTERS] 英文字母 A-Z(26 个),作为书写与发音启蒙
 * - [WORDS]  常见高频词(如 cat/dog/apple),作为初步词汇积累
 */
enum class EnglishCategory(val label: String) {
    LETTERS("字母"),
    WORDS("单词");
}

/**
 * 一个英文字母(同时含大小写形态)。
 *
 * v1.4.0 新增 [exampleWordChinese] 用于卡片"点字母 → 读字母+ 示例词 + 中文意思"复合发音。
 */
data class EnglishLetter(
    val id: Int,
    val letter: String,
    val uppercase: String,
    val lowercase: String,
    val phonetic: String,
    val exampleWord: String,
    /** v1.4.0 新增:示例词的中文释义,如 "Apple" → "苹果",用于卡片显示 + 中文 TTS */
    val exampleWordChinese: String = "",
    val category: EnglishCategory = EnglishCategory.LETTERS
)

/**
 * 一个英文单词。
 *
 * v1.4.0 新增:
 * - [difficulty] 三档(简单/中等/困难),用于英文 mode 的难度切换
 * - [exampleSentenceTranslation] 例句的中文翻译,用于"点例句 → 读英文 + 中文翻译"复合发音
 */
data class EnglishWord(
    val id: Int,
    val word: String,
    val phonetic: String,
    val chineseMeaning: String,
    val exampleSentence: String,
    /** 例句的中文翻译,如 "I love my cat." → "我爱我的猫。" */
    val exampleSentenceTranslation: String = "",
    /** 难度(简单/中等/困难),由词汇表 JSON 预定义。字母不分难度,仅单词有 */
    val difficulty: Difficulty = Difficulty.EASY,
    val category: EnglishCategory = EnglishCategory.WORDS
)
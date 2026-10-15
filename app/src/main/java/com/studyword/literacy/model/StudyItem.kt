package com.studyword.literacy.model

/**
 * Phase 2:统一的"学习卡片"抽象。
 *
 * v1.4.0 新增:
 * - [englishExtra]   英文 word 例句的中文翻译(显示在例句下方)
 * - [chineseHint]    英文 letter 的 exampleWord 中文意思(卡片底部展示)
 * - [englishDifficulty] 英文 word 的难度(简单/中等/困难),用于题池过滤
 */
sealed interface StudyItem {
    val id: Int
    /**
     * v1.5.0:进度主键 —— 与字库顺序无关的稳定标识。
     * 中文为汉字本身,英文为 "L:字母" / "W:单词"。持久化与查表一律用它。
     */
    val progressKey: String
    val primaryText: String
    val secondaryText: String
    val tertiaryText: String
    val category: String
    val words: List<WordEntry>
    val examples: List<ExampleSentence>
    val exampleTranslation: String
    val ttsLocale: String  // "cn" 或 "en"
    /** v1.4.0:英文 word 例句的中文翻译(显示 + 中文 TTS) */
    val englishExtra: String get() = ""
    /** v1.4.0:英文 letter 的示例词中文意思(显示 + 中文 TTS) */
    val chineseHint: String get() = ""
    /** v1.4.0:英文 word 的难度(中文无此概念,固定 EASY) */
    val englishDifficulty: Difficulty? get() = null
}

/** 中文汉字 */
data class ChineseStudyItem(val character: LearningCharacter) : StudyItem {
    override val id: Int get() = character.id
    override val progressKey: String get() = ProgressKey.chinese(character.hanzi)
    override val primaryText: String get() = character.hanzi
    override val secondaryText: String get() = character.pinyin
    override val tertiaryText: String get() = ""
    override val category: String get() = character.difficulty.label
    override val words: List<WordEntry> get() = character.words
    override val examples: List<ExampleSentence> get() = character.examples
    override val exampleTranslation: String get() = ""
    override val ttsLocale: String get() = "cn"
}

/** 英文字母 A-Z */
data class EnglishLetterItem(val letter: EnglishLetter) : StudyItem {
    override val id: Int get() = letter.id
    override val progressKey: String get() = ProgressKey.letter(letter.uppercase)
    override val primaryText: String get() = letter.uppercase
    override val secondaryText: String get() = letter.phonetic
    override val tertiaryText: String get() = letter.lowercase
    override val category: String get() = "字母"
    override val words: List<WordEntry>
        get() = if (letter.exampleWord.isNotBlank())
            listOf(WordEntry(word = letter.exampleWord, pinyin = ""))
        else emptyList()
    override val examples: List<ExampleSentence> get() = emptyList()
    override val exampleTranslation: String get() = ""
    override val ttsLocale: String get() = "en"
    override val chineseHint: String get() = letter.exampleWordChinese
}

/** 英文单词 */
data class EnglishWordItem(val word: EnglishWord) : StudyItem {
    override val id: Int get() = word.id
    override val progressKey: String get() = ProgressKey.word(word.word)
    override val primaryText: String get() = word.word
    override val secondaryText: String get() = word.phonetic
    override val tertiaryText: String get() = ""
    override val category: String get() = "单词"
    override val words: List<WordEntry> get() = emptyList()
    override val examples: List<ExampleSentence>
        get() = if (word.exampleSentence.isNotBlank())
            listOf(ExampleSentence(sentence = word.exampleSentence, pinyin = ""))
        else emptyList()
    override val exampleTranslation: String get() = word.chineseMeaning
    override val ttsLocale: String get() = "en"
    override val englishExtra: String get() = word.exampleSentenceTranslation
    override val englishDifficulty: Difficulty get() = word.difficulty
}

/**
 * v1.5.0:把 [EnglishRepository.findByProgressKey] 的返回值(EnglishLetter / EnglishWord)
 * 统一包装成 [StudyItem],供进度页 / 字库页等按内容键还原卡片时复用。
 */
fun Any?.toStudyItem(): StudyItem? = when (this) {
    is EnglishLetter -> EnglishLetterItem(this)
    is EnglishWord -> EnglishWordItem(this)
    else -> null
}
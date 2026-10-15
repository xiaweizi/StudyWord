package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [StudyItem.progressKey] 单元测试(v1.5.0)。
 *
 * UI/游戏/持久化三层都通过这个属性取进度键,必须保证:
 * 1. 中文用汉字、英文用 "L:"/"W:" 前缀,三类不打架
 * 2. 与 [StudyItem.id](位置 id,仍用于页面跳转)解耦
 */
class StudyItemProgressKeyTest {

    private fun character(id: Int, hanzi: String) = LearningCharacter(
        id = id,
        hanzi = hanzi,
        pinyin = "x",
        difficulty = Difficulty.EASY
    )

    private fun letter(id: Int, upper: String) = EnglishLetter(
        id = id,
        letter = upper,
        uppercase = upper,
        lowercase = upper.lowercase(),
        phonetic = "/x/",
        exampleWord = "Xray"
    )

    private fun word(id: Int, text: String) = EnglishWord(
        id = id,
        word = text,
        phonetic = "/x/",
        chineseMeaning = "x",
        exampleSentence = "x"
    )

    @Test
    fun `中文字卡的进度键是汉字`() {
        assertEquals("天", ChineseStudyItem(character(0, "天")).progressKey)
    }

    @Test
    fun `英文字母卡的进度键带 L 前缀`() {
        assertEquals("L:A", EnglishLetterItem(letter(3, "A")).progressKey)
    }

    @Test
    fun `英文单词卡的进度键带 W 前缀且小写`() {
        assertEquals("W:apple", EnglishWordItem(word(1000, "Apple")).progressKey)
    }

    @Test
    fun `进度键与位置 id 无关_id 变化不影响键`() {
        val a = EnglishWordItem(word(1000, "apple")).progressKey
        val b = EnglishWordItem(word(1015, "apple")).progressKey
        assertEquals(a, b)
    }

    @Test
    fun `中文键与英文键不会互相碰撞`() {
        // 汉字 "A" 与字母 A 必须是不同的键,否则中英文进度会串
        val chineseKey = ChineseStudyItem(character(0, "A")).progressKey
        val letterKey = EnglishLetterItem(letter(0, "A")).progressKey
        assertNotEquals(chineseKey, letterKey)
    }

    @Test
    fun `字母键与单词键不会互相碰撞`() {
        val letterKey = EnglishLetterItem(letter(0, "W")).progressKey
        val wordKey = EnglishWordItem(word(1000, "W")).progressKey
        assertNotEquals(letterKey, wordKey)
    }
}

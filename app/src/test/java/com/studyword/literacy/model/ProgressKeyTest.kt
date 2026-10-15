package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProgressKey] 单元测试(v1.5.0)。
 *
 * 这层是"进度不再随字库顺序错位"的地基,键规则一旦漂移就会导致老用户进度丢失。
 */
class ProgressKeyTest {

    @Test
    fun `中文键就是汉字本身`() {
        assertEquals("天", ProgressKey.chinese("天"))
    }

    @Test
    fun `中文键会去掉首尾空白`() {
        assertEquals("天", ProgressKey.chinese("  天  "))
    }

    @Test
    fun `字母键统一转大写并加前缀`() {
        assertEquals("L:A", ProgressKey.letter("A"))
        assertEquals("L:A", ProgressKey.letter("a"))
        assertEquals("L:Z", ProgressKey.letter(" z "))
    }

    @Test
    fun `单词键统一转小写并加前缀`() {
        assertEquals("W:apple", ProgressKey.word("Apple"))
        assertEquals("W:apple", ProgressKey.word("APPLE"))
        assertEquals("W:apple", ProgressKey.word(" apple "))
    }

    @Test
    fun `大小写不同的同一个单词映射到同一个键`() {
        assertEquals(ProgressKey.word("Apple"), ProgressKey.word("apple"))
    }

    @Test
    fun `isEnglish 只认字母与单词前缀`() {
        assertTrue(ProgressKey.isEnglish("L:A"))
        assertTrue(ProgressKey.isEnglish("W:apple"))
        assertFalse(ProgressKey.isEnglish("天"))
        assertFalse(ProgressKey.isEnglish(""))
    }

    @Test
    fun `中文键不会被误判为英文键`() {
        assertFalse(ProgressKey.isEnglish(ProgressKey.chinese("A")))
    }
}

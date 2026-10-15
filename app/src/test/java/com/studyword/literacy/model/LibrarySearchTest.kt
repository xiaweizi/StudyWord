package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LibrarySearch] 单元测试(v1.6.0 / P2-3 字库搜索)。
 *
 * 重点:**拼音搜索要忽略声调** —— 家长通常打不出「bǐ」,输入 `bi` 必须能搜到。
 * 这是"能不能用"的分水岭,肉眼看代码看不出来,必须测。
 */
class LibrarySearchTest {

    // ===================== normalize =====================

    @Test
    fun `去声调`() {
        assertEquals("bi", LibrarySearch.normalize("bǐ"))
        assertEquals("er", LibrarySearch.normalize("èr"))
        assertEquals("nv", LibrarySearch.normalize("nǚ"))
    }

    @Test
    fun `去空格与隔音符号`() {
        assertEquals("xiaopengyou", LibrarySearch.normalize("xiǎo péng yǒu"))
        assertEquals("xian", LibrarySearch.normalize("xi'an"))
    }

    @Test
    fun `转小写`() {
        assertEquals("abc", LibrarySearch.normalize("ABC"))
    }

    @Test
    fun `保留汉字原样`() {
        assertEquals("天", LibrarySearch.normalize("天"))
    }

    // ===================== matchesChinese =====================

    @Test
    fun `汉字包含即命中`() {
        assertTrue(LibrarySearch.matchesChinese("天", "tiān", "天"))
        assertTrue(LibrarySearch.matchesChinese("今天", "jīn tiān", "天"))
    }

    @Test
    fun `拼音无调输入也能命中带调拼音`() {
        assertTrue("输入 bi 应能搜到 bǐ", LibrarySearch.matchesChinese("笔", "bǐ", "bi"))
        assertTrue("输入 er 应能搜到 èr", LibrarySearch.matchesChinese("二", "èr", "er"))
    }

    @Test
    fun `拼音前缀命中`() {
        assertTrue(LibrarySearch.matchesChinese("笔", "bǐ", "b"))
    }

    @Test
    fun `多音节拼音连续输入可命中`() {
        assertTrue(LibrarySearch.matchesChinese("朋友", "péng yǒu", "pengyou"))
        assertTrue(LibrarySearch.matchesChinese("朋友", "péng yǒu", "peng you"))
    }

    @Test
    fun `不相关查询不命中`() {
        assertFalse(LibrarySearch.matchesChinese("天", "tiān", "水"))
        assertFalse(LibrarySearch.matchesChinese("天", "tiān", "zzz"))
    }

    @Test
    fun `空查询一律命中_表示不过滤`() {
        assertTrue(LibrarySearch.matchesChinese("天", "tiān", ""))
        assertTrue(LibrarySearch.matchesChinese("天", "tiān", "   "))
    }

    @Test
    fun `拼音为空的条目只按汉字匹配`() {
        assertFalse(LibrarySearch.matchesChinese("天", "", "tian"))
        assertTrue(LibrarySearch.matchesChinese("天", "", "天"))
    }

    // ===================== matchesEnglish =====================

    @Test
    fun `英文忽略大小写`() {
        val fields = listOf("A", "a", "Apple", "苹果")
        assertTrue(LibrarySearch.matchesEnglish(fields, "apple"))
        assertTrue(LibrarySearch.matchesEnglish(fields, "APPLE"))
        assertTrue(LibrarySearch.matchesEnglish(fields, "a"))
    }

    @Test
    fun `英文可按中文意思搜`() {
        val fields = listOf("cat", "猫")
        assertTrue(LibrarySearch.matchesEnglish(fields, "猫"))
    }

    @Test
    fun `英文不相关查询不命中`() {
        assertFalse(LibrarySearch.matchesEnglish(listOf("cat", "猫"), "dog"))
    }

    @Test
    fun `英文空查询一律命中`() {
        assertTrue(LibrarySearch.matchesEnglish(listOf("cat"), ""))
    }
}

package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProgressCsvImporter] 单元测试(v1.6.0 / P2-3 CSV 导入)。
 *
 * 核心用例是**针对真实导出格式的往返**:导出长什么样,导入就必须认得出。
 * 另外重点覆盖"列顺序变了也不出错"(按表头取列)与"未测试不改动本地记录"。
 */
class ProgressCsvImporterTest {

    /** 与 SettingsActivity.exportProgress 的输出结构一致 */
    private val exported = """
        # Chinese characters
        汉字,拼音,难度,掌握情况
        一,yī,简单,认识
        二,èr,简单,不认识
        三,sān,简单,未测试
        # English letters
        Letter,Uppercase,Lowercase,Phonetic,Example,Status
        A,A,a,/eɪ/,Apple,known
        B,B,b,/biː/,Ball,review
        # English words
        Word,Phonetic,ChineseMeaning,ExampleSentence,Status
        cat,/kæt/,猫,I love my cat.,known
        dog,/dɒɡ/,狗,The dog runs.,review
    """.trimIndent()

    @Test
    fun `完整导出格式可正确往返`() {
        val r = ProgressCsvImporter.parse(exported)

        assertEquals(setOf("一"), r.chineseKnown)
        assertEquals(setOf("二"), r.chineseUnknown)
        // 字母与单词**共用**英文集合 —— 对应 ProgressStore 里 known_english_ids
        // 同时存 "L:A" 与 "W:apple" 的设计
        assertEquals(setOf("L:A", "W:cat"), r.englishKnown)
        assertEquals(setOf("L:B", "W:dog"), r.englishUnknown)
        assertEquals(0, r.skippedRows)
        assertEquals(0, r.orphanRows)
    }

    @Test
    fun `未测试条目不进任何集合_所以不会清掉本地进度`() {
        val r = ProgressCsvImporter.parse(exported)
        assertFalse("未测试不应出现在已认识", r.chineseKnown.contains("三"))
        assertFalse("未测试不应出现在待巩固", r.chineseUnknown.contains("三"))
    }

    @Test
    fun `列顺序变了也能正确解析_按表头取列`() {
        val reordered = """
            # Chinese characters
            掌握情况,难度,拼音,汉字
            认识,简单,yī,一
            不认识,简单,èr,二
        """.trimIndent()
        val r = ProgressCsvImporter.parse(reordered)
        assertEquals(setOf("一"), r.chineseKnown)
        assertEquals(setOf("二"), r.chineseUnknown)
    }

    @Test
    fun `英文状态词大小写不敏感`() {
        val text = """
            # English words
            Word,Phonetic,ChineseMeaning,ExampleSentence,Status
            cat,,, ,KNOWN
            dog,,, ,Review
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertEquals(setOf("W:cat"), r.englishKnown)
        assertEquals(setOf("W:dog"), r.englishUnknown)
    }

    @Test
    fun `中文状态词也接受已认识与待巩固`() {
        val text = """
            # Chinese characters
            汉字,拼音,难度,掌握情况
            一,,,已认识
            二,,,待巩固
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertEquals(setOf("一"), r.chineseKnown)
        assertEquals(setOf("二"), r.chineseUnknown)
    }

    @Test
    fun `无法识别的状态值计入跳过而不抛异常`() {
        val text = """
            # Chinese characters
            汉字,拼音,难度,掌握情况
            一,,,认识
            二,,,随便写的
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertEquals(setOf("一"), r.chineseKnown)
        assertEquals(1, r.skippedRows)
    }

    @Test
    fun `字段数不足的行被跳过`() {
        val text = """
            # Chinese characters
            汉字,拼音,难度,掌握情况
            一,认识
            二,,,认识
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertEquals(setOf("二"), r.chineseKnown)
        assertEquals(1, r.skippedRows)
    }

    @Test
    fun `没有段落标记的行计入 orphan`() {
        val text = """
            一,yī,简单,认识
            二,èr,简单,不认识
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertTrue(r.isEmpty)
        assertEquals(2, r.orphanRows)
    }

    @Test
    fun `同一字同时出现时以最后一次为准`() {
        val text = """
            # Chinese characters
            汉字,拼音,难度,掌握情况
            一,,,认识
            一,,,不认识
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertEquals(setOf("一"), r.chineseUnknown)
        assertFalse(r.chineseKnown.contains("一"))
    }

    @Test
    fun `含逗号的例句不会让列错位`() {
        val text = """
            # English words
            Word,Phonetic,ChineseMeaning,ExampleSentence,Status
            hello,/həˈləʊ/,你好,"Hello, world.",known
        """.trimIndent()
        val r = ProgressCsvImporter.parse(text)
        assertEquals(setOf("W:hello"), r.englishKnown)
        assertEquals(0, r.skippedRows)
    }

    @Test
    fun `空的或无关文本返回空结果`() {
        assertTrue(ProgressCsvImporter.parse("").isEmpty)
        assertTrue(ProgressCsvImporter.parse("随便一段话，没有任何表头").isEmpty)
    }

    @Test
    fun `total 统计各类目数量`() {
        val r = ProgressCsvImporter.parse(exported)
        assertEquals(2, r.chineseKnown.size + r.chineseUnknown.size)
        assertEquals(4, r.englishKnown.size + r.englishUnknown.size)
        assertEquals(6, r.total)
    }
}

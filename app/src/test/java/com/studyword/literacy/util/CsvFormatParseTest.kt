package com.studyword.literacy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CsvFormat] 解析单元测试(v1.6.0 / P2-3 CSV 导入)。
 *
 * 导出侧([CsvFormat.cell])早就有测试;这里补上**解析侧** ——
 * 导入的 correctness 完全依赖它,而 CSV 的坑(引号内逗号/换行/转义引号)
 * 肉眼极难验证,必须钉死。
 */
class CsvFormatParseTest {

    @Test
    fun `简单行按逗号拆分`() {
        assertEquals(listOf("a", "b", "c"), CsvFormat.parseAll("a,b,c").single())
    }

    @Test
    fun `引号内的逗号属于字段内容`() {
        val rows = CsvFormat.parseAll("\"Hello, world.\",2,3")
        assertEquals(listOf("Hello, world.", "2", "3"), rows.single())
    }

    @Test
    fun `双引号转义为字面双引号`() {
        val rows = CsvFormat.parseAll("\"say \"\"hi\"\"\",x")
        assertEquals(listOf("say \"hi\"", "x"), rows.single())
    }

    @Test
    fun `引号内的换行不断行`() {
        val rows = CsvFormat.parseAll("\"line1\nline2\",b")
        assertEquals(1, rows.size)
        assertEquals(listOf("line1\nline2", "b"), rows[0])
    }

    @Test
    fun `CRLF 与 LF 都能断行`() {
        assertEquals(2, CsvFormat.parseAll("a,b\r\nc,d").size)
        assertEquals(2, CsvFormat.parseAll("a,b\nc,d").size)
        assertEquals(2, CsvFormat.parseAll("a,b\rc,d").size)
    }

    @Test
    fun `空白行被跳过`() {
        val rows = CsvFormat.parseAll("a,b\n\n   \nc,d")
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("c", "d"), rows[1])
    }

    @Test
    fun `空字段被保留为空格字符串`() {
        assertEquals(listOf("a", "", "c"), CsvFormat.parseAll("a,,c").single())
    }

    @Test
    fun `末行没有换行也能解析`() {
        val rows = CsvFormat.parseAll("a,b\nc,d")
        assertEquals(listOf("c", "d"), rows.last())
    }

    @Test
    fun `与 cell 是互逆的`() {
        val values = listOf("普通", "含,逗号", "含\"引号", "含\n换行", "")
        val line = CsvFormat.row(values)
        assertEquals(values, CsvFormat.parseAll(line).single())
    }

    @Test
    fun `空文本返回空列表`() {
        assertTrue(CsvFormat.parseAll("").isEmpty())
        assertTrue(CsvFormat.parseAll("\n\n").isEmpty())
    }
}

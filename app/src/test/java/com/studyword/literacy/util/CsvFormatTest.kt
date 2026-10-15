package com.studyword.literacy.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CsvFormat] 单元测试(v1.5.0)。
 *
 * v1.4.4 的导出直接 `joinToString(",")`,英文例句 "Hello, world." 之类
 * 含半角逗号的值会让整列错位,这里锁住修复行为。
 */
class CsvFormatTest {

    @Test
    fun `普通值不加引号`() {
        assertEquals("天", CsvFormat.cell("天"))
        assertEquals("apple", CsvFormat.cell("apple"))
    }

    @Test
    fun `含逗号的值加引号`() {
        assertEquals("\"Hello, world.\"", CsvFormat.cell("Hello, world."))
    }

    @Test
    fun `含中文顿号的值不需要引号`() {
        // 中文标点不是 CSV 分隔符
        assertEquals("天空、今天", CsvFormat.cell("天空、今天"))
    }

    @Test
    fun `含双引号的值引号翻倍并被包裹`() {
        assertEquals("\"say \"\"hi\"\"\"", CsvFormat.cell("say \"hi\""))
    }

    @Test
    fun `含换行的值被包裹`() {
        assertEquals("\"a\nb\"", CsvFormat.cell("a\nb"))
    }

    @Test
    fun `空串原样返回`() {
        assertEquals("", CsvFormat.cell(""))
    }

    @Test
    fun `row 逐格转义后按逗号连接`() {
        val line = CsvFormat.row(listOf("apple", "Hello, world.", "known"))
        assertEquals("apple,\"Hello, world.\",known", line)
    }

    @Test
    fun `row 保持列数不变`() {
        // 关键回归:转义后列数必须仍然是 3,否则读表程序会串列
        val line = CsvFormat.row(listOf("a,b", "c\"d", "e"))
        assertEquals(3, splitCsvColumns(line).size)
    }

    /** 极简 CSV 解析,仅用于验证列数(P0 测试不引入额外依赖) */
    private fun splitCsvColumns(line: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    columns += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        columns += current.toString()
        return columns
    }
}

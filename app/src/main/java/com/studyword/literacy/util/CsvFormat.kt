package com.studyword.literacy.util

/**
 * CSV 单元格转义(v1.5.0)。
 *
 * 抽成纯函数便于单测。v1.4.4 的导出直接用 `joinToString(",")`,
 * 一旦词组或英文例句里出现半角逗号(例如 "Hello, world."),整行列数就会错位。
 */
object CsvFormat {

    /**
     * 按 RFC 4180 转义:含逗号 / 双引号 / 换行的值用双引号包裹,内部双引号翻倍。
     * 不含特殊字符时原样返回,避免给整表加无用引号。
     */
    fun cell(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }

    /** 把一行字段拼成 CSV 行(逐格转义后以逗号连接) */
    fun row(values: List<String>): String = values.joinToString(",") { cell(it) }

    /**
     * RFC 4180 解析(v1.6.0 / P2-3 CSV 导入)。
     *
     * 为什么需要它:导出用的是 [cell] 转义 —— 含逗号/引号/换行的值会被双引号包裹。
     * 导入如果只按 `split(",")` 拆,遇到 `"Hello, world."` 这种就会错位。
     * 这里实现完整规则:
     * - 引号内的逗号 / 换行属于字段内容
     * - 连续两个双引号 `""` 表示一个字面双引号
     * - 兼容 CRLF 与 LF
     * - 跳过完全空白的行(导出时用它做段落分隔)
     *
     * @return 每行一个字段列表
     */
    fun parseAll(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var fieldStarted = false      // 用于区分"空字段"与"未开始的字段"
        var i = 0

        fun endField() {
            fields.add(current.toString())
            current.clear()
            fieldStarted = false
        }

        fun endRow() {
            endField()
            // 整行只有空白 → 视为分隔行,丢弃
            if (fields.any { it.isNotBlank() }) rows.add(fields)
            fields = mutableListOf()
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        current.append('"'); i++      // 转义的双引号
                    }
                    ch == '"' -> inQuotes = false
                    else -> current.append(ch)
                }
                ch == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true }
                ch == ',' -> endField()
                ch == '\r' -> {
                    // CRLF 或单独的 CR 都当换行
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                ch == '\n' -> endRow()
                else -> { current.append(ch); fieldStarted = true }
            }
            i++
        }
        // 收尾(文件末尾没有换行时)
        if (current.isNotEmpty() || fields.isNotEmpty() || fieldStarted) endRow()
        return rows
    }
}

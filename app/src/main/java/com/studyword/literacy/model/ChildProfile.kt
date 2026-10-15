package com.studyword.literacy.model

/**
 * 孩子档案(v1.6.0 / P2-2 多用户档案)。
 *
 * [id] 同时也是 [com.studyword.literacy.data.ProgressStore] 的 SharedPreferences 文件名后缀,
 * 所以一旦创建就**不应该再改**(改名只改 [name])。
 *
 * 默认档案的 id 固定为 `"default"`,沿用历史文件名 `literacy_progress` ——
 * 这样老用户在升级后不会丢进度。
 */
data class ChildProfile(
    val id: String,
    val name: String,
    val createdAt: Long
)

/**
 * 档案名的规则(纯函数,便于单测)。
 */
object ProfileRules {

    const val MAX_NAME_LENGTH = 12

    /** 默认档案的显示名 */
    const val DEFAULT_NAME = "宝贝"

    /**
     * 生成下一个默认档案名:宝贝 / 宝贝2 / 宝贝3…
     * 已存在同名时继续往后找,避免出现两个"宝贝2"。
     */
    fun nextDefaultName(existing: List<ChildProfile>): String {
        val taken = existing.map { it.name }.toSet()
        if (DEFAULT_NAME !in taken) return DEFAULT_NAME
        var n = 2
        while ("$DEFAULT_NAME$n" in taken) n++
        return "$DEFAULT_NAME$n"
    }

    /**
     * 规范化家长输入的名字:把**任意连续空白**(空格/换行/制表符)压成单个空格、
     * 去掉首尾空白、截断到上限。
     *
     * 用 `\s+` 而不是只列 `[\r\n\t]`:后者遇到 "小 \n\t 明" 会留下 3 个连续空格,
     * 档案 chip 上就会出现很怪的间距。
     *
     * 返回空串表示这个名字不可用(调用方应回退到默认名)。
     */
    fun normalizeName(raw: String?): String =
        (raw ?: "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME_LENGTH)

    /** 只剩一个档案时不允许删除(否则数据会无处可放) */
    fun canDelete(profiles: List<ChildProfile>): Boolean = profiles.size > 1
}

/**
 * [ChildProfile] 列表的持久化编解码。
 *
 * 为什么需要转义:档案名是**家长手输的**,完全可能出现 `|`、`;` 或反斜杠
 * (例如「小明;3岁」)。ReviewCodec 那边可以直接用 `|`/`;` 拼字符串,
 * 是因为进度键只可能是汉字或 `L:A`/`W:apple`;这里输入不可控,必须转义。
 *
 * 格式:`id|name|createdAt` 为一条记录,记录之间用 `;`;
 * 字段内的 `\` `|` `;` 均以 `\` 转义。
 */
object ProfileCodec {

    private const val RECORD = ';'
    private const val FIELD = '|'
    private const val ESCAPE = '\\'

    fun encode(profiles: List<ChildProfile>): String =
        profiles.joinToString(RECORD.toString()) { p ->
            listOf(escape(p.id), escape(p.name), p.createdAt.toString())
                .joinToString(FIELD.toString())
        }

    fun decode(raw: String?): List<ChildProfile> {
        if (raw.isNullOrBlank()) return emptyList()

        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false

        for (ch in raw) {
            when {
                escaped -> { current.append(ch); escaped = false }
                ch == ESCAPE -> escaped = true
                ch == FIELD -> { fields.add(current.toString()); current.clear() }
                ch == RECORD -> {
                    fields.add(current.toString()); current.clear()
                    records.add(fields); fields = mutableListOf()
                }
                else -> current.append(ch)
            }
        }
        fields.add(current.toString())
        records.add(fields)

        // 损坏的记录直接跳过,不让一条坏数据毁掉整份档案列表
        return records.mapNotNull { f ->
            if (f.size != 3 || f[0].isBlank()) return@mapNotNull null
            val createdAt = f[2].toLongOrNull() ?: return@mapNotNull null
            ChildProfile(id = f[0], name = f[1], createdAt = createdAt)
        }
    }

    private fun escape(s: String): String = buildString {
        for (ch in s) {
            if (ch == ESCAPE || ch == FIELD || ch == RECORD) append(ESCAPE)
            append(ch)
        }
    }
}

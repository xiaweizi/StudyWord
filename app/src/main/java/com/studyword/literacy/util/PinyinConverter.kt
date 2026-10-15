package com.studyword.literacy.util

/**
 * 汉字 → 拼音(v1.5.0 重构)。
 *
 * ## 为什么改掉 ICU
 *
 * v1.4.4 用 `android.icu.text.Transliterator("Han-Latin/Names")` 在运行时推导拼音,
 * Android Lint 报 `[NewApi]`:`Transliterator#getInstance` / `#transliterate`
 * **需要 API 29**,而本应用 minSdk 是 26。
 * 由于首屏就要为 3000 个字生成拼音,这在 Android 8/9(API 26~28)上等于**启动即崩**。
 * 另外 `Han-Latin/Names` 是人名转写规则集,对多音字(长/行/重/教)经常取错音。
 *
 * ## 现在怎么做
 *
 * 拼音改为**构建期生成、运行期查表**:
 * - 数据源:`scripts/generate_pinyin.py` 用 pypinyin 生成 `assets/pinyin_table.json`(3000 字全覆盖)
 * - 运行期:[com.studyword.literacy.data.PinyinTable] 一次性读表,查表是 O(1)
 *
 * 取值优先级:
 * 1. [overrides] —— 人工校正表(对儿童材料的最常见读音做的刻意选择)
 * 2. `table` —— 离线拼音表
 * 3. `""` —— 都没有时返回空串(卡片显示 "--"),**绝不崩溃**
 *
 * 本文件因此不再依赖任何 Android API,可被纯 JVM 单元测试完整覆盖。
 */
object PinyinConverter {

    /**
     * 人工校正表:优先级高于离线拼音表。
     *
     * 选用读音规则:**该字在现代汉语单字/常用词中最常见的读音**,与卡片展示一致(幼儿识字场景)。
     *
     * 说明:`pinyin_table.json` 由 pypinyin 默认读音生成,绝大多数情况下与人工判断一致;
     * 此表只保留需要**刻意覆盖**的字(例如「长」表里是 zhǎng,卡片按既有行为显示 cháng)。
     */
    private val overrides: Map<String, String> = mapOf(
        // ——— v1.4.0 既有条目(保持不变,避免影响已发布行为) ———
        "了" to "le",
        "地" to "dì",
        "么" to "me",
        "还" to "hái",
        "便" to "pián",
        "长" to "cháng",
        "行" to "xíng",
        "得" to "dé",
        "种" to "zhǒng",
        "只" to "zhǐ",
        "觉" to "jué",
        "空" to "kōng",
        // ——— v1.5.0 新增:常见多音字 ———
        "着" to "zhe",
        "重" to "zhòng",
        "数" to "shù",
        "发" to "fā",
        "好" to "hǎo",
        "中" to "zhōng",
        "会" to "huì",
        "分" to "fēn",
        "相" to "xiāng",
        "少" to "shǎo",
        "角" to "jiǎo",
        "背" to "bèi",
        "血" to "xuè",
        "藏" to "cáng",
        "系" to "xì",
        "当" to "dāng",
        "干" to "gān",
        "应" to "yīng",
        "曾" to "céng",
        "场" to "chǎng",
        "称" to "chēng",
        "冲" to "chōng",
        "传" to "chuán",
        "创" to "chuàng",
        "担" to "dān",
        "都" to "dōu",
        "更" to "gèng",
        "观" to "guān",
        "号" to "hào",
        "和" to "hé",
        "华" to "huá",
        "划" to "huá",
        "几" to "jǐ",
        "间" to "jiān",
        "将" to "jiāng",
        "降" to "jiàng",
        "结" to "jié",
        "解" to "jiě",
        "看" to "kàn",
        "累" to "lèi",
        "量" to "liàng",
        "落" to "luò",
        "难" to "nán",
        "弄" to "nòng",
        "扫" to "sǎo",
        "盛" to "shèng",
        "似" to "sì",
        "宿" to "sù",
        "提" to "tí",
        "吐" to "tǔ",
        "鲜" to "xiān",
        "与" to "yǔ",
        "占" to "zhàn",
        "折" to "zhé",
        "教" to "jiào",
    )

    /** 该字是否在人工校正表中(便于数据侧核对 pypinyin 与人工判断的差异) */
    fun hasOverride(hanzi: String): Boolean =
        hanzi.isNotEmpty() && overrides.containsKey(hanzi.first().toString())

    /**
     * 输入一个汉字,返回其拼音字符串。
     *
     * @param table 离线拼音表,通常传 [com.studyword.literacy.data.PinyinTable.load] 的结果
     */
    fun toPinyin(hanzi: String, table: Map<String, String>? = null): String {
        if (hanzi.isEmpty()) return ""
        val first = hanzi.first().toString()
        overrides[first]?.let { return it }
        table?.get(first)?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    /**
     * 输入一段汉字,返回逐字拼音数组(查不到读音的字会被跳过)。
     *
     * 用法:"日子" → ["rì", "zi"]
     */
    fun toPinyinSequence(hanzi: String, table: Map<String, String>? = null): List<String> {
        if (hanzi.isEmpty()) return emptyList()
        return hanzi.map { c -> toPinyin(c.toString(), table) }
            .filter { it.isNotBlank() }
    }
}

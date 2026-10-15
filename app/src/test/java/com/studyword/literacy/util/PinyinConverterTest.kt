package com.studyword.literacy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PinyinConverter] 单元测试(v1.5.0)。
 *
 * 注意:本测试**只覆盖 overrides 表中的字**。
 * 非覆盖字会走到 android.icu.text.Transliterator,在 JVM 单元测试里是 stub
 * (抛 "Stub!"),属于 Robolectric/仪器化测试的范畴,这里刻意不碰。
 *
 * 价值在于:把"多音字读音"这份人工决策锁住,防止后续误改。
 */
class PinyinConverterTest {

    @Test
    fun `空输入返回空串`() {
        assertEquals("", PinyinConverter.toPinyin(""))
    }

    // ===================== v1.4.0 既有 12 条 =====================

    @Test
    fun `v1_4_0 既有覆盖条目读音不变`() {
        val expected = mapOf(
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
            "空" to "kōng"
        )
        expected.forEach { (hanzi, pinyin) ->
            assertEquals("「$hanzi」应读 $pinyin", pinyin, PinyinConverter.toPinyin(hanzi))
        }
    }

    // ===================== v1.5.0 新增条目 =====================

    @Test
    fun `v1_5_0 新增常见多音字读音正确`() {
        val expected = mapOf(
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
            "教" to "jiào"
        )
        expected.forEach { (hanzi, pinyin) ->
            assertEquals("「$hanzi」应读 $pinyin", pinyin, PinyinConverter.toPinyin(hanzi))
        }
    }

    @Test
    fun `覆盖表条目不会返回 ICU 的占位符`() {
        // ICU 的 Han-Latin/Names 对"了"会输出 "p" 这类占位,必须被覆盖掉
        val bad = PinyinConverter.toPinyin("了")
        assertNotEqualsSafe("p", bad)
        assertFalse(bad.isBlank())
    }

    private fun assertNotEqualsSafe(unexpected: String, actual: String) {
        if (unexpected == actual) {
            throw AssertionError("不应等于 ICU 的错误输出「$unexpected」")
        }
    }

    @Test
    fun `多字输入只取第一个字的读音`() {
        // 卡片只展示单字,多字输入应稳定取首字
        assertEquals("hǎo", PinyinConverter.toPinyin("好天"))
    }

    // ===================== toPinyinSequence =====================

    @Test
    fun `toPinyinSequence 空输入返回空列表`() {
        assertTrue(PinyinConverter.toPinyinSequence("").isEmpty())
    }

    @Test
    fun `toPinyinSequence 逐字查覆盖表并保持顺序`() {
        assertEquals(listOf("le", "dì"), PinyinConverter.toPinyinSequence("了地"))
    }

    @Test
    fun `toPinyinSequence 遇到覆盖字不依赖 ICU`() {
        val result = PinyinConverter.toPinyinSequence("好了")
        assertEquals(listOf("hǎo", "le"), result)
    }

    // ===================== v1.5.0:离线拼音表 + 低版本安全 =====================

    @Test
    fun `提供了拼音表时非覆盖字用表里的读音`() {
        // 单元测试里 Build.VERSION.SDK_INT = 0,ICU 路径不可用,
        // 正好用来验证"表能兜住非覆盖字"。
        val table = mapOf("天" to "tiān", "地" to "dì")

        assertEquals("tiān", PinyinConverter.toPinyin("天", table))
    }

    @Test
    fun `覆盖表优先于拼音表`() {
        // 人工决策优先:长在 overrides 里是 cháng,即使表里给了别的读音也应沿用 overrides
        val table = mapOf("长" to "zhǎng")

        assertEquals("cháng", PinyinConverter.toPinyin("长", table))
    }

    @Test
    fun `没有表时返回空串而不是崩溃`() {
        // 关键回归:v1.4.4 在这里会走到 android.icu.text.Transliterator,
        // 而该方法需要 API 29,minSdk 却是 26 —— 拼音在首屏为 3000 字生成,等于启动即崩。
        // 现在查不到就返回空串,卡片显示 "--",绝不崩溃。
        assertEquals("", PinyinConverter.toPinyin("天"))
    }

    @Test
    fun `toPinyinSequence 在库缺失且无表时安全返回空列表`() {
        // 用非覆盖字(天天 会命中 overrides,不能用来验证这条)
        assertTrue(PinyinConverter.toPinyinSequence("天人").isEmpty())
    }

    @Test
    fun `toPinyinSequence 支持拼音表`() {
        val table = mapOf("天" to "tiān", "地" to "dì")
        assertEquals(listOf("tiān", "dì"), PinyinConverter.toPinyinSequence("天地", table))
    }
}

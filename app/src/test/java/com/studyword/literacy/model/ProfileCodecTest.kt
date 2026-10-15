package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProfileCodec] 单元测试(v1.6.0 / P2-2 多用户档案)。
 *
 * 重点:档案名是**家长手输的**,完全可能包含 `|` `;` `\`
 * (例如「小明;3岁」)。这里把这些边界情况都钉死,
 * 因为一旦转义出错,表现是"档案列表错乱/名字被截断"这种很难排查的问题。
 */
class ProfileCodecTest {

    private val p1 = ChildProfile("default", "宝贝", 1_700_000_000_000L)
    private val p2 = ChildProfile("pabc", "小明", 1_700_000_000_001L)

    @Test
    fun `空输入解码为空列表`() {
        assertTrue(ProfileCodec.decode(null).isEmpty())
        assertTrue(ProfileCodec.decode("").isEmpty())
        assertTrue(ProfileCodec.decode("   ").isEmpty())
    }

    @Test
    fun `编码再解码可还原`() {
        val list = listOf(p1, p2)
        assertEquals(list, ProfileCodec.decode(ProfileCodec.encode(list)))
    }

    @Test
    fun `名字含分隔符也能还原_这是转义存在的理由`() {
        val tricky = listOf(
            p1.copy(name = "小明;3岁"),
            p2.copy(name = "张|三"),
            p1.copy(id = "p1", name = "反\\斜杠")
        )
        assertEquals(tricky, ProfileCodec.decode(ProfileCodec.encode(tricky)))
    }

    @Test
    fun `名字含全部特殊字符也不串档`() {
        val tricky = listOf(
            ChildProfile("a", ";;||\\\\;|", 1L),
            ChildProfile("b", "正常", 2L)
        )
        val decoded = ProfileCodec.decode(ProfileCodec.encode(tricky))
        assertEquals(2, decoded.size)
        assertEquals(";;||\\\\;|", decoded[0].name)
        assertEquals("b", decoded[1].id)
    }

    @Test
    fun `保持输入顺序`() {
        val list = listOf(p2, p1)
        assertEquals(listOf("pabc", "default"), ProfileCodec.decode(ProfileCodec.encode(list)).map { it.id })
    }

    @Test
    fun `单条记录编码不含多余分隔符`() {
        assertEquals("default|宝贝|1700000000000", ProfileCodec.encode(listOf(p1)))
    }

    @Test
    fun `损坏的记录被跳过而不是整份失效`() {
        val raw = "default|宝贝|1700000000000;坏记录;pabc|小明|1700000000001"
        val decoded = ProfileCodec.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals("default", decoded[0].id)
        assertEquals("pabc", decoded[1].id)
    }

    @Test
    fun `时间戳非法或字段数不对的记录被跳过`() {
        assertTrue(ProfileCodec.decode("id|name|notanumber").isEmpty())
        assertTrue(ProfileCodec.decode("id|name").isEmpty())
    }

    @Test
    fun `空 id 的记录被跳过`() {
        assertTrue(ProfileCodec.decode("|名字|1700000000000").isEmpty())
    }
}

/**
 * [ProfileRules] 单元测试。
 */
class ProfileRulesTest {

    private fun profile(name: String, id: String = name) =
        ChildProfile(id, name, 1L)

    // ===================== nextDefaultName =====================

    @Test
    fun `没有档案时默认名是宝贝`() {
        assertEquals("宝贝", ProfileRules.nextDefaultName(emptyList()))
    }

    @Test
    fun `宝贝已被占用时依次递增`() {
        assertEquals("宝贝2", ProfileRules.nextDefaultName(listOf(profile("宝贝"))))
        assertEquals("宝贝3", ProfileRules.nextDefaultName(listOf(profile("宝贝"), profile("宝贝2"))))
    }

    @Test
    fun `中间有空洞时补空位而不是跳到最大`() {
        // 已有 宝贝 和 宝贝3 → 应补 宝贝2 而不是 宝贝4
        assertEquals("宝贝2", ProfileRules.nextDefaultName(listOf(profile("宝贝"), profile("宝贝3"))))
    }

    @Test
    fun `其它名字不影响默认名`() {
        assertEquals("宝贝", ProfileRules.nextDefaultName(listOf(profile("小明"))))
    }

    // ===================== normalizeName =====================

    @Test
    fun `去掉首尾空白`() {
        assertEquals("小明", ProfileRules.normalizeName("  小明  "))
    }

    @Test
    fun `换行制表符确实被压成单个空格`() {
        assertEquals("小 明", ProfileRules.normalizeName("小\n明"))
        assertEquals("小 明", ProfileRules.normalizeName("小\t明"))
        assertEquals("小 明", ProfileRules.normalizeName("小 \n\t 明"))
    }

    @Test
    fun `超长名字被截断`() {
        val long = "一".repeat(50)
        assertEquals(ProfileRules.MAX_NAME_LENGTH, ProfileRules.normalizeName(long).length)
    }

    @Test
    fun `空白名字返回空串`() {
        assertEquals("", ProfileRules.normalizeName(null))
        assertEquals("", ProfileRules.normalizeName("   "))
        assertEquals("", ProfileRules.normalizeName("\n\t"))
    }

    // ===================== canDelete =====================

    @Test
    fun `只剩一个档案时不能删除`() {
        assertFalse(ProfileRules.canDelete(listOf(profile("宝贝"))))
        assertFalse(ProfileRules.canDelete(emptyList()))
    }

    @Test
    fun `两个及以上可删除`() {
        assertTrue(ProfileRules.canDelete(listOf(profile("宝贝"), profile("小明"))))
    }
}

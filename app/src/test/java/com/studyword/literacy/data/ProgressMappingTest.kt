package com.studyword.literacy.data

import com.studyword.literacy.model.ProgressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProgressMapping] 单元测试(v1.5.0)。
 *
 * 这是整个"进度不再错位"改造里风险最高的一段:老用户的 int id 只在这个时候
 * 被翻译一次,翻错了就永久丢失(且不会崩溃,只会静默错位)。
 */
class ProgressMappingTest {

    private val hanziById = mapOf(
        0 to "一",
        1 to "二",
        2 to "三",
        3 to "四"
    )

    @Test
    fun `按 id 映射为汉字`() {
        val result = ProgressMapping.chineseKeys(setOf(0, 2), hanziById)
        assertEquals(setOf("一", "三"), result)
    }

    @Test
    fun `越界 id 被丢弃而不是崩溃`() {
        // 9999 是历史脏数据(字库曾更大,或旧版本 id 越界):
        // v1.4.4 会把它们算进"认识：N"导致计数虚高,这里必须丢掉。
        val result = ProgressMapping.chineseKeys(setOf(0, 9999), hanziById)
        assertEquals(setOf("一"), result)
        assertEquals(1, ProgressMapping.droppedCount(setOf(0, 9999), result))
    }

    @Test
    fun `空集合映射为空集合`() {
        assertTrue(ProgressMapping.chineseKeys(emptySet(), hanziById).isEmpty())
    }

    @Test
    fun `英文键映射同时覆盖字母与单词`() {
        val keyById = mapOf(
            0 to ProgressKey.letter("A"),
            1 to ProgressKey.letter("B"),
            1000 to ProgressKey.word("Apple"),
            1001 to ProgressKey.word("Ball")
        )

        val result = ProgressMapping.englishKeys(setOf(1, 1000), keyById)

        assertEquals(setOf("L:B", "W:apple"), result)
    }

    @Test
    fun `英文越界 id 被丢弃`() {
        val keyById = mapOf(0 to ProgressKey.letter("A"))
        val result = ProgressMapping.englishKeys(setOf(0, 5000), keyById)
        assertEquals(setOf("L:A"), result)
    }

    @Test
    fun `内容键与字库顺序无关_重排后同一批字仍是同一批键`() {
        // 旧实现的问题根源:进度存的是位置。这里证明换成内容键后,
        // 即使字库整体重排(只要汉字还在),映射结果完全相同。
        val original = mapOf(0 to "一", 1 to "二", 2 to "三")
        val reordered = mapOf(0 to "三", 1 to "一", 2 to "二") // 顺序被打乱

        val before = ProgressMapping.chineseKeys(setOf(0, 1, 2), original)
        val after = ProgressMapping.chineseKeys(setOf(0, 1, 2), reordered)

        // 位置映射当然不同(这正是旧实现的病),但"同一批字仍在集合里"这一性质成立:
        assertEquals(setOf("一", "二", "三"), before)
        assertEquals(setOf("一", "二", "三"), after)
    }

    @Test
    fun `迁移后再次换算不会重复计入`() {
        // 迁移只跑一次(由 ProgressStore.isMigratedToKeys 守卫),
        // 这里验证映射本身是纯函数、不会累积。
        val first = ProgressMapping.chineseKeys(setOf(0), hanziById)
        val second = ProgressMapping.chineseKeys(setOf(0), hanziById)
        assertEquals(first, second)
    }
}

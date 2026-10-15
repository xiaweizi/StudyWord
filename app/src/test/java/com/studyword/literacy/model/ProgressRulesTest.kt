package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProgressRules] 单元测试(v1.5.0)。
 *
 * 覆盖两块此前散落在 Activity 里、最容易改漏的纯逻辑:
 * 1. known / unknown 的互斥改写
 * 2. 题池"待巩固 → 未测 → 已掌握"的排序优先级
 */
class ProgressRulesTest {

    private fun sets(vararg known: String): Pair<MutableSet<String>, MutableSet<String>> =
        known.toMutableSet<String>() to mutableSetOf()

    // ===================== apply =====================

    @Test
    fun `标记认识会加入 known 并移出 unknown`() {
        val (known, unknown) = sets()
        unknown.add("天")

        ProgressRules.apply(known, unknown, "天", isKnown = true)

        assertTrue(known.contains("天"))
        assertFalse(unknown.contains("天"))
    }

    @Test
    fun `标记不认识会加入 unknown 并移出 known`() {
        val (known, unknown) = sets("天")

        ProgressRules.apply(known, unknown, "天", isKnown = false)

        assertTrue(unknown.contains("天"))
        assertFalse(known.contains("天"))
    }

    @Test
    fun `重复标记同一结果保持幂等`() {
        val (known, unknown) = sets()

        ProgressRules.apply(known, unknown, "天", isKnown = true)
        ProgressRules.apply(known, unknown, "天", isKnown = true)

        assertEquals(1, known.size)
        assertTrue(unknown.isEmpty())
    }

    @Test
    fun `改判会覆盖上一次结果且两个集合始终互斥`() {
        val (known, unknown) = sets()

        ProgressRules.apply(known, unknown, "天", isKnown = true)
        ProgressRules.apply(known, unknown, "天", isKnown = false)
        assertFalse(known.contains("天"))
        assertTrue(unknown.contains("天"))

        ProgressRules.apply(known, unknown, "天", isKnown = true)
        assertTrue(known.contains("天"))
        assertFalse(unknown.contains("天"))
    }

    @Test
    fun `英文键与中文键互不影响`() {
        val (known, unknown) = sets()

        ProgressRules.apply(known, unknown, ProgressKey.letter("A"), isKnown = true)
        ProgressRules.apply(known, unknown, "天", isKnown = false)

        assertTrue(known.contains("L:A"))
        assertTrue(unknown.contains("天"))
        assertEquals(1, known.size)
        assertEquals(1, unknown.size)
    }

    // ===================== setStatus =====================

    @Test
    fun `setStatus true 等价于认识`() {
        val (known, unknown) = sets()
        ProgressRules.setStatus(known, unknown, "天", true)
        assertTrue(known.contains("天"))
    }

    @Test
    fun `setStatus false 等价于不认识`() {
        val (known, unknown) = sets()
        ProgressRules.setStatus(known, unknown, "天", false)
        assertTrue(unknown.contains("天"))
    }

    @Test
    fun `setStatus null 表示回到未学习_两个集合都要清掉`() {
        val (known, unknown) = sets()

        ProgressRules.setStatus(known, unknown, "天", true)
        ProgressRules.setStatus(known, unknown, "天", null)
        assertFalse(known.contains("天"))
        assertFalse(unknown.contains("天"))

        ProgressRules.setStatus(known, unknown, "天", false)
        ProgressRules.setStatus(known, unknown, "天", null)
        assertFalse(known.contains("天"))
        assertFalse(unknown.contains("天"))
    }

    // ===================== statusOf =====================

    @Test
    fun `statusOf 三态判定`() {
        val known = setOf("天")
        val unknown = setOf("地")

        assertEquals(LearnStatus.KNOWN, ProgressRules.statusOf(known, unknown, "天"))
        assertEquals(LearnStatus.UNKNOWN, ProgressRules.statusOf(known, unknown, "地"))
        assertEquals(LearnStatus.UNSEEN, ProgressRules.statusOf(known, unknown, "人"))
    }

    // ===================== partition =====================

    @Test
    fun `题池排序为 待巩固 然后 未测 然后 已掌握`() {
        val pool = listOf("甲", "乙", "丙", "丁")
        val known = setOf("丁")
        val unknown = setOf("乙")

        val ordered = ProgressRules.partition(pool, { it }, known, unknown).ordered()

        assertEquals(listOf("乙", "甲", "丙", "丁"), ordered)
    }

    @Test
    fun `bucket 内容与顺序保持一致`() {
        val pool = listOf("甲", "乙", "丙", "丁")
        val partition = ProgressRules.partition(pool, { it }, setOf("丁"), setOf("乙"))

        assertEquals(listOf("乙"), partition.review)
        assertEquals(listOf("甲", "丙"), partition.untested)
        assertEquals(listOf("丁"), partition.mastered)
    }

    @Test
    fun `同时出现在 known 与 unknown 时按待巩固优先_与旧实现一致`() {
        // v1.4.4 的顺序是 review 先收集,因此出现脏数据时应优先复习
        val partition = ProgressRules.partition(listOf("天"), { it }, setOf("天"), setOf("天"))

        assertEquals(listOf("天"), partition.review)
        assertTrue(partition.mastered.isEmpty())
        assertTrue(partition.untested.isEmpty())
    }

    @Test
    fun `空题池返回空分区`() {
        val partition = ProgressRules.partition(emptyList<String>(), { it }, emptySet(), emptySet())

        assertTrue(partition.ordered().isEmpty())
    }

    // ===================== v1.6.0:到期错题桶 =====================

    @Test
    fun `不传 dueKeys 时行为与旧版一致_没有 due 桶内容`() {
        val pool = listOf("甲", "乙", "丙", "丁")
        val partition = ProgressRules.partition(pool, { it }, setOf("丁"), setOf("乙"))

        assertTrue("默认不应产生到期错题", partition.due.isEmpty())
        assertEquals(listOf("乙", "甲", "丙", "丁"), partition.ordered())
    }

    @Test
    fun `到期的错题排在最前_优先于待巩固`() {
        val pool = listOf("甲", "乙", "丙", "丁")
        val partition = ProgressRules.partition(
            pool, { it }, setOf("丁"), setOf("乙"), dueKeys = setOf("甲")
        )

        assertEquals(listOf("甲"), partition.due)
        assertEquals(listOf("乙"), partition.review)
        assertEquals("到期错题必须最先出现", listOf("甲", "乙", "丙", "丁"), partition.ordered())
    }

    @Test
    fun `同时命中 due 与 unknown 时归入 due 桶_不会重复出现`() {
        val partition = ProgressRules.partition(
            listOf("天"), { it }, emptySet(), setOf("天"), dueKeys = setOf("天")
        )

        assertEquals(listOf("天"), partition.due)
        assertTrue("不能同时进两个桶", partition.review.isEmpty())
        assertEquals(1, partition.ordered().size)
    }

    @Test
    fun `partition 保留原始条目类型与顺序`() {
        data class Row(val key: String, val label: String)
        val rows = listOf(Row("天", "天空"), Row("地", "土地"))

        val ordered = ProgressRules.partition(rows, { it.key }, emptySet(), setOf("地")).ordered()

        assertEquals("土地", ordered.first().label)
        assertEquals("天空", ordered.last().label)
    }
}

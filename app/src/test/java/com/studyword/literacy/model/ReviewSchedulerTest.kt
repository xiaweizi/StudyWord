package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReviewScheduler] 单元测试(v1.6.0 / P2-1 错题本 + 间隔重复)。
 *
 * 简化版 Leitner 盒子:答错归零,答对升盒,间隔 1 → 3 → 7 → 16 天。
 */
class ReviewSchedulerTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L

    // ===================== intervalMs =====================

    @Test
    fun `各盒子对应 0_1_3_7_16 天`() {
        assertEquals("盒子0要立刻可复习,否则功能头一天看起来是坏的", 0L, ReviewScheduler.intervalMs(0))
        assertEquals(1 * day, ReviewScheduler.intervalMs(1))
        assertEquals(3 * day, ReviewScheduler.intervalMs(2))
        assertEquals(7 * day, ReviewScheduler.intervalMs(3))
        assertEquals(16 * day, ReviewScheduler.intervalMs(4))
    }

    @Test
    fun `盒子编号越界会被夹到合法范围`() {
        assertEquals(0L, ReviewScheduler.intervalMs(-5))
        assertEquals(16 * day, ReviewScheduler.intervalMs(99))
    }

    // ===================== onWrong / onKnown =====================

    @Test
    fun `首次答错进入错题本_盒子归零_并累计次数`() {
        val s = ReviewScheduler.onWrong(null, t0)
        assertEquals(1, s.wrongCount)
        assertEquals(0, s.box)
        assertEquals(t0, s.lastReviewedAt)
    }

    @Test
    fun `再次答错次数累加且盒子重新归零`() {
        val first = ReviewScheduler.onWrong(null, t0)
        val promoted = first.copy(box = 2)
        val second = ReviewScheduler.onWrong(promoted, t0 + day)

        assertEquals(2, second.wrongCount)
        assertEquals("答错后盒子必须归零,否则间隔会被错误拉长", 0, second.box)
    }

    @Test
    fun `从未进过错题本的字答对时不产生记录`() {
        assertNull(
            "避免为 3000 个字都写一条无意义记录",
            ReviewScheduler.onKnown(null, t0)
        )
    }

    @Test
    fun `进过错题本后答对会升盒并刷新时间`() {
        val wrong = ReviewScheduler.onWrong(null, t0)
        val known = ReviewScheduler.onKnown(wrong, t0 + day)!!

        assertEquals(1, known.box)
        assertEquals(1, known.wrongCount)
        assertEquals(t0 + day, known.lastReviewedAt)
    }

    @Test
    fun `盒子有上限_不会无限增长`() {
        var s: ReviewState? = ReviewScheduler.onWrong(null, t0)
        repeat(10) { s = ReviewScheduler.onKnown(s, t0 + day) }
        assertEquals(ReviewScheduler.MAX_BOX, s!!.box)
    }

    // ===================== isDue =====================

    @Test
    fun `从未复习过的错题立即到期`() {
        assertTrue(ReviewScheduler.isDue(ReviewState(1, 0, 0L), t0))
    }

    @Test
    fun `刚答错的字立刻可复习(盒子0)`() {
        val s = ReviewState(wrongCount = 1, box = 0, lastReviewedAt = t0)
        assertTrue(
            "孩子刚答错的字应当马上能进'复习错题'再练,而不是等一天",
            ReviewScheduler.isDue(s, t0 + 1000)
        )
    }

    @Test
    fun `盒子1需等1天才到期`() {
        val s = ReviewState(wrongCount = 1, box = 1, lastReviewedAt = t0)
        assertFalse(ReviewScheduler.isDue(s, t0 + day / 2))
        assertTrue(ReviewScheduler.isDue(s, t0 + day))
    }

    @Test
    fun `盒子越高间隔越长`() {
        val box4 = ReviewState(1, 4, t0)
        assertFalse(ReviewScheduler.isDue(box4, t0 + 8 * day))
        assertTrue(ReviewScheduler.isDue(box4, t0 + 16 * day))
    }

    @Test
    fun `nextDueAt 基于盒子计算`() {
        assertEquals(t0 + 3 * day, ReviewScheduler.nextDueAt(ReviewState(1, 2, t0)))
        assertEquals(0L, ReviewScheduler.nextDueAt(ReviewState(1, 0, 0L)))
    }

    // ===================== dueKeys =====================

    @Test
    fun `dueKeys 只返回到期的_并按错得最多排前`() {
        val states = mapOf(
            "天" to ReviewState(wrongCount = 1, box = 4, lastReviewedAt = t0),   // 16 天,未到期
            "地" to ReviewState(wrongCount = 3, box = 0, lastReviewedAt = t0),   // 立刻到期,错 3 次
            "人" to ReviewState(wrongCount = 2, box = 0, lastReviewedAt = t0)    // 立刻到期,错 2 次
        )
        val due = ReviewScheduler.dueKeys(states, t0 + day)

        assertEquals(listOf("地", "人"), due)
    }

    @Test
    fun `dueKeys 对空错题本返回空`() {
        assertTrue(ReviewScheduler.dueKeys(emptyMap(), t0).isEmpty())
    }
}

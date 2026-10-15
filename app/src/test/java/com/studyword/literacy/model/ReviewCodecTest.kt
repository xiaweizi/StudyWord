package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReviewCodec] 单元测试(v1.6.0)。
 *
 * 错题状态用 `;` 分隔记录、`|` 分隔字段。抽成纯函数正是为了避免
 * 历史曲线那边"用分隔符拼字符串却无法单测"的教训。
 */
class ReviewCodecTest {

    @Test
    fun `空输入解码为空表`() {
        assertTrue(ReviewCodec.decode(null).isEmpty())
        assertTrue(ReviewCodec.decode("").isEmpty())
        assertTrue(ReviewCodec.decode("   ").isEmpty())
    }

    @Test
    fun `编码再解码可还原`() {
        val states = mapOf(
            "天" to ReviewState(wrongCount = 2, box = 1, lastReviewedAt = 1_700_000_000_000L),
            "L:A" to ReviewState(wrongCount = 1, box = 0, lastReviewedAt = 1_700_000_000_001L),
            "W:apple" to ReviewState(wrongCount = 5, box = 3, lastReviewedAt = 1_700_000_000_002L)
        )
        assertEquals(states, ReviewCodec.decode(ReviewCodec.encode(states)))
    }

    @Test
    fun `编码按 key 排序_保证多次运行结果一致`() {
        val a = mapOf("天" to ReviewState(1, 0, 1L), "地" to ReviewState(2, 0, 2L))
        val b = mapOf("地" to ReviewState(2, 0, 2L), "天" to ReviewState(1, 0, 1L))
        assertEquals(ReviewCodec.encode(a), ReviewCodec.encode(b))
    }

    @Test
    fun `空表编码为空串`() {
        assertEquals("", ReviewCodec.encode(emptyMap()))
    }

    @Test
    fun `损坏的记录被跳过而不是抛异常`() {
        val raw = "天|2|1|1700000000000;坏记录;地|x|0|1;人|1|0|1700000000001"
        val decoded = ReviewCodec.decode(raw)

        assertEquals(2, decoded.size)
        assertEquals(ReviewState(2, 1, 1_700_000_000_000L), decoded["天"])
        assertEquals(ReviewState(1, 0, 1_700_000_000_001L), decoded["人"])
    }

    @Test
    fun `字段数不对的记录被跳过`() {
        assertTrue(ReviewCodec.decode("天|2|1").isEmpty())
    }

    @Test
    fun `空 key 的记录被跳过`() {
        assertTrue(ReviewCodec.decode("|2|1|1700000000000").isEmpty())
    }

    @Test
    fun `英文键能正常往返_因为键里不含分隔符`() {
        val states = mapOf("W:apple" to ReviewState(3, 2, 123L))
        assertEquals(states, ReviewCodec.decode(ReviewCodec.encode(states)))
    }
}

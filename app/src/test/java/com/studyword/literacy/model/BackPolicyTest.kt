package com.studyword.literacy.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BackPolicy] 单元测试(v1.5.0)。
 *
 * 这三种返回行为的优先级此前内联在 MainActivity 的 OnBackPressedCallback 里,
 * 是 v1.4.2~v1.4.4 反复出问题的地方(抽屉、跳转来源、退出确认互相打架)。
 * 现在用测试把优先级钉死。
 */
class BackPolicyTest {

    @Test
    fun `抽屉打开时优先关闭抽屉`() {
        assertEquals(
            BackAction.CLOSE_DRAWER,
            BackPolicy.decide(drawerOpen = true, hasJumpSource = false)
        )
    }

    @Test
    fun `抽屉打开时即使有跳转来源也先关抽屉`() {
        // 用户预期"退一层"是关掉眼前的抽屉,而不是跳走或退出
        assertEquals(
            BackAction.CLOSE_DRAWER,
            BackPolicy.decide(drawerOpen = true, hasJumpSource = true)
        )
    }

    @Test
    fun `有跳转来源时回到来源页`() {
        assertEquals(
            BackAction.GO_TO_SOURCE,
            BackPolicy.decide(drawerOpen = false, hasJumpSource = true)
        )
    }

    @Test
    fun `正常主页态弹退出确认`() {
        assertEquals(
            BackAction.CONFIRM_EXIT,
            BackPolicy.decide(drawerOpen = false, hasJumpSource = false)
        )
    }

    @Test
    fun `优先级顺序为 抽屉 高于 来源页 高于 退出`() {
        // 穷举全部输入组合,确认顺序不会被后续改动打乱
        val actual = listOf(
            BackPolicy.decide(true, true),
            BackPolicy.decide(true, false),
            BackPolicy.decide(false, true),
            BackPolicy.decide(false, false)
        )
        assertEquals(
            listOf(
                BackAction.CLOSE_DRAWER,
                BackAction.CLOSE_DRAWER,
                BackAction.GO_TO_SOURCE,
                BackAction.CONFIRM_EXIT
            ),
            actual
        )
    }
}

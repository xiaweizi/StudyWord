package com.studyword.literacy.model

/**
 * 主页系统返回键的决策(v1.5.0)。
 *
 * 此前这三种分支(`关闭抽屉` / `回到来源页` / `弹退出确认`)全部内联在
 * [com.studyword.literacy.ui.MainActivity] 的 `OnBackPressedCallback` 里,
 * 优先级规则埋在 Activity 内部、无法单测。抽成纯函数后:
 * - 优先级有测试锁定(抽屉 → 来源页 → 退出)
 * - Activity 只负责"执行决定"
 */
enum class BackAction {
    /** 抽屉开着:只关抽屉,不退出、不跳转 */
    CLOSE_DRAWER,

    /** 从进度页/字库页跳来的:回到来源页 */
    GO_TO_SOURCE,

    /** 正常主页态:弹退出确认对话框 */
    CONFIRM_EXIT
}

object BackPolicy {

    /**
     * 判定本次返回键应该做什么。
     *
     * 优先级(v1.4.4 行为,现由测试锁定):
     * 1. 抽屉打开 → 先关抽屉(用户预期"退一层",不是退出 App)
     * 2. 存在跳转来源 → 回到来源页(单步返回)
     * 3. 否则 → 弹退出确认
     */
    fun decide(drawerOpen: Boolean, hasJumpSource: Boolean): BackAction = when {
        drawerOpen -> BackAction.CLOSE_DRAWER
        hasJumpSource -> BackAction.GO_TO_SOURCE
        else -> BackAction.CONFIRM_EXIT
    }
}

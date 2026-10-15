package com.studyword.literacy.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * 系统栏避让工具。
 *
 * 为什么需要它
 * ------------
 * 应用主题里 `statusBarColor` 是 **透明的**,窗口内容是**全屏**绘制
 * (状态栏叠在内容之上)。如果根布局只是 `padding=20dp`,
 * 顶部的控件(返回箭头 / 标题)就正好落在状态栏区域里 ——
 *
 * **视觉上看得见,但点击会被状态栏窗口吃掉,按钮永远收不到事件。**
 *
 * 这个问题在游戏页、进度页、设置页、字库页都存在过(游戏页是用户反馈的:
 * "按左上角箭头没反应"),而主页面一开始就做了避让所以没暴露。
 * 抽成扩展函数,避免以后新增页面再漏。
 */
fun View.applyStatusBarTopPadding() {
    val base = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.updatePadding(top = base + top)
        insets
    }
    // 监听器注册后主动请求一次,保证首帧就带上正确内边距
    ViewCompat.requestApplyInsets(this)
}

/**
 * 上下都避让,并且分别叠加各自的基础内边距。
 * 适合"上下都有系统栏压着"的滚动页面。
 */
fun View.applySystemBarPadding(baseTop: Int = paddingTop, baseBottom: Int = paddingBottom) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

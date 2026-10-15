package com.studyword.literacy.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * 主页滚动区:内容不超出一屏时**完全不响应拖拽**,让卡片牢牢固定在最高位置。
 *
 * 为什么需要
 * ----------
 * 用户反馈"卡片仍然能够上下移动,没有完全固定在最高位置" ——
 * 只在切卡片时复位滚动位置是不够的,手动拖拽依然会滚动。
 *
 * 但也不能无条件禁用滚动:卡片内容(尤其例句)在内容超长时确实会超出一屏,
 * 那时若完全锁死,底部内容就再也够不到了(此前用户反馈过"例句显示不全")。
 *
 * 所以这里按需处理:
 * - **内容放得下** → 不拦截、不消费触摸,页面纹丝不动(用户要的效果)
 * - **内容放不下** → 保留正常滚动,保证内容都能看到
 */
class FixedHomeScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    /** 内容是否真的超出了一屏(需要滚动才能看全) */
    private fun contentOverflows(): Boolean {
        val child = getChildAt(0) ?: return false
        val viewport = height - paddingTop - paddingBottom
        if (viewport <= 0) return false
        return child.height > viewport
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        contentOverflows() && super.onInterceptTouchEvent(ev)

    // lint 的 ClickableViewAccessibility 是提醒"自定义 onTouchEvent 可能吞掉无障碍点击"。
    // 这里恰恰相反:内容放得下时**返回 false、不消费任何触摸**,点击照常交给子控件
    // (卡片、词组 chip、例句都用点击发音);只有内容溢出时才转发给父类滚动。
    // 因此不存在吞点击的问题,lint 的告警在本场景不适用。
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean =
        contentOverflows() && super.onTouchEvent(ev)
}

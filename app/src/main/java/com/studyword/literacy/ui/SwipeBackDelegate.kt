package com.studyword.literacy.ui

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

/**
 * 左边缘右滑返回(v1.5.0 抽取)。
 *
 * [ProgressActivity] 与 [CharacterLibraryActivity] 此前**各写了一份完全相同的实现**
 * (`GestureDetector` + 同样的三个阈值常量 + 同样的 `dispatchTouchEvent` 转发),
 * 任何阈值调整都要改两处。
 *
 * 用法:
 * ```
 * private val swipeBack by lazy { SwipeBackDelegate(this) }
 *
 * override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
 *     swipeBack.onTouchEvent(ev)
 *     return super.dispatchTouchEvent(ev)
 * }
 * ```
 *
 * 判定条件(与 v1.4.1 行为一致,避免影响 RecyclerView 滚动与 chip 点击):
 * 1. 起点落在屏幕左侧 [EDGE_THRESHOLD_DP] 内
 * 2. 水平位移 ≥ [SWIPE_DISTANCE_THRESHOLD_DP]
 * 3. 水平位移 > 垂直位移的 2 倍(确保不是上下滑动)
 * 4. 横向速度 > [SWIPE_VELOCITY_THRESHOLD]
 */
class SwipeBackDelegate(private val activity: Activity) {

    private val detector: GestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val density = activity.resources.displayMetrics.density
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val startsAtLeftEdge = e1.x <= EDGE_THRESHOLD_DP * density
                val longEnough = dx >= SWIPE_DISTANCE_THRESHOLD_DP * density
                val mostlyHorizontal = abs(dx) > abs(dy) * 2
                val fastEnough = velocityX > SWIPE_VELOCITY_THRESHOLD
                if (startsAtLeftEdge && longEnough && mostlyHorizontal && fastEnough) {
                    activity.finish()
                    return true
                }
                return false
            }
        }
    )

    /** 在 Activity.dispatchTouchEvent 里转发 */
    fun onTouchEvent(ev: MotionEvent) {
        detector.onTouchEvent(ev)
    }

    companion object {
        /** 边缘触发区宽度(dp) */
        private const val EDGE_THRESHOLD_DP = 24f

        /** 最小滑动距离(dp) */
        private const val SWIPE_DISTANCE_THRESHOLD_DP = 120f

        /** 最小横向速度(px/s) */
        private const val SWIPE_VELOCITY_THRESHOLD = 400f
    }
}

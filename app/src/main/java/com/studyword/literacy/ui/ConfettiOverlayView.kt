package com.studyword.literacy.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.studyword.literacy.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 撒花 / 鼓励气泡的覆盖层(v1.5.0)。
 *
 * 抽出来的原因:这套粒子系统此前在 [MainActivity] 与
 * [com.studyword.literacy.game.GameActivity] 里**各写了一份**(创建 TextView、
 * `ValueAnimator` + `cos/sin` 抛物线、随机角度/速度/时长、透明度包络、移除视图),
 * 两处几乎逐行相同,改一处必然漏另一处。
 *
 * 现在两边都通过布局里的 `@id/confettiOverlay` 调用本类:
 * - [burst]         从锚点(或容器某处)向外喷发 emoji 粒子
 * - [floatMessage]  一段文字向上飘起并淡出(用于"继续加油!"这类鼓励)
 * - [cancelAll]     取消所有挂起动画与回调(Activity onDestroy 调用,避免操作已销毁的界面)
 *
 * 所有本类的公开方法都必须在主线程调用。
 */
class ConfettiOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 一次喷发的参数。
     *
     * 默认值对应主页"认识啦"的那次撒花;游戏页的小/大连击通过覆盖字段实现。
     */
    data class BurstSpec(
        val count: Int = 18,
        val emojis: List<String> = DEFAULT_EMOJIS,
        /** 粒子字号范围(sp) */
        val textSize: IntRange = 18..34,
        /** 初速度 = 该系数 × 容器高度 */
        val velocityFactor: ClosedFloatingPointRange<Float> = 0.35f..0.75f,
        val durationMs: LongRange = 900L..1400L,
        /** 无锚点时,喷发起点的纵向位置(相对容器高度,0f=顶部 1f=底部) */
        val centerYFactor: Float = 0.5f,
        /** 粒子打散后,延迟多久回调 onEnd(用于尽快恢复按钮可点) */
        val holdBeforeEndMs: Long = 300L,
        /** 是否带自旋 */
        val rotate: Boolean = true
    )

    private val random = Random(System.currentTimeMillis())
    private val runningAnimators = mutableListOf<ValueAnimator>()
    private var pendingEnd: Runnable? = null

    /**
     * 向外喷发粒子。
     *
     * @param anchor 喷发中心;传 null 时用容器中心(纵向由 [BurstSpec.centerYFactor] 决定)
     * @param onEnd  粒子打散后回调(可为 null)
     */
    fun burst(spec: BurstSpec = BurstSpec(), anchor: View? = null, onEnd: (() -> Unit)? = null) {
        runWhenMeasured { doBurst(spec, anchor, onEnd) }
    }

    private fun doBurst(spec: BurstSpec, anchor: View?, onEnd: (() -> Unit)?) {
        removeAllViews()
        cancelAnimators()
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX: Float
        val centerY: Float
        if (anchor != null && anchor.width > 0 && anchor.height > 0) {
            centerX = anchor.x + anchor.width / 2f
            centerY = anchor.y + anchor.height / 2f
        } else {
            centerX = width / 2f
            centerY = height * spec.centerYFactor
        }

        repeat(spec.count) { index ->
            val emoji = spec.emojis[index % spec.emojis.size]
            val textView = TextView(context).apply {
                text = emoji
                textSize = random.nextInt(
                    spec.textSize.first,
                    spec.textSize.last + 1
                ).toFloat()
                alpha = 0f
            }
            addView(
                textView,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )
            textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            val halfWidth = textView.measuredWidth / 2f
            val halfHeight = textView.measuredHeight / 2f

            val angle = random.nextDouble(0.0, Math.PI * 2)
            val velocity = random.nextDouble(
                spec.velocityFactor.start.toDouble(),
                spec.velocityFactor.endInclusive.toDouble()
            ) * height
            val rotationDirection = if (random.nextBoolean()) 1 else -1
            val rotationRange = random.nextInt(120, 300)
            val duration = if (spec.durationMs.first == spec.durationMs.last) {
                spec.durationMs.first
            } else {
                random.nextLong(spec.durationMs.first, spec.durationMs.last)
            }

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    val distance = velocity * fraction
                    textView.translationX =
                        (centerX + (distance * cos(angle)).toFloat()) - halfWidth
                    textView.translationY =
                        (centerY + (distance * sin(angle)).toFloat()) - halfHeight
                    if (spec.rotate) {
                        textView.rotation = rotationDirection * rotationRange * fraction
                    }
                    textView.alpha = alphaAt(fraction)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (textView.parent as? ViewGroup)?.removeView(textView)
                    }
                })
                start()
            }
            runningAnimators += animator
        }

        scheduleEnd(spec.holdBeforeEndMs, onEnd)
    }

    /**
     * 让一段文字从 [anchor] 上方飘起并淡出(鼓励反馈)。
     */
    fun floatMessage(
        text: String,
        anchor: View? = null,
        @androidx.annotation.ColorRes textColorRes: Int = R.color.deep_blue,
        @androidx.annotation.DrawableRes backgroundRes: Int = R.drawable.bg_status_unseen,
        riseDistance: Float = 90f,
        durationMs: Long = 700L,
        onEnd: (() -> Unit)? = null
    ) {
        runWhenMeasured { doFloatMessage(text, anchor, textColorRes, backgroundRes, riseDistance, durationMs, onEnd) }
    }

    private fun doFloatMessage(
        text: String,
        anchor: View?,
        @androidx.annotation.ColorRes textColorRes: Int,
        @androidx.annotation.DrawableRes backgroundRes: Int,
        riseDistance: Float,
        durationMs: Long,
        onEnd: (() -> Unit)?
    ) {
        val label = TextView(context).apply {
            this.text = text
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, textColorRes))
            setBackgroundResource(backgroundRes)
            setPadding(28, 12, 28, 12)
            alpha = 0f
        }
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        val startX = width / 2f - label.paint.measureText(label.text.toString()) / 2
        val anchorY = anchor?.takeIf { it.y > 0f }?.y ?: (height / 2f)
        val startY = anchorY - 24f
        label.translationX = startX
        label.translationY = startY

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = durationMs
            addUpdateListener { va ->
                val fraction = va.animatedValue as Float
                label.translationY = startY - riseDistance * fraction
                label.alpha = alphaAt(fraction)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (label.parent as? ViewGroup)?.removeView(label)
                    onEnd?.invoke()
                }
            })
            start()
        }
        runningAnimators += animator
    }

    /** 取消所有粒子、动画与挂起回调。Activity onDestroy 时务必调用。 */
    fun cancelAll() {
        pendingEnd?.let { removeCallbacks(it) }
        pendingEnd = null
        cancelAnimators()
        removeAllViews()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    // ============================================================
    // 内部
    // ============================================================

    /**
     * 容器尚未测量出尺寸时(布局还没走完第一帧)延后执行,最多重试 [MAX_MEASURE_RETRY] 帧。
     * 原实现散在两个 Activity 里,用 `overlay.post { 递归调用自身 }` 实现同一件事。
     */
    private fun runWhenMeasured(attempt: Int = 0, action: () -> Unit) {
        if (width > 0 && height > 0) {
            action()
            return
        }
        if (attempt >= MAX_MEASURE_RETRY) return
        post { runWhenMeasured(attempt + 1, action) }
    }

    private fun cancelAnimators() {
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
    }

    private fun scheduleEnd(delayMs: Long, onEnd: (() -> Unit)?) {
        if (onEnd == null) return
        val runnable = Runnable {
            pendingEnd = null
            cancelAnimators()
            removeAllViews()
            onEnd()
        }
        pendingEnd = runnable
        postDelayed(runnable, delayMs)
    }

    /** 透明度包络:前 20% 淡入,后 20% 淡出 */
    private fun alphaAt(fraction: Float): Float = when {
        fraction < 0.2f -> fraction / 0.2f
        fraction > 0.8f -> (1f - fraction) / 0.2f
        else -> 1f
    }

    companion object {
        /** 容器未测量时的最大重试帧数(约 1 秒) */
        private const val MAX_MEASURE_RETRY = 60

        val DEFAULT_EMOJIS = listOf("🎉", "✨", "🎈", "🎊", "🌟", "💫")
        val MINI_EMOJIS = listOf("✅", "✨", "🌟")

        /** 主页"认识啦"撒花 */
        fun celebrateSpec(): BurstSpec = BurstSpec()

        /** 游戏答对的小撒花 */
        fun miniSpec(): BurstSpec = BurstSpec(
            count = 6,
            emojis = MINI_EMOJIS,
            textSize = 20..32,
            velocityFactor = 0.2f..0.4f,
            durationMs = 600L..600L,
            holdBeforeEndMs = 0L,
            rotate = false
        )

        /** 游戏结算的大撒花 */
        fun bigSpec(): BurstSpec = BurstSpec(
            count = 18,
            textSize = 20..36,
            velocityFactor = 0.35f..0.7f,
            centerYFactor = 1f / 3f
        )
    }
}

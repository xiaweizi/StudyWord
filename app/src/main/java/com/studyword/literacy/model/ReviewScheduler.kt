package com.studyword.literacy.model

/**
 * 错题本与间隔重复(v1.6.0 / P2-1)。
 *
 * 此前"不认识"只是把卡片重新排到队尾 —— 没有任何"错过几次、多久没复习"的记录,
 * 孩子会反复遇到同样的字,而已经掌握的字也会被反复推送。
 *
 * 这里实现一个**简化版 Leitner 盒子**:
 * - 点「不认识」→ 该字进入错题本,盒子归零,记录时间
 * - 之后再复习答对 → 盒子 +1,下次复习间隔拉长(1 → 3 → 7 → 16 天)
 * - 到期的错题会在出题队列里**优先**出现(见 [ProgressRules.partition] 的 due 桶)
 *
 * 全部为纯函数,便于单测(见 app/src/test 的 ReviewSchedulerTest / ReviewCodecTest)。
 */
data class ReviewState(
    /** 累计标记"不认识"的次数,用于把错得最多的字排在前面 */
    val wrongCount: Int,
    /** Leitner 盒子编号(连续答对次数),越大下次复习越晚 */
    val box: Int,
    /** 上次复习时间(毫秒时间戳);0 表示尚未复习过 */
    val lastReviewedAt: Long
)

object ReviewScheduler {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 各盒子对应的复习间隔(天)。
     *
     * - 盒子 0 = **立刻可复习**(0 天):孩子刚答错的字,当下就能进"复习错题"再练一遍。
     *   若设成 1 天,功能在头一天会显示"没有要复习的字",看起来像坏的。
     * - 之后每答对一次升一盒,间隔按 1 → 3 → 7 → 16 天拉长。
     */
    private val INTERVALS_DAYS = longArrayOf(0L, 1L, 3L, 7L, 16L)

    /** 最大盒子编号 = 16 天档 */
    const val MAX_BOX: Int = 4

    /** 盒子编号 → 间隔毫秒 */
    fun intervalMs(box: Int): Long {
        val idx = box.coerceIn(0, INTERVALS_DAYS.lastIndex)
        return INTERVALS_DAYS[idx] * DAY_MS
    }

    /** 某个错题现在是否到期需要复习 */
    fun isDue(state: ReviewState, now: Long): Boolean {
        if (state.lastReviewedAt <= 0L) return true
        return now - state.lastReviewedAt >= intervalMs(state.box)
    }

    /** 下次到期时间 */
    fun nextDueAt(state: ReviewState): Long =
        if (state.lastReviewedAt <= 0L) 0L else state.lastReviewedAt + intervalMs(state.box)

    /** 标记"不认识":进入错题本,盒子归零,从现在开始计算间隔 */
    fun onWrong(previous: ReviewState?, now: Long): ReviewState = ReviewState(
        wrongCount = (previous?.wrongCount ?: 0) + 1,
        box = 0,
        lastReviewedAt = now
    )

    /**
     * 标记"认识":
     * - 该字从未进过错题本 → 返回 null,表示不需要跟踪(避免为 3000 个字都写一条记录)
     * - 曾进过错题本 → 盒子 +1(上限 [MAX_BOX]),刷新复习时间
     */
    fun onKnown(previous: ReviewState?, now: Long): ReviewState? {
        if (previous == null) return null
        return previous.copy(
            box = (previous.box + 1).coerceAtMost(MAX_BOX),
            lastReviewedAt = now
        )
    }

    /**
     * 从一组错题里挑出**现在到期**的键,并按"错得最多、最久没复习"排前。
     * 这是错题复习模式的题池。
     */
    fun dueKeys(states: Map<String, ReviewState>, now: Long): List<String> =
        states.filterValues { isDue(it, now) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, ReviewState>> { it.value.wrongCount }
                    .thenBy { it.value.lastReviewedAt }
            )
            .map { it.key }
}

/**
 * [ReviewState] 的持久化编解码。
 *
 * 用 `;` 分隔记录、`|` 分隔字段 —— 进度键只可能是汉字或 `L:A` / `W:apple`,
 * 不含这两个字符,因此不需要转义。
 *
 * 抽成纯函数是为了可单测:历史曲线那边用 `|`/`,` 拼字符串就是不可测的教训。
 */
object ReviewCodec {

    private const val RECORD_SEP = ";"
    private const val FIELD_SEP = "|"

    fun encode(states: Map<String, ReviewState>): String =
        states.entries
            .sortedBy { it.key }
            .joinToString(RECORD_SEP) { (key, s) ->
                listOf(key, s.wrongCount, s.box, s.lastReviewedAt).joinToString(FIELD_SEP)
            }

    fun decode(raw: String?): Map<String, ReviewState> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, ReviewState>()
        raw.split(RECORD_SEP).forEach { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size != 4) return@forEach
            val key = parts[0]
            val wrong = parts[1].toIntOrNull() ?: return@forEach
            val box = parts[2].toIntOrNull() ?: return@forEach
            val last = parts[3].toLongOrNull() ?: return@forEach
            if (key.isEmpty()) return@forEach
            result[key] = ReviewState(wrong, box, last)
        }
        return result
    }
}

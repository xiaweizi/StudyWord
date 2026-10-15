package com.studyword.literacy.model

/**
 * 进度规则(v1.5.0)。
 *
 * 把"判定一次结果如何改写 known/unknown"与"题池如何排序"这两块纯逻辑
 * 从 Activity 里抽出来,原因有二:
 * 1. 它们此前散落在 [com.studyword.literacy.ui.MainActivity] 与
 *    [com.studyword.literacy.ui.CharacterLibraryActivity] 里各写一份,容易改漏;
 * 2. 抽成纯函数后可以脱离 Android 框架做 JVM 单元测试(见 app/src/test)。
 *
 * 所有键均为 [ProgressKey] 生成的内容键(汉字 / "L:A" / "W:apple")。
 */
object ProgressRules {

    /**
     * 记录一次判定:known / unknown 互斥,以本次结果为准。
     *
     * @param isKnown true = 认识,false = 不认识
     */
    fun apply(
        known: MutableSet<String>,
        unknown: MutableSet<String>,
        key: String,
        isKnown: Boolean
    ) {
        if (isKnown) {
            known.add(key)
            unknown.remove(key)
        } else {
            unknown.add(key)
            known.remove(key)
        }
    }

    /**
     * 显式设置某个键的状态(字库页长按改状态用)。
     *
     * @param status true = 认识,false = 不认识,null = 回到未学习
     */
    fun setStatus(
        known: MutableSet<String>,
        unknown: MutableSet<String>,
        key: String,
        status: Boolean?
    ) {
        when (status) {
            true -> {
                known.add(key)
                unknown.remove(key)
            }
            false -> {
                unknown.add(key)
                known.remove(key)
            }
            null -> {
                known.remove(key)
                unknown.remove(key)
            }
        }
    }

    /** 当前状态:已认识 / 待巩固 / 未学习 */
    fun statusOf(known: Set<String>, unknown: Set<String>, key: String): LearnStatus = when {
        key in known -> LearnStatus.KNOWN
        key in unknown -> LearnStatus.UNKNOWN
        else -> LearnStatus.UNSEEN
    }

    /**
     * 按"到期错题 → 待巩固 → 未测 → 已掌握"切分题池。
     *
     * v1.4.4 的顺序是"待巩固 → 未测 → 已掌握";v1.6.0 在最前面加了**到期的错题**桶
     * (间隔重复,见 [ReviewScheduler])。默认 [dueKeys] 为空,行为与旧版完全一致。
     *
     * @param keyOf   从条目取出进度键(StudyItem 用 `{ it.progressKey }`)
     * @param dueKeys 现在到期需要复习的错题键
     */
    fun <T> partition(
        items: List<T>,
        keyOf: (T) -> String,
        known: Set<String>,
        unknown: Set<String>,
        dueKeys: Set<String> = emptySet()
    ): QueuePartition<T> {
        val due = mutableListOf<T>()
        val review = mutableListOf<T>()
        val untested = mutableListOf<T>()
        val mastered = mutableListOf<T>()
        items.forEach { item ->
            val key = keyOf(item)
            when {
                key in dueKeys -> due += item
                key in unknown -> review += item
                key !in known -> untested += item
                else -> mastered += item
            }
        }
        return QueuePartition(due, review, untested, mastered)
    }

    data class QueuePartition<T>(
        /** 到期错题(间隔重复优先) */
        val due: List<T>,
        val review: List<T>,
        val untested: List<T>,
        val mastered: List<T>
    ) {
        /** 拼接成最终入队顺序 */
        fun ordered(): List<T> = due + review + untested + mastered
    }
}

/** 学习状态 */
enum class LearnStatus {
    KNOWN,
    UNKNOWN,
    UNSEEN
}

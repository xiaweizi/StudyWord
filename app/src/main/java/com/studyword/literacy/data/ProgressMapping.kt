package com.studyword.literacy.data

/**
 * 旧版「位置 id」进度 → 新版「内容键」进度的纯映射逻辑(v1.5.0)。
 *
 * 抽成不依赖 Android/仓库的纯函数,便于在 app/src/test 里直接覆盖
 * "插字后进度不错位""越界 id 被丢弃"等关键场景。
 */
object ProgressMapping {

    /**
     * 中文:旧 id 集合 → 汉字集合。maps 中不存在的 id(越界/已删除)被丢弃。
     */
    fun chineseKeys(legacyIds: Set<Int>, hanziById: Map<Int, String>): Set<String> =
        legacyIds.mapNotNull { hanziById[it] }.toSet()

    /**
     * 英文:旧 id 集合 → 内容键集合("L:A" / "W:apple")。
     * maps 中不存在的 id 被丢弃。
     */
    fun englishKeys(legacyIds: Set<Int>, keyById: Map<Int, String>): Set<String> =
        legacyIds.mapNotNull { keyById[it] }.toSet()

    /**
     * 统计被丢弃的 id 数量(即旧集合里有、但当前字库已无对应项的 id)。
     * 用于日志:这些正是 v1.4.4 会虚高"认识：N"的脏数据。
     */
    fun droppedCount(legacyIds: Set<Int>, mapped: Set<String>): Int =
        legacyIds.size - mapped.size
}

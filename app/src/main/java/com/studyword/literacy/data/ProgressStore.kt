package com.studyword.literacy.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.studyword.literacy.model.ProgressKey
import com.studyword.literacy.model.ReviewCodec
import com.studyword.literacy.model.ReviewScheduler
import com.studyword.literacy.model.ReviewState

/**
 * 本地进度持久化。
 *
 * v1.5.0 关键变更 —— **进度主键从"位置 id"改为"内容键"**：
 * - 中文:直接存汉字本身(`"天"`、`"地"`)
 * - 英文:存带类别前缀的键(`"L:A"` = 字母 A,`"W:apple"` = 单词 apple)
 *
 * 旧版本把 int id 写进 prefs,而 id 是解析字库时按顺序 `nextId++` 生成的。
 * 一旦在 character_sets.json 中间插入/删除一个字,其后所有 id 平移,
 * 历史进度就会整体错位到别的字上(且静默无报错)。
 * 换成内容键后,字库顺序可以任意调整而进度天然稳定。
 *
 * 旧数据由 [ProgressMigrator] 在启动时一次性转换(见 [WAS_MIGRATED_V2])。
 *
 * 多档案(v1.5.0 预留):[profileId] 决定 SharedPreferences 文件名。
 * 默认档案沿用历史文件名 `literacy_progress`,老用户数据不受影响。
 */
class ProgressStore(
    context: Context,
    private val profileId: String = PROFILE_DEFAULT
) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        prefsNameFor(profileId),
        Context.MODE_PRIVATE
    )

    // ========================= 中文(汉字)维度 =========================

    fun loadKnown(): MutableSet<String> = readStringSet(KEY_KNOWN)

    fun loadUnknown(): MutableSet<String> = readStringSet(KEY_UNKNOWN)

    fun save(known: Set<String>, unknown: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_KNOWN, known.toSet())
            .putStringSet(KEY_UNKNOWN, unknown.toSet())
            .apply()
    }

    // ========================= 英文(字母 + 单词)维度 =========================

    fun loadEnglishKnown(): MutableSet<String> = readStringSet(KEY_KNOWN_EN)

    fun loadEnglishUnknown(): MutableSet<String> = readStringSet(KEY_UNKNOWN_EN)

    fun saveEnglish(known: Set<String>, unknown: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_KNOWN_EN, known.toSet())
            .putStringSet(KEY_UNKNOWN_EN, unknown.toSet())
            .apply()
    }

    // ========================= 当前学习语种 / 难度 / 子模式 =========================
    // v1.5.0:难度与英文子模式此前完全没持久化,导致旋转屏幕或重启后被重置为"简单"。

    fun loadLanguage(): String = prefs.getString(KEY_LANGUAGE, null) ?: LANGUAGE_CHINESE

    fun saveLanguage(mode: String) {
        prefs.edit().putString(KEY_LANGUAGE, mode).apply()
    }

    /** 返回上次使用的难度名(EASY/MEDIUM/HARD);从未设置时返回 null,由调用方决定默认值 */
    fun loadDifficulty(): String? = prefs.getString(KEY_DIFFICULTY, null)

    fun saveDifficulty(name: String) {
        prefs.edit().putString(KEY_DIFFICULTY, name).apply()
    }

    /** 返回上次使用的英文子模式名(LETTERS/WORDS);从未设置时返回 null */
    fun loadEnglishSubMode(): String? = prefs.getString(KEY_ENGLISH_SUB_MODE, null)

    fun saveEnglishSubMode(name: String) {
        prefs.edit().putString(KEY_ENGLISH_SUB_MODE, name).apply()
    }

    // ========================= 迁移支持(v1.5.0) =========================

    /** 旧版 int id 集合是否已成功迁移到内容键 */
    fun isMigratedToKeys(): Boolean = prefs.getBoolean(KEY_MIGRATED_V2, false)

    fun markMigratedToKeys() {
        prefs.edit().putBoolean(KEY_MIGRATED_V2, true).apply()
    }

    /**
     * 读取 v1.4.4 及更早版本写入的 int id 集合(仅迁移使用)。
     * 返回 (中文 known, 中文 unknown, 英文 known, 英文 unknown)。
     */
    fun readLegacyIdSets(): LegacyIdSets = LegacyIdSets(
        chineseKnown = readIntSet(KEY_KNOWN),
        chineseUnknown = readIntSet(KEY_UNKNOWN),
        englishKnown = readIntSet(KEY_KNOWN_EN),
        englishUnknown = readIntSet(KEY_UNKNOWN_EN)
    )

    /** 旧版 int id 集合快照 */
    data class LegacyIdSets(
        val chineseKnown: Set<Int>,
        val chineseUnknown: Set<Int>,
        val englishKnown: Set<Int>,
        val englishUnknown: Set<Int>
    ) {
        val isEmpty: Boolean
            get() = chineseKnown.isEmpty() && chineseUnknown.isEmpty() &&
                englishKnown.isEmpty() && englishUnknown.isEmpty()
    }

    // ========================= 重置 =========================

    // ========================= 错题本 / 间隔重复(v1.6.0) =========================

    /** 读取错题本状态(键 → 错题次数/盒子/上次复习时间) */
    fun loadReviewState(): Map<String, ReviewState> =
        ReviewCodec.decode(prefs.getString(KEY_REVIEW, null))

    fun saveReviewState(states: Map<String, ReviewState>) {
        prefs.edit().putString(KEY_REVIEW, ReviewCodec.encode(states)).apply()
    }

    /**
     * 记录一次"不认识":该字进入错题本,盒子归零。
     * @return 更新后的状态
     */
    fun recordWrong(key: String, now: Long): ReviewState {
        val states = loadReviewState().toMutableMap()
        val updated = ReviewScheduler.onWrong(states[key], now)
        states[key] = updated
        saveReviewState(states)
        return updated
    }

    /**
     * 记录一次"认识"。只有曾进过错题本的字才会被跟踪,
     * 避免为 3000 个字都写一条无意义的记录。
     */
    fun recordCorrect(key: String, now: Long) {
        val states = loadReviewState().toMutableMap()
        val updated = ReviewScheduler.onKnown(states[key], now) ?: return
        states[key] = updated
        saveReviewState(states)
    }

    /** 当前到期的错题键(按"错得最多、最久没复习"排序) */
    fun dueReviewKeys(now: Long): List<String> =
        ReviewScheduler.dueKeys(loadReviewState(), now)

    /** 删除指定语言之外的错题记录(重置某一语种时用) */
    private fun keepReviewState(keepEnglish: Boolean) {
        val states = loadReviewState()
        val filtered = states.filterKeys { ProgressKey.isEnglish(it) == keepEnglish }
        if (filtered.size != states.size) saveReviewState(filtered)
    }

    // ========================= 重置 =========================

    /** 仅重置中文进度(含中文错题本),英文进度保留 */
    fun resetChinese() {
        keepReviewState(keepEnglish = true)
        prefs.edit()
            .remove(KEY_KNOWN)
            .remove(KEY_UNKNOWN)
            .apply()
    }

    /** 仅重置英文进度(含英文错题本),中文 known/unknown/history 保留 */
    fun resetEnglish() {
        keepReviewState(keepEnglish = false)
        prefs.edit()
            .remove(KEY_KNOWN_EN)
            .remove(KEY_UNKNOWN_EN)
            .apply()
    }

    // ========================= 历史曲线(中文维度) =========================

    fun recordSnapshot(knownCount: Int, unknownCount: Int) {
        val history = loadHistory().toMutableList()
        val last = history.lastOrNull()
        if (last != null &&
            last.knownCount == knownCount &&
            last.unknownCount == unknownCount
        ) {
            return
        }
        history += ProgressSnapshot(
            timestamp = System.currentTimeMillis(),
            knownCount = knownCount,
            unknownCount = unknownCount
        )
        val trimmed = if (history.size > MAX_HISTORY_SIZE) {
            history.takeLast(MAX_HISTORY_SIZE)
        } else {
            history
        }
        val serialized = trimmed.joinToString(HISTORY_DELIMITER) {
            listOf(it.timestamp, it.knownCount, it.unknownCount).joinToString(ENTRY_DELIMITER)
        }
        prefs.edit().putString(KEY_HISTORY, serialized).apply()
    }

    fun loadHistory(): List<ProgressSnapshot> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(HISTORY_DELIMITER).mapNotNull { entry ->
            val parts = entry.split(ENTRY_DELIMITER)
            if (parts.size != 3) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            val known = parts[1].toIntOrNull() ?: return@mapNotNull null
            val unknown = parts[2].toIntOrNull() ?: return@mapNotNull null
            ProgressSnapshot(timestamp, known, unknown)
        }
    }

    // ========================= 内部 =========================

    private fun readStringSet(key: String): MutableSet<String> =
        prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun readIntSet(key: String): Set<Int> =
        prefs.getStringSet(key, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    companion object {
        private const val TAG = "ProgressStore"
        private const val PREFS_NAME = "literacy_progress"
        const val PROFILE_DEFAULT = "default"

        /**
         * 某个档案对应的 SharedPreferences 文件名。
         *
         * 这是**唯一**的文件名规则来源:[ProfileStore] 删除档案时也用它,
         * 避免两处各写一份导致"删了注册表却没删数据"。
         *
         * 默认档案刻意沿用历史文件名,让 v1.4.x 老用户的进度无缝接上。
         */
        fun prefsNameFor(profileId: String): String =
            if (profileId == PROFILE_DEFAULT) PREFS_NAME else "${PREFS_NAME}_$profileId"

        /**
         * 用**当前选中的孩子档案**构造。
         *
         * 各 Activity 都应通过它取 [ProgressStore],不要直接 `ProgressStore(ctx)` ——
         * 否则切换档案后仍会读写上一个孩子的数据。
         */
        fun active(context: Context): ProgressStore =
            ProgressStore(context, ProfileStore(context).activeProfileId())

        // 中文维度 key(v1.5.0 起存汉字)
        private const val KEY_KNOWN = "known_ids"
        private const val KEY_UNKNOWN = "unknown_ids"
        private const val KEY_HISTORY = "history_entries"
        // 英文维度 key(v1.5.0 起存 "L:A" / "W:apple")
        private const val KEY_KNOWN_EN = "known_english_ids"
        private const val KEY_UNKNOWN_EN = "unknown_english_ids"
        // 偏好
        private const val KEY_LANGUAGE = "current_language"
        private const val KEY_DIFFICULTY = "current_difficulty"
        private const val KEY_ENGLISH_SUB_MODE = "english_sub_mode"
        // 迁移
        private const val KEY_MIGRATED_V2 = "progress_keys_v2_migrated"
        // 错题本 / 间隔重复
        private const val KEY_REVIEW = "review_state"

        const val LANGUAGE_CHINESE = "CHINESE"

        private const val HISTORY_DELIMITER = "|"
        private const val ENTRY_DELIMITER = ","
        private const val MAX_HISTORY_SIZE = 60
    }
}

data class ProgressSnapshot(
    val timestamp: Long,
    val knownCount: Int,
    val unknownCount: Int
)

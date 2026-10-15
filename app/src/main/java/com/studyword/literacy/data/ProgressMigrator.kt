package com.studyword.literacy.data

import android.util.Log
import com.studyword.literacy.model.ProgressKey

/**
 * v1.5.0 一次性数据迁移:把 v1.4.4 及更早版本写入的「位置 id」进度
 * 转换成「内容键」进度([ProgressKey])。
 *
 * 背景:v1.4.4 的 id 是解析字库时按顺序 `nextId++` 生成的,
 * 字库一旦增删就会导致历史进度整体错位(静默、无报错)。
 * 换成汉字/单词内容键后问题消失。
 *
 * 迁移是幂等的:完成后写入 [ProgressStore.markMigratedToKeys] 标记,后续启动直接跳过。
 * 旧 key 内容**保留不删**,以便需要时人工核对或回滚到旧版本。
 * 纯映射逻辑在 [ProgressMapping],可脱离 Android 测试。
 */
object ProgressMigrator {

    private const val TAG = "ProgressMigrator"

    /**
     * 如需要则执行迁移。
     *
     * @return true 表示本次真的执行了迁移
     */
    fun migrateIfNeeded(
        store: ProgressStore,
        characters: CharacterRepository,
        english: EnglishRepository
    ): Boolean {
        if (store.isMigratedToKeys()) return false

        val legacy = store.readLegacyIdSets()
        if (legacy.isEmpty) {
            // 全新安装(或已无旧数据),不需要转换
            store.markMigratedToKeys()
            return false
        }

        val hanziById: Map<Int, String> = characters.all().associate { it.id to it.hanzi }
        val englishKeyById: Map<Int, String> = buildMap {
            english.letters().forEach { put(it.id, ProgressKey.letter(it.uppercase)) }
            english.words().forEach { put(it.id, ProgressKey.word(it.word)) }
        }

        val knownZh = ProgressMapping.chineseKeys(legacy.chineseKnown, hanziById)
        val unknownZh = ProgressMapping.chineseKeys(legacy.chineseUnknown, hanziById)
        val knownEn = ProgressMapping.englishKeys(legacy.englishKnown, englishKeyById)
        val unknownEn = ProgressMapping.englishKeys(legacy.englishUnknown, englishKeyById)

        store.save(knownZh, unknownZh)
        store.saveEnglish(knownEn, unknownEn)
        store.markMigratedToKeys()

        Log.i(
            TAG,
            "已迁移进度到内容键:中文 ${knownZh.size}+${unknownZh.size} 条" +
                "(丢弃越界 id ${ProgressMapping.droppedCount(legacy.chineseKnown, knownZh) + ProgressMapping.droppedCount(legacy.chineseUnknown, unknownZh)})," +
                "英文 ${knownEn.size}+${unknownEn.size} 条" +
                "(丢弃越界 id ${ProgressMapping.droppedCount(legacy.englishKnown, knownEn) + ProgressMapping.droppedCount(legacy.englishUnknown, unknownEn)})"
        )
        return true
    }
}

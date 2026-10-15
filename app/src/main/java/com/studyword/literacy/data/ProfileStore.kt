package com.studyword.literacy.data

import android.content.Context
import com.studyword.literacy.model.ChildProfile
import com.studyword.literacy.model.ProfileCodec
import com.studyword.literacy.model.ProfileRules

/**
 * 孩子档案注册表(v1.6.0 / P2-2)。
 *
 * 为什么单独一个 SharedPreferences 文件
 * ------------------------------------
 * 每个孩子的学习进度分别在 `literacy_progress[_<id>]` 里(由 [ProgressStore] 决定),
 * 但"有哪些孩子、当前是谁"这份**注册表本身不能放在任何一个孩子的档案里** ——
 * 否则删掉某个孩子会连带删掉注册表。所以固定放在 [PREFS_NAME] 里。
 *
 * 兼容性
 * ------
 * 首次运行时注册表为空,此时会**自动创建一个 id = [ProgressStore.PROFILE_DEFAULT]、
 * 名为「宝贝」的档案**。因为默认档案用的正是历史文件名 `literacy_progress`,
 * 老用户升级后进度照旧,不会丢。
 */
class ProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 全部档案,按创建时间升序 */
    fun profiles(): List<ChildProfile> = ensureSeeded()

    /** 当前档案 id;注册表损坏或指向不存在的档案时,回落到第一个 */
    fun activeProfileId(): String {
        val list = ensureSeeded()
        val saved = prefs.getString(KEY_ACTIVE, null)
        return if (saved != null && list.any { it.id == saved }) saved else list.first().id
    }

    fun activeProfile(): ChildProfile =
        profiles().first { it.id == activeProfileId() }

    /** 切换当前档案;id 不存在时不生效 */
    fun setActiveProfile(id: String) {
        if (profiles().none { it.id == id }) return
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /**
     * 新建档案。[rawName] 为空或规范化后为空时,自动取「宝贝 / 宝贝2 / …」。
     * @return 新档案
     */
    fun create(rawName: String?): ChildProfile {
        val list = profiles()
        val name = ProfileRules.normalizeName(rawName)
            .ifEmpty { ProfileRules.nextDefaultName(list) }
        val profile = ChildProfile(
            id = newProfileId(),
            name = name,
            createdAt = System.currentTimeMillis()
        )
        save(list + profile)
        return profile
    }

    /** 改名(只改显示名,不动 id,所以进度不受影响) */
    fun rename(id: String, rawName: String) {
        val list = profiles()
        val name = ProfileRules.normalizeName(rawName)
        if (name.isEmpty()) return
        save(list.map { if (it.id == id) it.copy(name = name) else it })
    }

    /**
     * 删除档案**连同它的全部进度**。
     *
     * - 只剩一个档案时拒绝删除(返回 false),否则数据无处可放
     * - 若删的正是当前档案,自动切换到剩下的第一个
     *
     * @return 是否删除成功
     */
    @Suppress("ApplySharedPref") // 见方法内注释:删除路径上需要同步落盘
    fun delete(id: String): Boolean {
        val list = profiles()
        if (!ProfileRules.canDelete(list)) return false
        if (list.none { it.id == id }) return false

        // 先清掉这个孩子的进度文件,再从注册表移除
        // 文件名规则统一来自 ProgressStore,避免两处各写一份导致"删了注册表却没删数据"
        // 这里刻意用 commit() 而不是 lint 建议的 apply():
        // 删除是**破坏性且不可恢复**的操作,而 apply() 是异步落盘 ——
        // 若进程紧接着被杀,可能出现"注册表已移除但进度文件还在"(孤儿数据),
        // 或"界面提示已删除、实际还没写下去"。删除是低频操作,同步写代价可忽略。
        appContext.getSharedPreferences(
            ProgressStore.prefsNameFor(id),
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        val remaining = list.filterNot { it.id == id }
        save(remaining)
        if (prefs.getString(KEY_ACTIVE, null) == id) {
            prefs.edit().putString(KEY_ACTIVE, remaining.first().id).apply()
        }
        return true
    }

    // ========================= 内部 =========================

    private fun ensureSeeded(): List<ChildProfile> {
        val existing = ProfileCodec.decode(prefs.getString(KEY_PROFILES, null))
        if (existing.isNotEmpty()) return existing

        // 首次运行:建默认档案,id 固定为 PROFILE_DEFAULT 以承接历史进度
        val seed = ChildProfile(
            id = ProgressStore.PROFILE_DEFAULT,
            name = ProfileRules.DEFAULT_NAME,
            createdAt = System.currentTimeMillis()
        )
        save(listOf(seed))
        prefs.edit().putString(KEY_ACTIVE, seed.id).apply()
        return listOf(seed)
    }

    private fun save(list: List<ChildProfile>) {
        prefs.edit().putString(KEY_PROFILES, ProfileCodec.encode(list)).apply()
    }

    private fun newProfileId(): String =
        "p" + System.currentTimeMillis().toString(36)

    companion object {
        private const val PREFS_NAME = "literacy_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_profile"
    }
}

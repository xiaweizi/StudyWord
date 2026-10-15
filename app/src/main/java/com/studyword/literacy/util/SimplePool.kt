package com.studyword.literacy.util

/**
 * 极简对象池。抽出来只有一个目的:**让"取/还"语义可以被单元测试锁住**。
 *
 * 为什么值得单独一个类
 * --------------------
 * 内置语音包用 MediaPlayer 播音频,MediaPlayer 无法在 JVM 单测里构造,
 * 于是池的逻辑一直没被测到 —— 结果踩了一个很隐蔽的 bug:
 *
 * 旧实现 `obtain()` 是**轮转**式(`removeFirst()` 后再 `addLast()`),
 * 实例在"使用中"期间**仍然留在池里**。这样 [release] 里的
 * `!items.contains(item)` 判定为 false,于是走了销毁分支,
 * **把一个正在使用的实例销毁了,池里却还留着它的引用**。
 *
 * 症状极具迷惑性:前两次播放正常(第 1 次新建、第 2 次拿到活实例),
 * 从第 3 次起每次拿到的都是已销毁的实例 → 抛异常 → 回退 TTS → 没声音。
 * 用户观察到"只有开头几声能响"才定位到此处。
 *
 * 因此 [obtain] 必须**把实例取走**,让"在池中"严格等价于"空闲可用"。
 *
 * @param maxSize 池中最多保留多少个空闲实例
 * @param factory 池空时如何创建新实例
 */
internal class SimplePool<T : Any>(
    private val maxSize: Int,
    private val factory: () -> T,
) {

    private val idle = ArrayDeque<T>()

    /** 当前空闲实例数量(测试与诊断用) */
    val size: Int get() = idle.size

    /**
     * 取出一个实例。
     *
     * **取走后它就不再属于池**,直到 [release] 归还 —— 这是本类最关键的约束,
     * 否则"是否在池中"就无法表达"是否空闲"。
     */
    fun obtain(): T = if (idle.isNotEmpty()) idle.removeFirst() else factory()

    /**
     * 归还一个实例。
     * 池未满则留作空闲;池已满或该实例已在池中(重复归还)则交给 [dispose] 销毁。
     */
    fun release(item: T, dispose: (T) -> Unit = {}) {
        if (idle.size < maxSize && !idle.contains(item)) {
            idle.addLast(item)
        } else {
            dispose(item)
        }
    }

    /** 清空并销毁全部空闲实例 */
    fun clear(dispose: (T) -> Unit = {}) {
        while (idle.isNotEmpty()) dispose(idle.removeFirst())
    }
}

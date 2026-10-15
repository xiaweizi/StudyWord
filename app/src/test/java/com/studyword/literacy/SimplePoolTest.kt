package com.studyword.literacy

import com.studyword.literacy.util.SimplePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对象池语义测试。
 *
 * 这些用例锁住的是一个**真实踩过的 bug**:内置语音包的 MediaPlayer 池
 * 原本用"轮转"方式取实例(`removeFirst` 后再 `addLast`),
 * 导致实例在"使用中"仍留在池里,归还时被误判为"已在池中"而**销毁**,
 * 池里却留着已销毁的引用 —— 表现为"只有开头几声能响,之后全没声音"。
 *
 * MediaPlayer 本身无法在 JVM 单测里构造,所以把池逻辑抽成 [SimplePool]
 * 以便在纯 JVM 下覆盖。
 */
class SimplePoolTest {

    /** 记录被销毁的对象,用于断言销毁时机 */
    private class Handle {
        var disposed = false
    }

    private fun newPool(maxSize: Int = 3) = SimplePool(maxSize) { Handle() }

    @Test
    fun `取出后实例不再属于池`() {
        val pool = newPool()
        val a = pool.obtain()
        // 这一条就是那个 bug 的核心:取走后池必须为空,
        // 否则"在池中"无法等价于"空闲"
        assertEquals("obtain 必须把实例取走,不能留在池里", 0, pool.size)
        pool.release(a)
        assertEquals(1, pool.size)
    }

    @Test
    fun `归还后能复用同一实例`() {
        val pool = newPool()
        val a = pool.obtain()
        pool.release(a) {}
        assertSame("应从池中取回同一个实例,而不是新建", a, pool.obtain())
    }

    @Test
    fun `在用的实例不会被误销毁`() {
        val pool = newPool()
        val a = pool.obtain()
        // 模拟旧 bug 的场景:取走后立刻归还,再取用 —— 期间不应销毁
        pool.release(a) { it.disposed = true }
        val again = pool.obtain()
        assertTrue("归还后的实例不应被销毁", !again.disposed)
        assertSame(a, again)
    }

    @Test
    fun `池满时归还触发销毁`() {
        val pool = newPool(maxSize = 2)
        val a = pool.obtain()
        val b = pool.obtain()
        val c = pool.obtain()
        pool.release(a) { it.disposed = true }
        pool.release(b) { it.disposed = true }
        assertEquals(2, pool.size)
        // 池已满 → c 应被销毁
        pool.release(c) { it.disposed = true }
        assertTrue("池满时多出的实例必须被销毁,否则会泄漏", c.disposed)
        assertEquals(2, pool.size)
    }

    @Test
    fun `重复归还不产生重复条目`() {
        val pool = newPool()
        val a = pool.obtain()
        pool.release(a) {}
        pool.release(a) { it.disposed = true }
        assertEquals("同一个实例重复归还只应保留一份", 1, pool.size)
        assertTrue("重复归还的那次应走销毁分支", a.disposed)
    }

    @Test
    fun `取完再还_连续多轮都不会拿到已销毁实例`() {
        // 直接复现"开头能响、后面不行"的场景:连续 10 轮取用+归还
        val pool = newPool(maxSize = 3)
        var last: Handle? = null
        repeat(10) { round ->
            val h = pool.obtain()
            assertTrue("第 ${round + 1} 轮拿到了已销毁的实例", !h.disposed)
            if (round == 0) {
                pool.release(h) { it.disposed = true }
                last = h
            } else {
                pool.release(h) { it.disposed = true }
            }
        }
        assertNotSame("首轮实例应已回到池中复用", Handle(), last)
    }

    @Test
    fun `clear 销毁全部空闲实例`() {
        val pool = newPool()
        val a = pool.obtain()
        val b = pool.obtain()
        pool.release(a) {}
        pool.release(b) {}
        assertEquals(2, pool.size)
        pool.clear { it.disposed = true }
        assertEquals(0, pool.size)
        assertTrue(a.disposed)
        assertTrue(b.disposed)
    }

    @Test
    fun `池空时工厂被调用`() {
        var created = 0
        val pool = SimplePool(3) { created++; Handle() }
        pool.obtain()
        pool.obtain()
        assertEquals(2, created)
    }
}

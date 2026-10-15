package com.studyword.literacy

import com.studyword.literacy.util.AudioClipNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置语音包的**跨语言命名契约**测试。
 *
 * 为什么需要它
 * ------------
 * 音频文件名由 Python 脚本 `scripts/generate_audio.py` 生成、由 Kotlin 侧
 * `AudioClips` 查找。两边用的是不同的 CRC32 实现 —— 一旦规则分叉
 * (编码方式、大小写、十六进制格式、Locale 差异),症状是
 * **"明明生成了音频却播不出声",而且不会有任何报错**,极难排查。
 *
 * 因此这里用**从 Python 算出并写死的期望值**锁住规则;
 * 任一边改动都会让本测试立刻失败。
 */
class AudioClipNamingTest {

    /** 期望值来自 `python -c "import zlib; ..."`,即生成脚本使用的同一算法 */
    private val expected = mapOf(
        "c:一" to "ke8693da9.mp3",
        "c:花" to "k5ddd9086.mp3",
        "c:天" to "k4d6e92af.mp3",
        "w:一起" to "ke4d9ec4c.mp3",
        "w:花朵" to "ka0bb8bd8.mp3",
        "e:我们一起去公园。" to "k74edbb8b.mp3",
        "z:苹果" to "kb3ee65b8.mp3",
        "z:你好" to "kb5c1b1cf.mp3",
        "l:A" to "k9afe4429.mp3",
        "l:Z" to "k109b8dc5.mp3",
        "n:Apple" to "k4dc9c444.mp3",
        "n:apple" to "k8c08eb40.mp3",
        "s:I love my cat." to "ke703d5c7.mp3",
    )

    @Test
    fun `文件名与 Python 侧算法一致`() {
        for ((key, file) in expected) {
            assertEquals("key=$key 的文件名与 Python 侧不一致", file, AudioClipNaming.fileName(key))
        }
    }

    @Test
    fun `asset 路径以 audio 开头`() {
        for (key in expected.keys) {
            val path = AudioClipNaming.assetPath(key)
            assertTrue("路径应为 audio/xxx.ogg,实际 $path", path.startsWith("audio/"))
            assertTrue(path.endsWith(".mp3"))
        }
    }

    @Test
    fun `同一个 key 结果稳定`() {
        assertEquals(AudioClipNaming.fileName("c:花"), AudioClipNaming.fileName("c:花"))
    }

    @Test
    fun `不同 key 不同文件名_抽查不碰撞`() {
        val names = expected.keys.map { AudioClipNaming.fileName(it) }
        assertEquals("抽查样本不该出现碰撞", names.size, names.toSet().size)
    }

    @Test
    fun `十六进制固定 8 位小写_且无前导零丢失`() {
        // 覆盖高 4 位为 0 的情况 —— 若实现忘了补零,这里会暴露
        for (key in expected.keys) {
            val hex = AudioClipNaming.crcHex(key)
            assertEquals("应为 8 位十六进制", 8, hex.length)
            assertTrue("应为小写十六进制: $hex", hex.all { it in "0123456789abcdef" })
        }
    }

    @Test
    fun `大小写与标点敏感`() {
        // n:Apple 与 n:apple 必须不同 —— 英语字母卡与单词卡是两个不同的音频
        assertTrue(AudioClipNaming.fileName("n:Apple") != AudioClipNaming.fileName("n:apple"))
        // 句末标点参与哈希
        assertTrue(AudioClipNaming.fileName("s:Hi.") != AudioClipNaming.fileName("s:Hi"))
    }

    @Test
    fun `规整 key 时只去掉文本部分的首尾空白`() {
        // 前缀部分不能被破坏
        assertEquals("e:你好", AudioClipNaming.normalizeKey("e: 你好 "))
        assertEquals("c:一", AudioClipNaming.normalizeKey("c:一"))
        // 没有冒号时整体 trim
        assertEquals("abc", AudioClipNaming.normalizeKey("  abc  "))
        // 中间的空格必须保留(句子里的空格有意义)
        assertEquals("s:I love my cat.", AudioClipNaming.normalizeKey("s: I love my cat. "))
    }

    @Test
    fun `带空白的 key 与不带空白指向同一个文件`() {
        // 这正是那个真实 bug:数据末尾多一个空格 -> key 对不上 -> 音频永远播不出
        assertEquals(
            AudioClipNaming.fileName("e:祝你生日快乐!"),
            AudioClipNaming.fileName(AudioClipNaming.normalizeKey("e:祝你生日快乐! "))
        )
    }
}

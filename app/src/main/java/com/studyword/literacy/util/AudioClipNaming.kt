package com.studyword.literacy.util

import java.util.zip.CRC32

/**
 * 内置语音包的 key → 文件名规则。
 *
 * ⚠️ **必须与 `scripts/generate_audio.py` 的 `clip_name()` 完全一致** ——
 * 一边是生成、一边是查找,不一致就会"明明生成了却找不到音频"。
 * 因此这里刻意抽成纯函数,并由 [com.studyword.literacy.AudioClipNamingTest]
 * 用 Python 侧算出的固定值锁死,任何一边改动都会立刻失败。
 *
 * 约定:
 * - key 形如 `c:一` / `w:一起` / `e:<句子>` / `z:<中文>` / `l:A` / `n:Apple` / `s:<英文>`
 * - 文件名 = `k` + CRC32(key 的 UTF-8 字节) 的 8 位小写十六进制 + `.ogg`
 */
object AudioClipNaming {

    const val DIR = "audio"
    const val EXT = "mp3"

    private const val HEX = "0123456789abcdef"

    /**
     * CRC32 的 8 位小写十六进制。
     *
     * 不用 `String.format("%08x")` 是因为它走默认 Locale,
     * 在某些区域设置下可能产生非 ASCII 数字 —— 那样文件名就会与 Python 侧对不上。
     * 这里手工拼十六进制,结果与语言环境无关。
     */
    fun crcHex(key: String): String {
        val crc = CRC32().apply { update(key.toByteArray(Charsets.UTF_8)) }.value
        val sb = StringBuilder(8)
        for (shift in intArrayOf(28, 24, 20, 16, 12, 8, 4, 0)) {
            sb.append(HEX[((crc ushr shift) and 0xFL).toInt()])
        }
        return sb.toString()
    }

    /** 文件名,如 `ke8693da9.ogg` */
    fun fileName(key: String): String = "k${crcHex(key)}.$EXT"

    /** assets 下的相对路径,如 `audio/ke8693da9.ogg` */
    fun assetPath(key: String): String = "$DIR/${fileName(key)}"

    /**
     * 规整 key:`前缀 + 冒号 + 文本`,**只对文本部分**去首尾空白。
     *
     * 为什么必须与生成脚本一致
     * ----------------------
     * key 由文本拼成(如 `e:祝你生日快乐!`),生成脚本建 key 时会 strip。
     * 数据里若混入一个首尾空格,两边 key 就对不上 —— 这条音频**永远播不出**,
     * 而症状只是"某一句没声音",极难定位。
     *
     * 实测踩过一次:`乐` 的例句是 `"祝你生日快乐! "`(末尾多一个空格)。
     * 数据已清理,这个函数作为兜底,保证 App 与脚本的算法严格对齐。
     */
    fun normalizeKey(key: String): String {
        val i = key.indexOf(':')
        return if (i < 0) key.trim() else key.substring(0, i + 1) + key.substring(i + 1).trim()
    }
}

package com.studyword.literacy.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.IOException

/**
 * 离线拼音表(v1.5.0)。
 *
 * 读取构建期由 `scripts/generate_pinyin.py`(pypinyin)生成的 `assets/pinyin_table.json`,
 * 形如 `{"天":"tiān","长":"zhǎng",...}`,key 是汉字本身 —— 与字库顺序无关。
 *
 * 采用"构建期生成 + 运行期查表"取代"运行期 ICU 推导",解决三个问题:
 * 1. **API 26~28 崩溃**:`android.icu.text.Transliterator` 的公开方法到 API 29 才有,
 *    而本应用 minSdk 26、且首屏就要为 3000 个字生成拼音。
 * 2. **多音字准确性**:ICU 的 Han-Latin/Names 是人名规则集;pypinyin 基于词频更准。
 * 3. **首启卡顿**:不再有 3000 次 ICU 转写与数千次正则编译。
 *
 * 表缺失(例如被裁剪)时回落到 [com.studyword.literacy.util.PinyinConverter],
 * 不会崩溃、也不会丢字。
 */
object PinyinTable {

    private const val TAG = "PinyinTable"
    private const val ASSET_FILE = "pinyin_table.json"

    @Volatile
    private var cached: Map<String, String>? = null

    /** 加载(进程内仅一次)。失败时返回空表,由调用方回落。 */
    fun load(context: Context): Map<String, String> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = read(context.applicationContext)
            cached = loaded
            return loaded
        }
    }

    private fun read(context: Context): Map<String, String> = try {
        val text = context.assets.open(ASSET_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(text)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = root.optString(key, "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
            }
        }
    } catch (io: IOException) {
        Log.w(TAG, "未找到 $ASSET_FILE,拼音将回落到 ICU 推导(API 29+ 才可用)", io)
        emptyMap()
    } catch (ex: Exception) {
        Log.w(TAG, "$ASSET_FILE 解析失败,拼音将回落到 ICU 推导", ex)
        emptyMap()
    }
}

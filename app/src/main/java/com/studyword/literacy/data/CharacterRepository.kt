package com.studyword.literacy.data

import android.content.Context
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.ExampleSentence
import com.studyword.literacy.model.LearningCharacter
import com.studyword.literacy.model.WordEntry
import com.studyword.literacy.util.PinyinConverter
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.LinkedHashSet

class CharacterRepository(context: Context) {

    private val allCharacters: List<LearningCharacter>
    private val grouped: Map<Difficulty, List<LearningCharacter>>

    init {
        ensureData(context.applicationContext)
        allCharacters = cachedAll ?: emptyList()
        grouped = cachedGrouped ?: emptyMap()
    }

    /** v1.5.0:按汉字查字,用于把内容键进度还原成字卡(O(1)) */
    private val byHanziIndex: Map<String, LearningCharacter> =
        allCharacters.associateBy { it.hanzi }

    fun byHanzi(hanzi: String): LearningCharacter? = byHanziIndex[hanzi]

    fun all(): List<LearningCharacter> = allCharacters

    fun count(): Int = allCharacters.size

    fun byDifficulty(difficulty: Difficulty): List<LearningCharacter> =
        grouped[difficulty] ?: emptyList()

    companion object {
        private const val ASSET_FILE = "character_sets.json"
        @Volatile private var cachedAll: List<LearningCharacter>? = null
        @Volatile private var cachedGrouped: Map<Difficulty, List<LearningCharacter>>? = null

        private fun ensureData(context: Context) {
            if (cachedAll != null && cachedGrouped != null) return
            synchronized(this) {
                if (cachedAll != null && cachedGrouped != null) return
                val (all, grouped) = loadCharacters(context)
                cachedAll = all
                cachedGrouped = grouped
            }
        }

        private fun loadCharacters(context: Context): Pair<List<LearningCharacter>, Map<Difficulty, List<LearningCharacter>>> {
            val jsonText = try {
                context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (io: IOException) {
                throw IllegalStateException("无法读取字库配置文件：$ASSET_FILE", io)
            }
            val root = JSONObject(jsonText)
            val items = mutableListOf<LearningCharacter>()
            val grouped = mutableMapOf<Difficulty, MutableList<LearningCharacter>>()
            val seen = LinkedHashSet<String>()
            // v1.5.0:构建期生成的离线拼音表(3000 字全覆盖,不再依赖 API 29 的 ICU)
            val pinyinTable = PinyinTable.load(context)
            var nextId = 0

            val order = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
            for (difficulty in order) {
                val key = difficulty.name.lowercase()
                val array = root.optJSONArray(key)
                    ?: throw IllegalStateException("字库配置缺少键：$key")
                val list = mutableListOf<LearningCharacter>()
                for (i in 0 until array.length()) {
                    val parsed = parseEntry(array, i) ?: continue
                    if (!seen.add(parsed.hanzi)) continue
                    // v1.5.0:取值优先级 = 字库显式 pinyin > 人工 overrides > 离线拼音表 > ICU(API 29+)
                    val pinyin = parsed.pinyin.ifBlank {
                        PinyinConverter.toPinyin(parsed.hanzi, pinyinTable)
                    }
                    val entry = parsed.copy(
                        id = nextId++,
                        pinyin = pinyin,
                        difficulty = difficulty
                    )
                    items += entry
                    list += entry
                }
                grouped[difficulty] = list
            }
            return items to grouped
        }

        /**
         * 解析数组中的单项。
         *
         * 支持两种格式(向后兼容):
         * - 字符串: `"天"` —— 仅含单字,words/examples 视为空
         * - 对象: `{"char":"天","pinyin":"tiān","words":[…],"examples":[…]}` —— 含词组与例句
         *
         * v1.5.0:对象格式新增可选 `pinyin` 字段。给了就用它(便于人工校正多音字),
         * 没给则由 [PinyinConverter] 推导。
         *
         * 返回的 [LearningCharacter] 中 id/difficulty 为占位值,由调用方覆盖。
         */
        private fun parseEntry(array: JSONArray, index: Int): LearningCharacter? {
            val raw = array.opt(index) ?: return null
            val hanzi: String
            val explicitPinyin: String
            val words: List<WordEntry>
            val examples: List<ExampleSentence>
            when (raw) {
                is String -> {
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) return null
                    hanzi = trimmed.substring(0, 1)
                    explicitPinyin = ""
                    words = emptyList()
                    examples = emptyList()
                }
                is JSONObject -> {
                    val charStr = raw.optString("char", "").trim()
                    if (charStr.isEmpty()) return null
                    hanzi = charStr.substring(0, 1)
                    explicitPinyin = raw.optString("pinyin", "").trim()
                    words = parseWords(raw.optJSONArray("words"))
                    examples = parseExamples(raw.optJSONArray("examples"))
                }
                else -> return null
            }
            return LearningCharacter(
                id = -1,
                hanzi = hanzi,
                pinyin = explicitPinyin,
                difficulty = Difficulty.EASY,
                words = words,
                examples = examples
            )
        }

        private fun parseWords(array: JSONArray?): List<WordEntry> {
            if (array == null || array.length() == 0) return emptyList()
            val result = mutableListOf<WordEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val word = obj.optString("word", "").trim()
                if (word.isEmpty()) continue
                val pinyin = obj.optString("pinyin", "").trim()
                result += WordEntry(word, pinyin)
            }
            return result
        }

        private fun parseExamples(array: JSONArray?): List<ExampleSentence> {
            if (array == null || array.length() == 0) return emptyList()
            val result = mutableListOf<ExampleSentence>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val sentence = obj.optString("sentence", "").trim()
                if (sentence.isEmpty()) continue
                val pinyin = obj.optString("pinyin", "").trim()
                result += ExampleSentence(sentence, pinyin)
            }
            return result
        }
    }
}

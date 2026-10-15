package com.studyword.literacy.data

import android.content.Context
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.EnglishCategory
import com.studyword.literacy.model.EnglishLetter
import com.studyword.literacy.model.EnglishWord
import com.studyword.literacy.model.ProgressKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 英文字母与单词的数据仓库。
 *
 * 设计要点:
 * - 与 [CharacterRepository] 同款的进程级单例(双检锁 + @Volatile)
 * - 数据来源:assets/english_sets.json,内含 letters(26)+ words(30)两个数组
 * - 同时维护字母与单词两个 [List],并按 [EnglishCategory] 分组,便于 UI / 题池直接过滤
 *
 * v1.4.0:
 * - 单词分难(EASY/MEDIUM/HARD)
 * - 新增 [byCategoryAndDifficulty] API
 */
class EnglishRepository(context: Context) {

    private val allLetters: List<EnglishLetter>
    private val allWords: List<EnglishWord>
    private val grouped: Map<EnglishCategory, List<Any>>
    /** 单词按难度分组的缓存,key = Difficulty,value = 该难度的单词列表 */
    private val wordsByDifficulty: Map<Difficulty, List<EnglishWord>>

    init {
        ensureData(context.applicationContext)
        allLetters = cachedLetters ?: emptyList()
        allWords = cachedWords ?: emptyList()
        grouped = mapOf(
            EnglishCategory.LETTERS to (cachedLetters ?: emptyList<EnglishLetter>()),
            EnglishCategory.WORDS to (cachedWords ?: emptyList<EnglishWord>())
        )
        wordsByDifficulty = allWords.groupBy { it.difficulty }
    }

    /** 返回所有英文字母(A-Z,26 个,按字母表顺序) */
    fun letters(): List<EnglishLetter> = allLetters

    /** 返回所有英文单词(目前 30 个高频词) */
    fun words(): List<EnglishWord> = allWords

    /** 返回字母与单词的合并视图(字母在前,单词在后) */
    fun all(): List<Any> = allLetters + allWords

    /** 按 [EnglishCategory] 过滤;c=LETTERS 返回 [EnglishLetter],c=WORDS 返回 [EnglishWord](类型擦除为 Any) */
    fun byCategory(category: EnglishCategory): List<Any> = grouped[category] ?: emptyList()

    /**
     * v1.4.0:按类别 + 难度组合过滤。
     *
     * - LETTERS:返回所有字母(字母不分难度,difficulty 参数被忽略)
     * - WORDS:返回指定难度的单词;若该难度无内容则返回空列表
     */
    fun byCategoryAndDifficulty(category: EnglishCategory, difficulty: Difficulty): List<Any> {
        return when (category) {
            EnglishCategory.LETTERS -> allLetters
            EnglishCategory.WORDS -> wordsByDifficulty[difficulty] ?: emptyList()
        }
    }

    /** 字母数量(应为 26) */
    fun letterCount(): Int = allLetters.size

    /** 单词数量(目前 30) */
    fun wordCount(): Int = allWords.size

    /** 字母 + 单词总数 */
    fun count(): Int = allLetters.size + allWords.size

    /**
     * 按大写字母查找 [EnglishLetter](O(1) 哈希查询)。
     */
    fun findByLetter(letter: String): EnglishLetter? {
        if (letter.isBlank()) return null
        val upper = letter.trim().uppercase()
        return letterIndex[upper]
    }

    /**
     * 按小写单词查找 [EnglishWord](O(1) 哈希查询,大小写不敏感)。
     */
    fun findByWord(word: String): EnglishWord? {
        if (word.isBlank()) return null
        val key = word.trim().lowercase()
        return wordIndex[key]
    }

    /**
     * 按字母 id 查找(0~25)。v1.3.0 用于进度页:把 englishKnownIds 还原回字母实例。
     */
    fun findByLetterById(id: Int): EnglishLetter? {
        if (id < 0 || id >= WORD_ID_OFFSET) return null
        return allLetters.getOrNull(id)
    }

    /**
     * 按单词 id 查找(内部已加 [WORD_ID_OFFSET] 偏移)。
     */
    fun findByWordById(id: Int): EnglishWord? {
        if (id < WORD_ID_OFFSET) return null
        return allWords.getOrNull(id - WORD_ID_OFFSET)
    }

    /**
     * v1.5.0:按**进度键**查找,返回 [EnglishLetter] 或 [EnglishWord]。
     * 用于把内容键进度("L:A" / "W:apple")还原成卡片。
     */
    fun findByProgressKey(key: String): Any? = when {
        key.startsWith(ProgressKey.LETTER_PREFIX) ->
            letterIndex[key.removePrefix(ProgressKey.LETTER_PREFIX)]
        key.startsWith(ProgressKey.WORD_PREFIX) ->
            wordIndex[key.removePrefix(ProgressKey.WORD_PREFIX)]
        else -> null
    }

    companion object {
        private const val ASSET_FILE = "english_sets.json"

        /**
         * 单词 id 偏移量。字母 id 范围 [0, 25],单词 id 范围 [WORD_ID_OFFSET, WORD_ID_OFFSET + wordCount - 1]。
         */
        const val WORD_ID_OFFSET = 1000

        @Volatile private var cachedLetters: List<EnglishLetter>? = null
        @Volatile private var cachedWords: List<EnglishWord>? = null
        @Volatile private var letterIndex: Map<String, EnglishLetter> = emptyMap()
        @Volatile private var wordIndex: Map<String, EnglishWord> = emptyMap()

        private fun ensureData(context: Context) {
            if (cachedLetters != null && cachedWords != null) return
            synchronized(this) {
                if (cachedLetters != null && cachedWords != null) return
                val (letters, words) = loadAll(context)
                cachedLetters = letters
                cachedWords = words
                letterIndex = letters.associateBy { it.uppercase }
                wordIndex = words.associateBy { it.word.lowercase() }
            }
        }

        private fun loadAll(context: Context): Pair<List<EnglishLetter>, List<EnglishWord>> {
            val jsonText = try {
                context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (io: IOException) {
                throw IllegalStateException("无法读取英文配置文件：$ASSET_FILE", io)
            }
            val root = JSONObject(jsonText)
            val letters = parseLetters(root.optJSONArray("letters"))
            val words = parseWords(root.optJSONArray("words"))
            return letters to words
        }

        private fun parseLetters(array: JSONArray?): List<EnglishLetter> {
            if (array == null || array.length() == 0) return emptyList()
            val result = mutableListOf<EnglishLetter>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val letter = obj.optString("letter", "").trim()
                if (letter.isEmpty()) continue
                val upper = obj.optString("uppercase", letter).trim().ifEmpty { letter.uppercase() }
                val lower = obj.optString("lowercase", letter.lowercase()).trim().ifEmpty { letter.lowercase() }
                val phonetic = obj.optString("phonetic", "").trim()
                val example = obj.optString("exampleWord", "").trim()
                val exampleChinese = obj.optString("exampleWordChinese", "").trim()
                result += EnglishLetter(
                    id = result.size,
                    letter = upper,
                    uppercase = upper,
                    lowercase = lower,
                    phonetic = phonetic,
                    exampleWord = example,
                    exampleWordChinese = exampleChinese
                )
            }
            return result
        }

        private fun parseWords(array: JSONArray?): List<EnglishWord> {
            if (array == null || array.length() == 0) return emptyList()
            val result = mutableListOf<EnglishWord>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val word = obj.optString("word", "").trim()
                if (word.isEmpty()) continue
                val phonetic = obj.optString("phonetic", "").trim()
                val meaning = obj.optString("chineseMeaning", "").trim()
                val sentence = obj.optString("exampleSentence", "").trim()
                val translation = obj.optString("exampleSentenceTranslation", "").trim()
                val difficulty = parseDifficulty(obj.optString("difficulty", "EASY"))
                result += EnglishWord(
                    id = WORD_ID_OFFSET + result.size,
                    word = word,
                    phonetic = phonetic,
                    chineseMeaning = meaning,
                    exampleSentence = sentence,
                    exampleSentenceTranslation = translation,
                    difficulty = difficulty
                )
            }
            return result
        }

        /** 把 JSON 字符串映射到 Difficulty 枚举,容错:无法识别默认 EASY */
        private fun parseDifficulty(s: String): Difficulty = when (s.uppercase()) {
            "EASY", "简单" -> Difficulty.EASY
            "MEDIUM", "中等" -> Difficulty.MEDIUM
            "HARD", "困难" -> Difficulty.HARD
            else -> Difficulty.EASY
        }
    }
}
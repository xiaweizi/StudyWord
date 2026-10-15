package com.studyword.literacy.ui

import androidx.lifecycle.ViewModel
import com.studyword.literacy.game.GameMode
import com.studyword.literacy.game.GameRound
import com.studyword.literacy.model.Difficulty
import com.studyword.literacy.model.StudyItem
import com.studyword.literacy.model.StudyMode

/**
 * 英文学习子模式。
 *
 * v1.5.0 从 MainActivity 的私有嵌套枚举提升为顶层:
 * 状态需要被 [MainViewModel] 持有。
 */
enum class EnglishSubMode {
    /** 只显示字母 A-Z */
    LETTERS,

    /** 只显示单词(按难度过滤) */
    WORDS
}

/**
 * 主页状态(v1.5.0)。
 *
 * 引入原因:此前 [MainActivity] 把这些状态全放在 Activity 字段里,
 * 而项目没有 onSaveInstanceState / ViewModel,于是**旋转屏幕即丢失**:
 * - [pendingItems] / [currentItem] 丢失 → onResume 重建队列,孩子正在看的字被换掉
 * - [jumpSource] 丢失 → 左上角"← 返回进度/字库"按钮莫名消失
 *
 * 注意:[currentDifficulty] / [englishSubMode] 同时也会持久化到 SharedPreferences
 * (见 ProgressStore.saveDifficulty / saveEnglishSubMode),两者目的不同:
 * - ViewModel:跨配置变更保留
 * - prefs:跨进程重启保留
 */
class MainViewModel : ViewModel() {

    var currentMode: StudyMode = StudyMode.CHINESE

    var currentDifficulty: Difficulty = Difficulty.EASY

    var englishSubMode: EnglishSubMode = EnglishSubMode.LETTERS

    /** 待测队列(待巩固 → 未测 → 已掌握) */
    val pendingItems: ArrayDeque<StudyItem> = ArrayDeque()

    /** 当前展示的字卡 */
    var currentItem: StudyItem? = null

    /** 跳转来源(null / "progress" / "library"),用于显示"← 返回"按钮 */
    var jumpSource: String? = null

    /**
     * v1.6.0:是否处于"复习错题"模式。
     * true 时题池只包含**现在到期**的错题(间隔重复),没有到期项则自动退出。
     */
    var reviewOnly: Boolean = false
}

/**
 * 游戏页状态(v1.5.0)。
 *
 * [GameRound] 内部持有 currentIndex / correctCount / results,
 * 放进 ViewModel 后旋转屏幕不再让整轮 5 题作废重开。
 */
class GameViewModel : ViewModel() {

    var mode: GameMode = GameMode.LISTEN

    var language: StudyMode = StudyMode.CHINESE

    var difficulty: Difficulty = Difficulty.EASY

    var round: GameRound? = null
}

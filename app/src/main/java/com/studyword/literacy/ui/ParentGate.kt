package com.studyword.literacy.ui

import android.app.Activity
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studyword.literacy.R
import com.studyword.literacy.util.TtsManager
import kotlin.random.Random

/**
 * 家长门(v1.6.0 / Parent Gate)。
 *
 * 为什么需要
 * ----------
 * Apple 对 App Store 的 Kids 类目**强制要求** parental gates ——
 * "需要完成成人级任务才能继续",用来防止孩子在没有家长知情时做某些事;
 * 并且明确建议:**面向不识字的儿童时,用语音提示让孩子知道要去叫家长**。
 * Google 的儿童应用设计指南同样要求 "Gate parental controls":
 * 用只有成人知道的问题守住重要设置。
 *
 * 本应用里"重置进度 / 导出数据 / 切换孩子"都属于此类,因此把
 * 「更多设置」入口挂在这个门后面。语言、难度、复习、进度、字库这些
 * 对孩子无害的入口**不加门**,避免家长每次都要解题。
 *
 * 题目形式
 * --------
 * 两位数加法(随机生成),答案必须手算 —— 3~6 岁幼儿无法完成,
 * 成年人一眼可解。答错不关闭对话框,可继续作答。
 *
 * 参考出处:
 * - Apple: https://developer.apple.com/app-store/kids-apps/
 * - Google: https://developers.google.cn/building-for-kids/designing-engaging-apps
 */
object ParentGate {

    /**
     * 弹出家长门。[onPass] 仅在答案正确时调用一次。
     */
    fun show(activity: Activity, onPass: () -> Unit) {
        val left = Random.nextInt(12, 49)
        val right = Random.nextInt(12, 49)
        val answer = left + right

        // 语音提示:幼儿不识字,靠"听"知道要叫家长(Apple 建议的做法)
        TtsManager.speak(activity.getString(R.string.parent_gate_speech), utteranceId = "parent_gate")

        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = activity.getString(R.string.parent_gate_hint)
            isSingleLine = true
            textSize = 22f
            setPadding(48, 32, 48, 32)
        }
        // EditText 直接作为对话框内容会贴边,套一层容器给足内边距
        val container = FrameLayout(activity).apply {
            val pad = (activity.resources.displayMetrics.density * 20).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(
                input,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.parent_gate_title)
            .setMessage(activity.getString(R.string.parent_gate_message, left, right))
            .setView(container)
            .setNegativeButton(R.string.parent_gate_cancel, null)
            .setPositiveButton(R.string.parent_gate_confirm, null)
            .create()

        // 覆盖确定按钮的默认"点击即关闭":答错时保持对话框打开,允许重试
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val typed = input.text.toString().trim().toIntOrNull()
                if (typed == answer) {
                    dialog.dismiss()
                    onPass()
                } else {
                    input.text?.clear()
                    Toast.makeText(activity, R.string.parent_gate_wrong, Toast.LENGTH_SHORT).show()
                    input.requestFocus()
                }
            }
        }
        dialog.show()
    }
}

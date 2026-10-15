package com.studyword.literacy

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.studyword.literacy.databinding.ActivitySplashBinding
import com.studyword.literacy.ui.MainActivity

/**
 * v1.6.0:开机启动页
 *
 * 显示一张静态插画 2 秒后启动 [MainActivity]。
 *
 * 历史:
 * - v1.4.0 最初是 5 张占位帧 + emoji 切换共 3 秒
 * - v1.6.0 改为由用户提供一张插画,显示时间 2 秒
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    private val handler = Handler(Looper.getMainLooper())
    private val goToMainRunnable = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler.postDelayed(goToMainRunnable, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        /** 单次开机启动画面停留时间(ms) */
        private const val SPLASH_DURATION_MS = 2000L
    }
}
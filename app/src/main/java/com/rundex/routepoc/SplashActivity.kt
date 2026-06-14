package com.rundex.routepoc

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** 최초 구동 스플래시 — 로고가 통통 튀며 떠올랐다가 피드로 진입. */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<TextView>(R.id.splashLogo)
        val title = findViewById<TextView>(R.id.splashTitle)
        val tagline = findViewById<TextView>(R.id.splashTagline)

        // 로고: 작게→커지며 통통(overshoot) + 페이드인, 이후 가볍게 위아래 바운스
        logo.alpha = 0f; logo.scaleX = 0.5f; logo.scaleY = 0.5f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1f),
            )
            duration = 620
            interpolator = OvershootInterpolator(2.5f)
            start()
        }
        ObjectAnimator.ofFloat(logo, "translationY", 0f, -26f, 0f).apply {
            startDelay = 640; duration = 720; start()
        }

        // 타이틀·태그라인: 아래에서 올라오며 페이드인
        for ((v, delay) in listOf(title to 260L, tagline to 420L)) {
            v.alpha = 0f; v.translationY = 36f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(v, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(v, "translationY", 36f, 0f),
                )
                startDelay = delay; duration = 520
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        // 1.5초 뒤 피드로 진입(부드러운 페이드 전환)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, FeedActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }
}

package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast

/** 설정 — 계정·러닝·정보. 익명 계정 기반(복구는 구조만 대비). */
class SettingsActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<TextView>(R.id.setBack).setOnClickListener { finish() }

        // 계정
        findViewById<TextView>(R.id.setAccount).setOnClickListener { showAccount() }
        findViewById<TextView>(R.id.setRecover).setOnClickListener {
            AlertBox("계정 백업·복구", "지금은 기기에 저장된 익명 계정으로 자동 로그인돼요.\n" +
                "이메일 연결을 통한 백업·복구 기능을 준비 중입니다. 곧 만나요!")
        }

        // 러닝 — 새 러닝 기본 공개 토글
        val defPublic = findViewById<TextView>(R.id.setDefaultPublic)
        fun renderDefault() {
            val on = prefs.getBoolean("default_public", false)
            defPublic.text = "새 러닝 기본 공개" + if (on) "   🔓 켜짐" else "   🔒 꺼짐"
        }
        renderDefault()
        defPublic.setOnClickListener {
            val on = !prefs.getBoolean("default_public", false)
            prefs.edit().putBoolean("default_public", on).apply()
            renderDefault()
            Toast.makeText(this, if (on) "새 러닝을 기본 공개로 올려요" else "새 러닝을 기본 비공개로 저장해요", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.setBattery).setOnClickListener { openBattery() }

        // 정보
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0"
        findViewById<TextView>(R.id.setVersion).apply {
            text = "📱 앱 버전   v$ver"
            setOnClickListener { Toast.makeText(this@SettingsActivity, "Rundex v$ver", Toast.LENGTH_SHORT).show() }
        }
        findViewById<TextView>(R.id.setRepo).setOnClickListener { openUrl("https://github.com/DreamyDreamss/Rundex") }
        findViewById<TextView>(R.id.setAbout).setOnClickListener {
            AlertBox("Rundex 🏃", "달릴수록 채워지는 러닝 도감 — 전국 행정동을 발견하고 색칠하며,\n" +
                "기록을 공유하고 크루와 함께 달려요. 즐거운 러닝 되세요!")
        }
    }

    private fun showAccount() {
        val s = Session(this)
        val uid = s.userId?.take(8) ?: "-"
        AlertBox("👤 내 계정",
            "익명 계정으로 이용 중이에요.\n계정 ID: $uid…\n\n" +
            "프로필 이름·아이디·소개는 '프로필 편집'에서 바꿀 수 있어요.")
    }

    private fun openBattery() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { Toast.makeText(this, "설정을 열 수 없어요", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    private fun AlertBox(title: String, msg: String) {
        android.app.AlertDialog.Builder(this, R.style.RundexDialog)
            .setTitle(title).setMessage(msg).setPositiveButton("확인", null).show()
    }
}

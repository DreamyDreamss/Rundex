package com.rundex.routepoc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

/** 단일 진입점 — 테스트할 POC 화면을 고르는 홈 메뉴 */
class HomeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.openPlanButton).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<Button>(R.id.openTrackButton).setOnClickListener {
            startActivity(Intent(this, TrackActivity::class.java))
        }
    }
}

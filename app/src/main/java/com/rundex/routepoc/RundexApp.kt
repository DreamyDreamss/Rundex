package com.rundex.routepoc

import android.app.Application

/**
 * 앱 진입점 — 시작 시 1회 Supabase 익명 로그인을 보장한다(가입 폼 없음).
 * 서버(ApiConfig)가 꺼져 있으면 아무 일도 하지 않아 로컬 전용으로 동작.
 */
class RundexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (ApiConfig.enabled) {
            AuthClient(Session(this)).ensureSession()
        }
    }
}

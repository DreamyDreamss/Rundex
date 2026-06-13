package com.rundex.routepoc

import android.content.Context
import java.util.UUID

/** 기기·사용자 세션 — deviceId(영구) + 서버 발급 userId/token 보관 */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString()
            .also { prefs.edit().putString("deviceId", it).apply() }

    var userId: String?
        get() = prefs.getString("userId", null)
        set(v) { prefs.edit().putString("userId", v).apply() }

    var token: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().putString("token", v).apply() }

    var refreshToken: String?
        get() = prefs.getString("refreshToken", null)
        set(v) { prefs.edit().putString("refreshToken", v).apply() }
}

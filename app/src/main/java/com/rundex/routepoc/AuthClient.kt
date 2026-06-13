package com.rundex.routepoc

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Supabase 익명 인증. 가입 폼 없이 첫 실행에 익명 계정(id+토큰)을 발급받고,
 * 이후 실행에는 refresh_token으로 같은 계정을 유지한다.
 *
 * - 최초: POST /auth/v1/signup (빈 바디) → 익명 사용자 + 세션
 * - 이후: POST /auth/v1/token?grant_type=refresh_token → 액세스 토큰 갱신
 *
 * 모두 best-effort(비차단). ApiConfig 미설정 시 아무것도 하지 않는다.
 */
class AuthClient(
    private val session: Session,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 앱 시작 시 1회 호출 — 세션이 없으면 익명 가입, 있으면 토큰 갱신 */
    fun ensureSession() {
        if (!ApiConfig.enabled) return
        if (session.userId == null) signInAnonymously() else refresh()
    }

    private fun signInAnonymously() {
        post("/auth/v1/signup", "{}") { it.onSuccess(::saveSession) }
    }

    private fun refresh() {
        val rt = session.refreshToken ?: return signInAnonymously()
        post(
            "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", rt).toString()
        ) { result ->
            // 갱신 실패(만료·폐기) 시 기존 토큰 유지 — 소셜만 일시 비활성
            result.onSuccess(::saveSession)
        }
    }

    private fun saveSession(o: JSONObject) {
        o.optString("access_token").takeIf { it.isNotEmpty() }?.let { session.token = it }
        o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { session.refreshToken = it }
        o.optJSONObject("user")?.optString("id")?.takeIf { it.isNotEmpty() }
            ?.let { session.userId = it }
    }

    private fun post(path: String, body: String, cb: (Result<JSONObject>) -> Unit) {
        val req = Request.Builder()
            .url(ApiConfig.BASE_URL.trimEnd('/') + path)
            .header("apikey", ApiConfig.ANON_KEY)
            .header("Authorization", "Bearer ${ApiConfig.ANON_KEY}")
            .post(body.toRequestBody(jsonMedia))
            .build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cb(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { cb(Result.failure(IOException("HTTP ${it.code}"))); return }
                    cb(runCatching { JSONObject(it.body?.string().orEmpty()) })
                }
            }
        })
    }
}

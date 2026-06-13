package com.rundex.routepoc

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 백엔드(REST) 클라이언트. [BACKEND_DESIGN.md]의 계약을 그대로 따른다.
 * [ApiConfig.enabled]가 false면 모든 호출을 조용히 스킵해 로컬 전용 모드로 동작한다.
 *
 * 응답 파싱은 화면별로 진행하고, 여기서는 원시 JSON(JSONObject/JSONArray)을 콜백으로 넘긴다.
 */
class ApiClient(
    private val session: Session,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ── 공개 엔드포인트 ────────────────────────────────────────────

    /** 러닝 종료 시 업로드. 서버 RPC가 도감/등급/칭호/테마를 권위 재계산해 응답. */
    fun submitRun(payload: JSONObject, cb: (Result<JSONObject>) -> Unit = {}) =
        postObject("/rest/v1/rpc/submit_run", JSONObject().put("payload", payload), cb)

    fun getThemes(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/theme_collections?select=*", cb)

    /** 칭호 보유율(희소성) — title_rarity 뷰 */
    fun getTitleRarity(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/title_rarity?select=*", cb)

    /** 대표 칭호 등 프로필 갱신 (본인 행) */
    fun patchMe(userId: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit = {}) =
        sendArrayAsObject("PATCH", "/rest/v1/profiles?id=eq.$userId", body, cb)

    // 추천 경로(Discover) — PostgREST 필터/정렬 쿼리
    fun listRoutes(query: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/routes?$query", cb)
    fun createRoute(body: JSONObject, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject("POST", "/rest/v1/routes", body, cb)
    fun rateRoute(body: JSONObject, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject("POST", "/rest/v1/route_ratings", body, cb)

    // 소셜
    fun getFeed(cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/activities?select=*&order=created_at.desc&limit=50", cb)

    /** 핸들·이름으로 사용자 검색 (친구 찾기) */
    fun searchUsers(query: String, cb: (Result<JSONArray>) -> Unit) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        getArray(
            "/rest/v1/profiles?select=id,handle,display_name,rep_title_ids" +
                "&or=(handle.ilike.*$q*,display_name.ilike.*$q*)&limit=30", cb
        )
    }

    fun follow(followerId: String, followeeId: String, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject(
            "POST", "/rest/v1/follows",
            JSONObject().put("follower_id", followerId).put("followee_id", followeeId), cb
        )

    fun unfollow(followerId: String, followeeId: String, cb: (Result<JSONObject>) -> Unit = {}) =
        sendNoBody(
            "DELETE",
            "/rest/v1/follows?follower_id=eq.$followerId&followee_id=eq.$followeeId", cb
        )

    fun react(activityId: String, emoji: String, cb: (Result<JSONObject>) -> Unit) =
        sendArrayAsObject(
            "POST", "/rest/v1/reactions",
            JSONObject().put("activity_id", activityId).put("emoji", emoji), cb
        )

    // 크루
    fun createCrew(name: String, cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/create_crew", JSONObject().put("crew_name", name), cb)
    fun joinCrew(code: String, cb: (Result<JSONObject>) -> Unit) =
        postObject("/rest/v1/rpc/join_crew", JSONObject().put("code", code), cb)
    fun listMyCrews(userId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/crew_members?user_id=eq.$userId&select=role,crews(id,name,join_code)", cb)
    fun getCrewMembers(crewId: String, cb: (Result<JSONArray>) -> Unit) =
        getArray("/rest/v1/crew_members?crew_id=eq.$crewId&select=role,profiles(display_name,handle)", cb)

    // ── 내부 HTTP 헬퍼 ────────────────────────────────────────────

    private fun postObject(path: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit) =
        sendObject("POST", path, body, cb)

    private fun sendObject(method: String, path: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit) {
        val req = build(path)?.method(method, body.toString().toRequestBody(jsonMedia))?.build()
            ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONObject(it) }) }
    }

    private fun getArray(path: String, cb: (Result<JSONArray>) -> Unit) {
        val req = build(path)?.get()?.build() ?: return cb(skipped())
        enqueue(req) { raw -> cb(raw.map { JSONArray(it) }) }
    }

    /** PostgREST POST/PATCH는 표현(배열)을 반환 — 첫 객체만 꺼내준다 */
    private fun sendArrayAsObject(method: String, path: String, body: JSONObject, cb: (Result<JSONObject>) -> Unit) {
        val req = build(path)
            ?.header("Prefer", "return=representation")
            ?.method(method, body.toString().toRequestBody(jsonMedia))?.build()
            ?: return cb(skipped())
        enqueue(req) { raw ->
            cb(raw.map { s ->
                val t = s.trim()
                if (t.startsWith("[")) JSONArray(t).optJSONObject(0) ?: JSONObject()
                else if (t.startsWith("{")) JSONObject(t) else JSONObject()
            })
        }
    }

    /** 본문 없는 요청(DELETE 등) — 응답 본문이 비어 있어도 성공 처리 */
    private fun sendNoBody(method: String, path: String, cb: (Result<JSONObject>) -> Unit) {
        val req = build(path)?.method(method, null)?.build() ?: return cb(skipped())
        enqueue(req) { raw ->
            cb(raw.map { s -> if (s.isBlank()) JSONObject() else JSONObject(s) })
        }
    }

    /** ApiConfig가 꺼져 있으면 null → 호출부는 조용히 스킵 */
    private fun build(path: String): Request.Builder? {
        if (!ApiConfig.enabled) return null
        val b = Request.Builder()
            .url(ApiConfig.BASE_URL.trimEnd('/') + path)
            .header("X-Device-Id", session.deviceId)
        if (ApiConfig.ANON_KEY.isNotBlank()) b.header("apikey", ApiConfig.ANON_KEY)
        session.token?.let { b.header("Authorization", "Bearer $it") }
            ?: ApiConfig.ANON_KEY.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private fun enqueue(req: Request, done: (Result<String>) -> Unit) {
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { done(Result.failure(IOException("HTTP ${it.code}"))); return }
                    done(Result.success(it.body?.string().orEmpty()))
                }
            }
        })
    }

    private fun <T> skipped(): Result<T> = Result.failure(IllegalStateException("ApiConfig disabled"))
}
